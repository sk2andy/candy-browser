import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

from summarize_android_session_heap import SessionHeap, TARGET_CLASSES, summarize_heap


CONTROLLER = TARGET_CLASSES[1]
CANDY_SESSION = TARGET_CLASSES[2]
GECKO_SESSION = TARGET_CLASSES[3]
PROVIDER = TARGET_CLASSES[5]


class HeapFixture:
    def __init__(self, id_size=4):
        self.id_size = id_size
        self.metadata = bytearray()
        self.heap = bytearray()
        self.next_string = 10000
        self.names = {}

    def identifier(self, value):
        return value.to_bytes(self.id_size, "big")

    def record(self, tag, body):
        return bytes([tag]) + struct.pack(">II", 0, len(body)) + body

    def string(self, value):
        if value not in self.names:
            identifier = self.next_string
            self.next_string += 1
            self.names[value] = identifier
            self.metadata.extend(self.record(1, self.identifier(identifier) + value.encode()))
        return self.names[value]

    def class_dump(self, identifier, name, fields=(), superclass=0, statics=()):
        name_id = self.string(name)
        self.metadata.extend(self.record(2, struct.pack(">I", 1) + self.identifier(identifier)
                                         + struct.pack(">I", 0) + self.identifier(name_id)))
        body = self.identifier(identifier) + struct.pack(">I", 0)
        body += self.identifier(superclass) + self.identifier(0) * 5 + struct.pack(">I", 0)
        body += struct.pack(">HH", 0, len(statics))
        for field, type_code, value in statics:
            body += self.identifier(self.string(field)) + bytes([type_code]) + self.value(type_code, value)
        body += struct.pack(">H", len(fields))
        for field, type_code in fields:
            body += self.identifier(self.string(field)) + bytes([type_code])
        self.heap.extend(b"\x20" + body)

    def value(self, type_code, value):
        sizes = {2: self.id_size, 4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
        return value.to_bytes(sizes[type_code], "big")

    def instance(self, identifier, class_id, values=()):
        payload = b"".join(self.value(type_code, value) for type_code, value in values)
        self.heap.extend(b"\x21" + self.identifier(identifier) + struct.pack(">I", 0)
                         + self.identifier(class_id) + struct.pack(">I", len(payload)) + payload)

    def array(self, identifier, class_id, values):
        self.heap.extend(b"\x22" + self.identifier(identifier) + struct.pack(">II", 0, len(values))
                         + self.identifier(class_id) + b"".join(self.identifier(value) for value in values))

    def primitive_array(self, identifier, type_code, values):
        self.heap.extend(b"\x23" + self.identifier(identifier) + struct.pack(">II", 0, len(values))
                         + bytes([type_code]) + b"".join(self.value(type_code, value) for value in values))

    def root(self, identifier, tag=0x01):
        extras = {0x01: self.identifier(0), 0x02: b"\0" * 8, 0x05: b""}
        self.heap.extend(bytes([tag]) + self.identifier(identifier) + extras.get(tag, b""))

    def data(self, segmented=True):
        header = b"JAVA PROFILE 1.0.3\0" + struct.pack(">IQ", self.id_size, 0)
        return header + self.metadata + self.record(28 if segmented else 12, self.heap) + (
            self.record(44, b"") if segmented else b""
        )


class AndroidSessionHeapSummaryTest(unittest.TestCase):
    def test_jni_global_root_reports_closed_session_and_native_handle(self):
        fixture = HeapFixture()
        fixture.class_dump(1, "org.mozilla.gecko.mozglue.JNIObject", [("mHandle", 11)])
        fixture.class_dump(2, PROVIDER, [("this$0", 2)], superclass=1)
        fixture.class_dump(3, TARGET_CLASSES[4], [("mSession", 2), ("mAttached", 4)])
        fixture.class_dump(4, GECKO_SESSION, [("mWindow", 2)])
        fixture.instance(101, 2, [(2, 102), (11, 123456789)])
        fixture.instance(102, 3, [(2, 103), (4, 1)])
        fixture.instance(103, 4, [(2, 0)])
        fixture.root(101)

        summary = SessionHeap(fixture.data()).summarize()

        session = self._object(summary, GECKO_SESSION)
        provider = self._object(summary, PROVIDER)
        self.assertEqual(summary["strong_reachable_counts"][GECKO_SESSION], 1)
        self.assertEqual(session["null_fields"], ["mWindow"])
        self.assertEqual(session["strong_root_path"][0]["via"], "jni_global")
        self.assertTrue(provider["native_handle_present"])
        self.assertNotIn("123456789", json.dumps(summary))

    def test_preserves_direct_jni_global_when_object_has_multiple_roots(self):
        fixture = HeapFixture()
        fixture.class_dump(1, PROVIDER)
        fixture.instance(101, 1)
        fixture.root(101, tag=0x02)
        fixture.root(101)
        summary = SessionHeap(fixture.data()).summarize()
        self.assertEqual(self._object(summary, PROVIDER)["strong_root_path"][0]["via"], "jni_global")

    def test_excludes_inherited_reference_referent_and_unreachable_tag(self):
        fixture = HeapFixture()
        fixture.class_dump(1, "java.lang.ref.Reference", [("referent", 2)])
        fixture.class_dump(2, "java.lang.ref.WeakReference", superclass=1)
        fixture.class_dump(3, CONTROLLER, [("destroyed", 4)])
        fixture.instance(101, 2, [(2, 102)])
        fixture.instance(102, 3, [(4, 1)])
        fixture.root(101)
        fixture.root(102, tag=0x90)

        summary = SessionHeap(fixture.data()).summarize()

        self.assertEqual(summary["counts"][CONTROLLER], 1)
        self.assertEqual(summary["strong_reachable_counts"][CONTROLLER], 0)
        self.assertIsNone(self._object(summary, CONTROLLER)["strong_root_path"])

    def test_primitive_value_matching_object_id_is_not_reference(self):
        fixture = HeapFixture()
        fixture.class_dump(1, PROVIDER, [("mHandle", 11)])
        fixture.class_dump(2, CONTROLLER)
        fixture.instance(101, 1, [(11, 102)])
        fixture.instance(102, 2)
        fixture.root(101)

        summary = SessionHeap(fixture.data()).summarize()

        self.assertEqual(summary["strong_reachable_counts"][CONTROLLER], 0)

    def test_static_and_object_array_paths_and_graph_cycle(self):
        fixture = HeapFixture(id_size=8)
        fixture.class_dump(1, "fixture.Holder", statics=[("controller", 2, 101)])
        fixture.class_dump(2, "[Ljava.lang.Object;")
        fixture.class_dump(3, CONTROLLER, [("controller", 2)])
        fixture.array(101, 2, [102, 0])
        fixture.instance(102, 3, [(2, 101)])
        fixture.root(1, tag=0x05)

        summary = SessionHeap(fixture.data()).summarize()

        path = self._object(summary, CONTROLLER)["strong_root_path"]
        self.assertEqual([step["via"] for step in path], ["sticky_class", "static:controller", "array_element"])
        self.assertEqual(summary["strong_reachable_counts"][CONTROLLER], 1)

    def test_inherited_fields_keep_subclass_name_precedence(self):
        fixture = HeapFixture()
        fixture.class_dump(1, "fixture.Parent", [("closed", 4), ("controller", 2)])
        fixture.class_dump(2, CANDY_SESSION, [("closed", 4), ("controller", 2)], superclass=1)
        fixture.class_dump(3, CONTROLLER)
        fixture.instance(101, 2, [(4, 1), (2, 0), (4, 0), (2, 102)])
        fixture.instance(102, 3)
        fixture.root(101)

        summary = SessionHeap(fixture.data()).summarize()

        self.assertTrue(self._object(summary, CANDY_SESSION)["flags"]["closed"])
        self.assertEqual(summary["strong_reachable_counts"][CONTROLLER], 1)

    def test_subclass_field_named_referent_is_not_weak(self):
        fixture = HeapFixture()
        fixture.class_dump(1, "java.lang.ref.Reference", [("referent", 2)])
        fixture.class_dump(2, "fixture.Reference", [("referent", 2)], superclass=1)
        fixture.class_dump(3, CONTROLLER)
        fixture.instance(101, 2, [(2, 102), (2, 0)])
        fixture.instance(102, 3)
        fixture.root(101)

        self.assertEqual(SessionHeap(fixture.data()).summarize()["strong_reachable_counts"][CONTROLLER], 1)

    def test_map_sizes_are_primitive_and_no_payload_or_metadata_leaks(self):
        secret = "https://private.example/input/secret-tab-id"
        fixture = HeapFixture()
        fixture.class_dump(1, CONTROLLER, [("browserEngineSessions", 2), (secret, 2)])
        fixture.class_dump(2, "java.util.HashMap", [("size", 10)])
        fixture.class_dump(3, secret, [(secret, 2)])
        fixture.class_dump(4, GECKO_SESSION)
        fixture.instance(101, 1, [(2, 102), (2, 103)])
        fixture.instance(102, 2, [(10, 7)])
        fixture.instance(103, 3, [(2, 104)])
        fixture.instance(104, 4)
        fixture.primitive_array(105, 8, list(secret.encode()))
        fixture.root(101)

        summary = SessionHeap(fixture.data()).summarize()

        self.assertEqual(self._object(summary, CONTROLLER)["map_sizes"], {"browserEngineSessions": 7})
        self.assertNotIn(secret, json.dumps(summary))
        self.assertIn("other_object", json.dumps(summary))
        self.assertNotIn("104", json.dumps(summary))

    def test_reference_field_named_size_does_not_report_object_id_as_map_size(self):
        fixture = HeapFixture()
        fixture.class_dump(1, CONTROLLER, [("browserEngineSessions", 2)])
        fixture.class_dump(2, "fixture.Map", [("size", 2)])
        fixture.instance(101, 1, [(2, 102)])
        fixture.instance(102, 2, [(2, 103)])

        summary = SessionHeap(fixture.data()).summarize()

        self.assertIsNone(self._object(summary, CONTROLLER)["map_sizes"]["browserEngineSessions"])

    def test_rejects_malformed_inherited_field_length(self):
        fixture = HeapFixture()
        fixture.class_dump(1, GECKO_SESSION, [("mWindow", 2)])
        fixture.instance(101, 1, [(4, 0)])

        with self.assertRaisesRegex(ValueError, "field length"):
            SessionHeap(fixture.data())

    def test_rejects_missing_and_cyclic_superclasses(self):
        for superclass in (1, 99):
            fixture = HeapFixture()
            fixture.class_dump(1, GECKO_SESSION, superclass=superclass)
            with self.subTest(superclass=superclass), self.assertRaises(ValueError):
                SessionHeap(fixture.data())

    def test_rejects_truncated_record_and_missing_segment_end(self):
        fixture = HeapFixture()
        fixture.class_dump(1, GECKO_SESSION)
        fixture.instance(101, 1)
        for data in (fixture.data()[:-1], fixture.data()[:-9]):
            with self.subTest(length=len(data)), self.assertRaisesRegex(ValueError, "incomplete"):
                SessionHeap(data)

    def test_rejects_unsupported_version_identifier_and_heap_tag(self):
        fixture = HeapFixture()
        fixture.class_dump(1, GECKO_SESSION)
        data = fixture.data()
        for changed in (data.replace(b"1.0.3", b"9.9.9"), HeapFixture(id_size=2).data()):
            with self.assertRaises(ValueError):
                SessionHeap(changed)
        fixture.heap.extend(b"\x91")
        with self.assertRaisesRegex(ValueError, "heap record"):
            SessionHeap(fixture.data())

    def test_enforces_size_object_array_and_string_limits(self):
        fixture = HeapFixture()
        fixture.class_dump(1, GECKO_SESSION, [("mWindow", 2)])
        fixture.instance(101, 1, [(2, 0)])
        for limit in ("MAX_HEAP_BYTES", "MAX_OBJECTS", "MAX_STRINGS", "MAX_RECORDS"):
            with self.subTest(limit=limit), patch("summarize_android_session_heap." + limit, 1):
                with self.assertRaises(ValueError):
                    SessionHeap(fixture.data())
        fixture.class_dump(2, "[Ljava.lang.Object;")
        fixture.array(102, 2, [0, 0])
        with patch("summarize_android_session_heap.MAX_ARRAY_ELEMENTS", 1):
            with self.assertRaisesRegex(ValueError, "array limit"):
                SessionHeap(fixture.data())

    def test_accepts_art_clock_metadata_and_rejects_its_wrong_length(self):
        fixture = HeapFixture()
        fixture.class_dump(1, GECKO_SESSION)
        fixture.instance(101, 1)
        fixture.metadata.extend(fixture.record(0xA0, b"\0" * 8))
        self.assertEqual(SessionHeap(fixture.data()).summarize()["counts"][GECKO_SESSION], 1)
        fixture.metadata.extend(fixture.record(0xA0, b"\0" * 7))
        with self.assertRaisesRegex(ValueError, "ART clock"):
            SessionHeap(fixture.data())

    def test_primitive_array_and_primitive_static_do_not_create_edges(self):
        fixture = HeapFixture()
        fixture.class_dump(1, "fixture.Holder", statics=[("controller", 10, 101)])
        fixture.class_dump(2, CONTROLLER)
        fixture.instance(101, 2)
        fixture.primitive_array(102, 10, [101])
        fixture.root(1, tag=0x05)
        fixture.root(102)
        self.assertEqual(SessionHeap(fixture.data()).summarize()["strong_reachable_counts"][CONTROLLER], 0)

    def test_enforces_layout_target_and_path_limits(self):
        fixture = HeapFixture()
        fixture.class_dump(1, CONTROLLER, [("controller", 2)])
        fixture.instance(101, 1, [(2, 102)])
        fixture.instance(102, 1, [(2, 0)])
        fixture.root(101)
        for limit in ("MAX_LAYOUT_FIELDS", "MAX_TOTAL_LAYOUT_FIELDS"):
            with self.subTest(limit=limit), patch("summarize_android_session_heap." + limit, 0):
                with self.assertRaisesRegex(ValueError, "limit"):
                    SessionHeap(fixture.data())
        heap = SessionHeap(fixture.data())
        with patch("summarize_android_session_heap.MAX_TARGET_OBJECTS", 1):
            with self.assertRaisesRegex(ValueError, "target limit"):
                heap.summarize()
        with patch("summarize_android_session_heap.MAX_PATH_LENGTH", 1):
            summary = heap.summarize()
            self.assertEqual(summary["objects"][1]["strong_root_path"], [{"class": "path_limit", "via": "omitted"}])

    def test_rejects_multiple_unsegmented_snapshots(self):
        fixture = HeapFixture()
        fixture.class_dump(1, GECKO_SESSION)
        fixture.instance(101, 1)
        with self.assertRaisesRegex(ValueError, "multiple"):
            SessionHeap(fixture.data(segmented=False) + fixture.record(12, b""))

    def test_extension_pending_message_multimap_counts_keys_and_empty_message_classes(self):
        fixture = HeapFixture()
        fixture.class_dump(1, TARGET_CLASSES[6], [("mPendingMessages", 2)])
        fixture.class_dump(2, "org.mozilla.gecko.MultiMap", [("mMap", 2)])
        fixture.class_dump(3, "java.util.HashMap", [("size", 10)])
        fixture.instance(101, 1, [(2, 102)])
        fixture.instance(102, 2, [(2, 103)])
        fixture.instance(103, 3, [(10, 0)])
        summary = SessionHeap(fixture.data()).summarize()
        self.assertEqual(self._object(summary, TARGET_CLASSES[6])["pending_message_key_count"], 0)
        self.assertEqual(summary["counts"][TARGET_CLASSES[7]], 0)
        self.assertEqual(summary["counts"][TARGET_CLASSES[8]], 0)

    def test_current_controller_preview_and_media_maps_are_reported(self):
        fixture = HeapFixture()
        fixture.class_dump(1, CONTROLLER, [("pendingGeckoPreviewCaptures", 2), ("geckoMediaStates", 2)])
        fixture.class_dump(2, "java.util.HashMap", [("size", 10)])
        fixture.instance(101, 1, [(2, 102), (2, 103)])
        fixture.instance(102, 2, [(10, 2)])
        fixture.instance(103, 2, [(10, 3)])
        summary = SessionHeap(fixture.data()).summarize()
        self.assertEqual(self._object(summary, CONTROLLER)["map_sizes"], {
            "pendingGeckoPreviewCaptures": 2, "geckoMediaStates": 3,
        })

    def test_loads_plain_hprof_file_and_unsegmented_heap(self):
        fixture = HeapFixture()
        fixture.class_dump(1, GECKO_SESSION)
        fixture.instance(101, 1)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.hprof"
            path.write_bytes(fixture.data(segmented=False))
            self.assertEqual(summarize_heap(path)["counts"][GECKO_SESSION], 1)
            path.write_bytes(b"")
            with self.assertRaises(ValueError):
                summarize_heap(path)

    def _object(self, summary, class_name):
        return next(row for row in summary["objects"] if row["class"] == class_name)


if __name__ == "__main__":
    unittest.main()
