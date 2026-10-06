#!/usr/bin/env python3
"""Summarize Android session ownership without printing heap payloads or object IDs.

ART Android 17 root semantics:
https://android.googlesource.com/platform/art/+/b753cf97923c3695338d21466fa14c57b480a59a/runtime/jni/java_vm_ext.cc#1214
JNI global roots are strong; java.lang.ref.Reference.referent is excluded.
This is a Java reachability summary, not retained bytes or native memory accounting.
"""

from __future__ import annotations

import argparse
from collections import Counter, deque
from dataclasses import dataclass
import json
import mmap
from pathlib import Path
from typing import Any


MAX_HEAP_BYTES = 512 * 1024 * 1024
MAX_OBJECTS = 4_000_000
MAX_STRINGS = 2_000_000
MAX_RECORDS = 4_000_000
MAX_ARRAY_ELEMENTS = 4_000_000
MAX_STRING_BYTES = 1024 * 1024
MAX_LAYOUT_DEPTH = 256
MAX_LAYOUT_FIELDS = 65_536
MAX_TOTAL_LAYOUT_FIELDS = 4_000_000
MAX_TARGET_OBJECTS = 10_000
MAX_PATH_LENGTH = 256
TARGET_CLASSES = (
    "dev.sk2andy.materialbrowser.MainActivity",
    "dev.sk2andy.materialbrowser.browser.BrowserController",
    "dev.sk2andy.materialbrowser.browser.gecko.GeckoViewBrowserSession",
    "org.mozilla.geckoview.GeckoSession",
    "org.mozilla.geckoview.SessionAccessibility",
    "org.mozilla.geckoview.SessionAccessibility$NativeProvider",
    "org.mozilla.geckoview.WebExtensionController",
    "org.mozilla.geckoview.WebExtensionController$MessageRecipient",
    "org.mozilla.geckoview.WebExtensionController$Message",
)
BOOLEAN_FIELDS = (
    "destroyed", "closed", "active", "isPrivate", "isAppInBackground",
    "isActivityStarted", "isActivityResumed", "backgroundSessionBudgetApplied",
    "mDestroyed", "mAttached",
)
NULL_FIELDS = ("mWindow", "mView", "mPendingMessages")
MAP_FIELDS = (
    "browserEngineSessions", "geckoViewBindings", "pendingMemorySessionChecks",
    "pendingGeckoPreviewCaptures", "geckoMediaStates",
)
SAFE_EDGE_FIELDS = frozenset((
    "this$0", "mSession", "mPromptDelegate", "mContentDelegate", "mNavigationDelegate",
    "mProgressDelegate", "mPermissionDelegate", "mMediaSessionDelegate", "mSelectionActionDelegate",
    "mActivity", "activity", "controller", "mContext", "context", "privacyBinding", "sink",
    "notificationPermissionDecisionProvider", "mAccessibility", "nativeProvider",
    "mHandle", "mWindow", "mView", "mMessageDelegates", "mWebExtensionController",
    "mDelegate", "f$0", "$binding", "mSessionHandlers",
    *MAP_FIELDS,
))
ROOT_TAGS = {
    0x01: ("jni_global", "id"), 0x02: ("jni_local", "pair"),
    0x03: ("java_frame", "pair"), 0x04: ("native_stack", "word"),
    0x05: ("sticky_class", "none"), 0x06: ("thread_block", "word"),
    0x07: ("monitor", "none"), 0x08: ("thread_object", "pair"),
    0x89: ("interned_string", "none"), 0x8A: ("finalizing", "none"),
    0x8B: ("debugger", "none"), 0x8C: ("reference_cleanup", "none"),
    0x8D: ("vm_internal", "none"), 0x8E: ("jni_monitor", "pair"),
    0xFF: ("unknown", "none"),
}
TOP_LEVEL_TAGS = frozenset((1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 28, 44, 0xA0))


@dataclass(frozen=True)
class ClassDump:
    superclass: int
    references: tuple[int, ...]
    fields: tuple[tuple[int, int], ...]
    statics: tuple[tuple[int, int], ...]


