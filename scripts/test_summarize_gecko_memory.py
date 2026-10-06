import gzip
import json
from pathlib import Path
import tempfile
import unittest

from summarize_gecko_memory import compare_reports, load_report, summarize_report, validate_report


class GeckoMemorySummaryTest(unittest.TestCase):
    def test_derives_unclassified_heap_without_counting_other_trees(self):
        summary = summarize_report(self._root([
            self._report("explicit/js-non-window/runtime", 100),
            self._report("explicit/images/decoded", 30),
            self._report("explicit/gfx/surfaces", 20, kind=0),
            self._report("heap-allocated", 160, kind=2),
            self._report("js-main-runtime/gc-heap", 100, kind=2),
            self._report("resident", 300, kind=2),
        ]))

        process = summary["processes"][0]
        self.assertEqual(process["explicit_bytes"], 180)
        self.assertEqual(process["allocation_categories_bytes"]["heap_unclassified"], 30)
        self.assertEqual(process["heap_unclassified_source"], "derived")
        self.assertEqual(process["allocation_categories_bytes"]["javascript"], 100)
        self.assertEqual(process["metrics_bytes"]["resident"], 300)

    def test_preserves_reported_heap_unclassified(self):
        summary = summarize_report(self._root([
            self._report("explicit/js-non-window/runtime", 100),
            self._report("explicit/heap-unclassified", 15),
            self._report("heap-allocated", 900, kind=2),
        ]))

        process = summary["processes"][0]
        self.assertEqual(process["explicit_bytes"], 115)
        self.assertEqual(process["heap_unclassified_source"], "reported")

    def test_excludes_aggregate_ancestors_and_sums_duplicate_leaf_reporters(self):
        summary = summarize_report(self._root([
            self._report("explicit", 90),
            self._report("explicit/images", 90),
            self._report("explicit/images/decoded", 40),
            self._report("explicit/images/decoded", 50),
        ]))

        process = summary["processes"][0]
        self.assertEqual(process["explicit_bytes"], 90)
        self.assertEqual(process["excluded_aggregate_paths"], 2)
        self.assertEqual(len(process["warnings"]), 1)

    def test_ownership_overlays_do_not_add_to_allocations(self):
        path = "explicit/add-ons/fixture@example.com/window-objects/top(https://example.org)/cached/window(https://example.org)/js-realm/main"
        summary = summarize_report(self._root([self._report(path, 70)]))

        process = summary["processes"][0]
        self.assertEqual(process["explicit_bytes"], 70)
        self.assertEqual(process["allocation_categories_bytes"]["javascript"], 70)
        self.assertEqual(process["ownership_subsets_bytes"]["bfcache"], 70)
        self.assertEqual(process["ownership_subsets_bytes"]["extensions"], 70)

    def test_native_worker_javascript_does_not_imply_extension_ownership(self):
        summary = summarize_report(self._root([
            self._report("explicit/workers/workers(chrome)/worker(resource:\\gre\\modules\\translations\\cld-worker.js, 0x123)/classes/class(ArrayBuffer)/objects/malloc-heap/elements", 160),
            self._report("explicit/workers/workers(chrome)/worker(moz-extension:\\fixture\\worker.js, 0x456)/classes/class(ArrayBuffer)/objects/malloc-heap/elements", 20),
        ]))

        process = summary["processes"][0]
        self.assertEqual(process["allocation_categories_bytes"]["javascript"], 180)
        self.assertEqual(process["allocation_categories_bytes"]["other"], 0)
        self.assertEqual(process["ownership_subsets_bytes"]["extensions"], 20)

    def test_counters_never_become_bytes(self):
        summary = summarize_report(self._root([
            self._report("explicit/dom/nodes", 20),
            self._report("ghost-windows", 3, kind=2, units=1),
            self._report("unrelated-count", 9000, kind=2, units=1),
        ]))

        self.assertEqual(summary["totals"]["explicit_bytes"], 20)
        self.assertEqual(summary["totals"]["counters"]["ghost_windows"], 3)

    def test_keeps_main_and_child_processes_separate(self):
        summary = summarize_report(self._root([
            self._report("explicit/js-non-window/runtime", 100),
            self._report("resident", 300, kind=2),
            self._report("explicit/js-non-window/runtime", 40, process="Web Content (pid 456)"),
            self._report("resident", 120, kind=2, process="Web Content (pid 456)"),
        ]))

        self.assertEqual(summary["process_count"], 2)
        self.assertEqual(summary["totals"]["explicit_bytes"], 140)
        self.assertEqual(summary["totals"]["metrics_bytes"]["resident"], 420)

    def test_missing_process_metrics_are_null_in_totals(self):
        summary = summarize_report(self._root([
            self._report("explicit/dom/nodes", 20),
            self._report("resident", 100, kind=2),
            self._report("explicit/dom/nodes", 40, process="Web Content (pid 456)"),
        ]))

        self.assertIsNone(summary["totals"]["metrics_bytes"]["resident"])

    def test_missing_explicit_tree_does_not_claim_zero_categories(self):
        summary = summarize_report(self._root([
            self._report("resident", 100, kind=2),
        ]))

        self.assertIsNone(summary["totals"]["explicit_bytes"])
        self.assertIsNone(summary["totals"]["allocation_categories_bytes"]["javascript"])

    def test_negative_unclassified_heap_is_visible(self):
        summary = summarize_report(self._root([
            self._report("explicit/dom/nodes", 200),
            self._report("heap-allocated", 100, kind=2),
        ]))

        process = summary["processes"][0]
        self.assertEqual(process["allocation_categories_bytes"]["heap_unclassified"], -100)
        self.assertEqual(len(process["warnings"]), 1)

    def test_summary_omits_sensitive_paths_and_process_domains(self):
        summary = summarize_report(self._root([
            self._report("explicit/window-objects/top(https://private.example/secret)/active/dom/nodes", 20, process="webIsolated=https://private.example (pid 456)"),
        ]))

        serialized = json.dumps(summary)
        self.assertNotIn("private.example", serialized)
        self.assertNotIn("secret", serialized)
        self.assertIn("content (pid 456)", serialized)

    def test_compares_aggregate_categories_and_preserves_unknown_metrics(self):
        before = summarize_report(self._root([self._report("explicit/images/decoded", 20)]))
        after = summarize_report(self._root([self._report("explicit/images/decoded", 35)]))

        comparison = compare_reports(before, after)

        self.assertEqual(comparison["aggregate_delta"]["allocation_categories_bytes"]["images"], 15)
        self.assertIsNone(comparison["aggregate_delta"]["metrics_bytes"]["resident"])

    def test_loads_json_and_gzip_by_magic_bytes(self):
        root = self._root([self._report("explicit/images/decoded", 20)])
        with tempfile.TemporaryDirectory() as directory:
            plain = Path(directory) / "plain.json"
            compressed = Path(directory) / "compressed.data"
            plain.write_text(json.dumps(root), encoding="utf-8")
            compressed.write_bytes(gzip.compress(json.dumps(root).encode("utf-8")))

            self.assertEqual(load_report(plain), root)
            self.assertEqual(load_report(compressed), root)

    def test_rejects_unknown_schema_and_boolean_amounts(self):
        root = self._root([self._report("explicit/images/decoded", 20)])
        root["version"] = 2
        with self.assertRaisesRegex(ValueError, "version"):
            validate_report(root)
        root["version"] = 1
        root["reports"][0]["amount"] = True
        with self.assertRaisesRegex(ValueError, "amount"):
            validate_report(root)

    def test_rejects_incompatible_duplicate_paths(self):
        root = self._root([
            self._report("explicit/images/decoded", 20),
            self._report("explicit/images/decoded", 40, kind=0),
        ])

        with self.assertRaisesRegex(ValueError, "mismatched"):
            summarize_report(root)

    def _root(self, reports):
        return {"version": 1, "hasMozMallocUsableSize": True, "reports": reports}

    def _report(self, path, amount, kind=1, units=0, process="Main Process (pid 123)"):
        return {"process": process, "path": path, "kind": kind, "units": units, "amount": amount, "description": "Fixture"}


if __name__ == "__main__":
    unittest.main()
