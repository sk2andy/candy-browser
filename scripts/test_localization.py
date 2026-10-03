from collections import Counter
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


RESOURCES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
FORMAT_ARGUMENT = re.compile(r"%(?:%|(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z])")
RESOURCE_TYPES = {"string", "plurals", "string-array"}


def read_resources(directory):
    resources = {}
    for path in sorted(directory.glob("*.xml")):
        for element in ET.parse(path).getroot():
            if element.tag not in RESOURCE_TYPES or element.get("translatable") == "false":
                continue
            name = element.attrib["name"]
            if name in resources:
                raise ValueError(f"Duplicate resource {name} in {directory.name}")
            resources[name] = element
    return resources


def locale_directory(tag):
    if tag == "en":
        return RESOURCES / "values"
    if "-" not in tag:
        return RESOURCES / f"values-{tag}"
    return RESOURCES / f"values-b+{tag.replace('-', '+')}"


def arguments(element):
    # Literal percent signs are prose; only substitution arguments need parity.
    return Counter(token for token in FORMAT_ARGUMENT.findall("".join(element.itertext())) if token != "%%")


class LocalizationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.default = read_resources(RESOURCES / "values")
        cls.locales = [
            element.attrib[ANDROID_NAME]
            for element in ET.parse(RESOURCES / "xml" / "locales_config.xml").getroot()
        ]

    def test_declared_languages_have_complete_unique_resources(self):
        self.assertEqual(len(self.locales), len(set(self.locales)))
        for tag in self.locales:
            with self.subTest(locale=tag):
                directory = locale_directory(tag)
                self.assertTrue(directory.is_dir(), f"Missing {directory}")
                self.assertEqual(set(self.default), set(read_resources(directory)))

    def test_translations_preserve_resource_types_and_format_arguments(self):
        for tag in self.locales:
            translated = read_resources(locale_directory(tag))
            for name, source in self.default.items():
                with self.subTest(locale=tag, resource=name):
                    target = translated.get(name)
                    self.assertIsNotNone(target)
                    self.assertEqual(source.tag, target.tag)
                    leaves = list(target) if source.tag in {"plurals", "string-array"} else [target]
                    for leaf in leaves:
                        self.assertTrue("".join(leaf.itertext()).strip(), "Empty translation")
                    if source.tag == "plurals":
                        source_items = {item.attrib["quantity"]: item for item in source}
                        quantities = [item.attrib["quantity"] for item in target]
                        self.assertIn("other", quantities)
                        self.assertEqual(len(quantities), len(set(quantities)))
                        for item in target:
                            quantity = item.attrib["quantity"]
                            self.assertIn(quantity, {"zero", "one", "two", "few", "many", "other"})
                            expected = source_items.get(quantity)
                            if expected is None:
                                expected = source_items["other"]
                            # Languages whose singular category includes 21 or 101 may need
                            # an explicit count even where the English singular omits it.
                            self.assertIn(arguments(item), (arguments(expected), arguments(source_items["other"])))
                    elif source.tag == "string-array":
                        self.assertEqual(len(source), len(target))
                        for original_item, translated_item in zip(source, target):
                            self.assertEqual(arguments(original_item), arguments(translated_item))
                    else:
                        self.assertEqual(arguments(source), arguments(target))


if __name__ == "__main__":
    unittest.main()