@dataclass(frozen=True)
class InstanceDump:
    class_id: int
    offset: int
    length: int


@dataclass(frozen=True)
class ArrayDump:
    class_id: int
    offset: int
    length: int
    type_code: int


class Reader:
    def __init__(self, data: Any, start: int, end: int):
        self.data = data
        self.position = start
        self.end = end

    def skip(self, length: int) -> None:
        if length < 0 or length > self.end - self.position:
            raise ValueError("incomplete HPROF record")
        self.position += length

    def number(self, length: int) -> int:
        start = self.position
        self.skip(length)
        return int.from_bytes(self.data[start:self.position], "big")


class SessionHeap:
    def __init__(self, data: Any):
        self.data = data
        self.strings: dict[int, bytes] = {}
        self.class_names: dict[int, int] = {}
        self.classes: dict[int, ClassDump] = {}
        self.instances: dict[int, InstanceDump] = {}
        self.arrays: dict[int, ArrayDump] = {}
        self.roots: dict[int, str] = {}
        self.layouts: dict[int, tuple[tuple[int, int, int, int], ...]] = {}
        self._layout_fields_count = 0
        self._parse()
        self._validate_layouts()

    def _parse(self) -> None:
        if not self.data or len(self.data) > MAX_HEAP_BYTES:
            raise ValueError("invalid HPROF size")
        terminator = self.data.find(b"\0", 0, 32)
        if terminator < 0 or self.data[:terminator] not in (
            b"JAVA PROFILE 1.0.1", b"JAVA PROFILE 1.0.2", b"JAVA PROFILE 1.0.3",
        ):
            raise ValueError("unsupported HPROF version")
        reader = Reader(self.data, terminator + 1, len(self.data))
        self.id_size = reader.number(4)
        if self.id_size not in (4, 8):
            raise ValueError("unsupported HPROF identifier size")
        self.sizes = {2: self.id_size, 4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
        reader.skip(8)
        records = 0
        segmented = False
        ended = False
        heap_seen = False
        unsegmented_seen = False
        while reader.position < reader.end:
            records += 1
            if records > MAX_RECORDS:
                raise ValueError("HPROF record limit exceeded")
            tag = reader.number(1)
            reader.skip(4)
            length = reader.number(4)
            start = reader.position
            reader.skip(length)
            record = Reader(self.data, start, reader.position)
            if tag not in TOP_LEVEL_TAGS:
                raise ValueError("unsupported HPROF record")
            if tag == 0xA0 and length != 8:
                raise ValueError("invalid ART clock metadata")
            if tag == 1:
                identifier = record.number(self.id_size)
                if identifier in self.strings or length - self.id_size > MAX_STRING_BYTES:
                    raise ValueError("invalid HPROF string metadata")
                if len(self.strings) >= MAX_STRINGS:
                    raise ValueError("HPROF string limit exceeded")
                self.strings[identifier] = self.data[record.position:record.end]
            elif tag == 2:
                record.skip(4)
                identifier = record.number(self.id_size)
                record.skip(4)
                name = record.number(self.id_size)
                if record.position != record.end or identifier in self.class_names:
                    raise ValueError("invalid HPROF load class")
                self.class_names[identifier] = name
            elif tag in (12, 28):
                if ended or unsegmented_seen or tag == 12 and heap_seen:
                    raise ValueError("multiple HPROF snapshots")
                unsegmented_seen = tag == 12
                segmented |= tag == 28
                heap_seen = True
                self._parse_heap(record)
            elif tag == 44:
                if length or ended or not segmented:
                    raise ValueError("invalid HPROF heap end")
                ended = True
        if not heap_seen or segmented and not ended:
            raise ValueError("incomplete HPROF snapshot")

    def _typed_value(self, reader: Reader, type_code: int) -> int:
        if type_code not in self.sizes:
            raise ValueError("unsupported HPROF field type")
        return reader.number(self.sizes[type_code])

    def _check_object(self, identifier: int) -> None:
        if not identifier or identifier in self.instances or identifier in self.classes or identifier in self.arrays:
            raise ValueError("duplicate or null HPROF object")
        if len(self.instances) + len(self.classes) + len(self.arrays) >= MAX_OBJECTS:
            raise ValueError("HPROF object limit exceeded")

    def _parse_heap(self, reader: Reader) -> None:
        while reader.position < reader.end:
            tag = reader.number(1)
            if tag == 0x20:
                identifier = reader.number(self.id_size)
                self._check_object(identifier)
                reader.skip(4)
                superclass = reader.number(self.id_size)
                references = tuple(reader.number(self.id_size) for _ in range(3))
                reader.skip(2 * self.id_size + 4)
                for _ in range(reader.number(2)):
                    reader.skip(2)
                    self._typed_value(reader, reader.number(1))
                statics = []
                for _ in range(reader.number(2)):
                    name = reader.number(self.id_size)
                    type_code = reader.number(1)
                    value = self._typed_value(reader, type_code)
                    if type_code == 2 and value:
                        statics.append((name, value))
                fields = []
                for _ in range(reader.number(2)):
                    name = reader.number(self.id_size)
                    type_code = reader.number(1)
                    if type_code not in self.sizes:
                        raise ValueError("unsupported HPROF field type")
                    fields.append((name, type_code))
                self.classes[identifier] = ClassDump(superclass, references, tuple(fields), tuple(statics))
            elif tag == 0x21:
                identifier = reader.number(self.id_size)
                self._check_object(identifier)
                reader.skip(4)
                class_id = reader.number(self.id_size)
                length = reader.number(4)
                self.instances[identifier] = InstanceDump(class_id, reader.position, length)
                reader.skip(length)
            elif tag in (0x22, 0x23, 0xC3):
                identifier = reader.number(self.id_size)
                self._check_object(identifier)
                reader.skip(4)
                length = reader.number(4)
                if length > MAX_ARRAY_ELEMENTS:
                    raise ValueError("HPROF array limit exceeded")
                if tag == 0x22:
                    class_id = reader.number(self.id_size)
                    type_code = 2
                else:
                    class_id = 0
                    type_code = reader.number(1)
                    if type_code == 2 or type_code not in self.sizes:
                        raise ValueError("invalid HPROF primitive array type")
                self.arrays[identifier] = ArrayDump(class_id, reader.position, length, type_code)
                if tag != 0xC3:
                    reader.skip(length * self.sizes[type_code])
            elif tag == 0xFE:
                reader.skip(4 + self.id_size)
            elif tag == 0x90:
                reader.skip(self.id_size)  # ART ROOT_UNREACHABLE is not a GC root.
            elif tag in ROOT_TAGS:
                label, extra = ROOT_TAGS[tag]
                identifier = reader.number(self.id_size)
                reader.skip({"none": 0, "word": 4, "pair": 8, "id": self.id_size}[extra])
                if identifier and (identifier not in self.roots or label == "jni_global"):
                    self.roots[identifier] = label
                if len(self.roots) > MAX_OBJECTS:
                    raise ValueError("HPROF root limit exceeded")
            else:
                raise ValueError("unsupported HPROF heap record")

    def class_name(self, class_id: int) -> str:
        name = self.strings.get(self.class_names.get(class_id, 0), b"")
        return name.decode("utf-8", "replace").replace("/", ".")

    def layout(self, class_id: int) -> tuple[tuple[int, int, int, int], ...]:
        if class_id in self.layouts:
            return self.layouts[class_id]
        fields = []
        current = class_id
        visited = set()
        offset = 0
        while current:
            if current in visited or len(visited) >= MAX_LAYOUT_DEPTH:
                raise ValueError("invalid HPROF class hierarchy")
            visited.add(current)
            if current not in self.classes:
                raise ValueError("missing HPROF superclass")
            for name, type_code in self.classes[current].fields:
                if name not in self.strings:
                    raise ValueError("missing HPROF field metadata")
                if len(fields) >= MAX_LAYOUT_FIELDS:
                    raise ValueError("HPROF layout field limit exceeded")
                fields.append((name, type_code, offset, current))
                offset += self.sizes[type_code]
            current = self.classes[current].superclass
        self._layout_fields_count += len(fields)
        if self._layout_fields_count > MAX_TOTAL_LAYOUT_FIELDS:
            raise ValueError("HPROF total layout limit exceeded")
        result = tuple(fields)
        self.layouts[class_id] = result
        return result

    def _validate_layouts(self) -> None:
        for class_id, class_dump in self.classes.items():
            if class_id not in self.class_names or self.class_names[class_id] not in self.strings:
                raise ValueError("missing HPROF class metadata")
            self.layout(class_id)
            if any(name not in self.strings for name, _ in class_dump.statics):
                raise ValueError("missing HPROF static metadata")
        for instance in self.instances.values():
            layout = self.layout(instance.class_id)
            length = sum(self.sizes[type_code] for _, type_code, _, _ in layout)
            if length != instance.length:
                raise ValueError("invalid HPROF inherited field length")
        for array in self.arrays.values():
            if array.type_code == 2 and array.class_id not in self.classes:
                raise ValueError("missing HPROF array class")

    def fields(self, identifier: int) -> dict[str, tuple[int, int]]:
        instance = self.instances[identifier]
        fields = {}
        for name, type_code, offset, _ in self.layout(instance.class_id):
            label = self.strings[name].decode("utf-8", "replace")
            start = instance.offset + offset
            value = int.from_bytes(self.data[start:start + self.sizes[type_code]], "big")
            # Subclass fields take precedence; superclass name collisions stay distinct in edges.
            fields.setdefault(label, (type_code, value))
        return fields

    def edges(self, identifier: int):
        if identifier in self.instances:
            instance = self.instances[identifier]
            yield instance.class_id, "class"
            for name, type_code, offset, owner in self.layout(instance.class_id):
                if type_code != 2:
                    continue
                field = self.strings[name].decode("utf-8", "replace")
                if field == "referent" and self.class_name(owner) == "java.lang.ref.Reference":
                    continue
                start = instance.offset + offset
                value = int.from_bytes(self.data[start:start + self.id_size], "big")
                if value:
                    yield value, field if field in SAFE_EDGE_FIELDS else "reference"
        elif identifier in self.arrays:
            array = self.arrays[identifier]
            if array.type_code == 2:
                yield array.class_id, "class"
                for index in range(array.length):
                    start = array.offset + index * self.id_size
                    value = int.from_bytes(self.data[start:start + self.id_size], "big")
                    if value:
                        yield value, "array_element"
        elif identifier in self.classes:
            class_dump = self.classes[identifier]
            if class_dump.superclass:
                yield class_dump.superclass, "superclass"
            for value in class_dump.references:
                if value:
                    yield value, "class_metadata"
            for name, value in class_dump.statics:
                field = self.strings[name].decode("utf-8", "replace")
                yield value, "static:" + field if field in SAFE_EDGE_FIELDS else "static_reference"

    def label(self, identifier: int) -> str:
        if identifier in self.instances:
            name = self.class_name(self.instances[identifier].class_id)
            return name if name in TARGET_CLASSES else "other_object"
        if identifier in self.classes:
            name = self.class_name(identifier)
            return "class:" + name if name in TARGET_CLASSES else "other_class"
        if identifier in self.arrays:
            return "object_array" if self.arrays[identifier].type_code == 2 else "primitive_array"
        return "unavailable_object"

    def summarize(self) -> dict[str, Any]:
        seen = {identifier: (0, label) for identifier, label in self.roots.items()}
        queue = deque(sorted(self.roots))
        while queue:
            identifier = queue.popleft()
            for value, edge in self.edges(identifier):
                if value not in seen:
                    if len(seen) >= MAX_OBJECTS:
                        raise ValueError("HPROF graph limit exceeded")
                    seen[value] = (identifier, edge)
                    queue.append(value)
        targets = []
        for identifier, instance in self.instances.items():
            if self.class_name(instance.class_id) in TARGET_CLASSES:
                if len(targets) >= MAX_TARGET_OBJECTS:
                    raise ValueError("HPROF summary target limit exceeded")
                targets.append(identifier)
        targets.sort(key=lambda identifier: (self.label(identifier), identifier))
        counts = Counter(self.label(identifier) for identifier in targets)
        reachable = Counter(self.label(identifier) for identifier in targets if identifier in seen)
        objects = []
        for identifier in targets:
            fields = self.fields(identifier)
            row = {
                "class": self.label(identifier),
                "strong_root_path": self._root_path(identifier, seen),
                "flags": {name: bool(fields[name][1]) for name in BOOLEAN_FIELDS
                          if name in fields and fields[name][0] == 4},
                "null_fields": [name for name in NULL_FIELDS
                                if name in fields and fields[name][0] == 2 and fields[name][1] == 0],
            }
            if "mHandle" in fields and fields["mHandle"][0] == 11:
                row["native_handle_present"] = fields["mHandle"][1] != 0
            if "mPendingMessages" in fields and fields["mPendingMessages"][0] == 2:
                row["pending_message_key_count"] = self._map_size(fields["mPendingMessages"][1], backing_map=True)
            if row["class"].endswith("BrowserController"):
                row["map_sizes"] = {name: self._map_size(fields[name][1]) for name in MAP_FIELDS
                                    if name in fields and fields[name][0] == 2}
            objects.append(row)
        return {
            "counts": {name: counts[name] for name in TARGET_CLASSES},
            "strong_reachable_counts": {name: reachable[name] for name in TARGET_CLASSES},
            "objects": objects,
            "notes": [
                "Strong Java root reachability approximation; excludes Reference.referent and ROOT_UNREACHABLE.",
                "JNI global roots are strong in ART. A plain heap dump need not have collected dead objects.",
                "Counts and paths do not establish persistence, retained bytes, native allocations, PSS, or a leak.",
                "Map sizes count keys; MultiMap pending-message keys are not the number of queued messages.",
                "Only allowlisted class and edge labels, flags and map sizes are emitted; no strings or object IDs.",
            ],
        }

    def _map_size(self, identifier: int, backing_map: bool = False) -> int | None:
        if identifier not in self.instances:
            return None
        fields = self.fields(identifier)
        for name in ("size", "_size"):
            if name in fields and fields[name][0] == 10:
                value = fields[name][1]
                return value if value < 0x80000000 else None
        instance = self.instances[identifier]
        if backing_map and self.class_name(instance.class_id) == "org.mozilla.gecko.MultiMap":
            field = fields.get("mMap")
            if field and field[0] == 2:
                return self._map_size(field[1])
        return None

    def _root_path(self, identifier: int, seen: dict[int, tuple[int, str]]) -> list[dict[str, str]] | None:
        if identifier not in seen:
            return None
        path = []
        while identifier:
            if len(path) >= MAX_PATH_LENGTH:
                return [{"class": "path_limit", "via": "omitted"}]
            parent, edge = seen[identifier]
            path.append({"class": self.label(identifier), "via": edge})
            identifier = parent
        return list(reversed(path))


def summarize_heap(path: Path) -> dict[str, Any]:
    with path.open("rb") as stream:
        size = stream.seek(0, 2)
        if not size or size > MAX_HEAP_BYTES:
            raise ValueError("invalid HPROF size")
        stream.seek(0)
        with mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as data:
            return SessionHeap(data).summarize()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("heap", type=Path, help="Android binary HPROF file")
    args = parser.parse_args()
    try:
        summary = summarize_heap(args.heap)
    except (OSError, ValueError) as error:
        parser.exit(2, f"Invalid session heap: {type(error).__name__}\n")
    print(json.dumps(summary, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
