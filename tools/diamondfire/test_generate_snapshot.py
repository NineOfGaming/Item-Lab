"""Offline regression tests using both checked-in normalized df-info exports."""

import copy
import json
import subprocess
import sys
import unittest

import generate_snapshot as generator


class SnapshotTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.selection = json.loads(generator.SELECTION.read_text(encoding="utf-8"))
        cls.current = json.loads((generator.FIXTURES / "actiondump-current.json").read_text(encoding="utf-8"))
        cls.previous = json.loads((generator.FIXTURES / "actiondump-previous.json").read_text(encoding="utf-8"))
        cls.evidence = json.loads(generator.EVIDENCE.read_text(encoding="utf-8"))

    def test_offline_regeneration_matches_checked_in_snapshots(self):
        subprocess.run([sys.executable, generator.__file__, "--check"], check=True)

    def test_codec_policy_keeps_registry_values_out_of_format_warnings(self):
        policy = json.loads((generator.FIXTURES / "codec-format-policy.json").read_text(encoding="utf-8"))
        runtime = generator.runtime_formats(policy)
        self.assertNotIn("26.1", runtime["client_versions"])
        self.assertEqual(runtime["client_versions"]["26.2"], [])
        self.assertEqual(runtime["client_versions"]["26.3"],
                         ["minecraft:instrument", "minecraft:pot_decorations", "minecraft:trim"])
        self.assertNotIn("reason", generator.encode(runtime))
        for mutation in ("unreviewed", "duplicate", "wrong baseline", "wrong fixture"):
            bad = copy.deepcopy(policy)
            if mutation == "unreviewed":
                bad["comparisons"][-1]["formats"] = []
            elif mutation == "duplicate":
                bad["comparisons"].append(copy.deepcopy(bad["comparisons"][0]))
            elif mutation == "wrong baseline":
                bad["server_version"] = "26.3"
            else:
                bad["comparisons"][0]["fixture"] = "../outside.json"
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                generator.runtime_formats(bad)

    def test_older_clients_cannot_reenter_df_compatibility_policy(self):
        policy = json.loads((generator.FIXTURES / "codec-format-policy.json").read_text(encoding="utf-8"))
        older = copy.deepcopy(policy["comparisons"][0])
        older.update(client_version="26.1", fixture="codec-comparison-26.1.json")
        policy["comparisons"].append(older)
        with self.assertRaisesRegex(ValueError, "Older clients"):
            generator.runtime_formats(policy)

    def test_both_fixture_orders_are_deterministic(self):
        for fixture in (self.current, self.previous):
            with self.subTest(commit=fixture["provenance"]["commit"]):
                reordered = copy.deepcopy(fixture)
                reordered["actions"].reverse()
                normalized = generator.normalize_actions(reordered, self.selection["display_names"], allow_missing=fixture is self.previous)
                self.assertEqual(generator.encode({"actions": normalized}), generator.encode({"actions": fixture["actions"]}))

    def test_duplicate_ids_fail_even_in_unselected_records(self):
        dump = copy.deepcopy(self.current)
        dump["actions"].append(copy.deepcopy(dump["actions"][0]))
        with self.assertRaisesRegex(ValueError, "Duplicate action ID"):
            generator.normalize_actions(dump, [])

    def test_distinct_whitespace_ids_survive(self):
        identities = {(action["codeblockName"], action["name"]) for action in self.current["actions"]}
        self.assertIn(("SET VARIABLE", "SetItemFlags"), identities)
        self.assertIn(("SET VARIABLE", " SetItemFlags "), identities)

    def test_missing_selected_action_and_duplicate_selection_fail(self):
        with self.assertRaisesRegex(ValueError, "absent from pinned dump"):
            generator.normalize_actions(self.current, ["Missing action"])
        with self.assertRaisesRegex(ValueError, "Duplicate selected"):
            generator.normalize_actions(self.current, ["Set Item Glowing", "Set Item Glowing"])

    def test_current_patch_is_required_and_prior_unknown_is_explicit(self):
        raw = generator.encode({"actions": self.previous["actions"]}).encode()
        with self.assertRaisesRegex(ValueError, "versions.patch"):
            generator.fixture_snapshot(raw, self.selection, self.selection["commit"])
        prior = generator.fixture_snapshot(raw, self.selection, self.selection["previous_commit"], previous=True)
        self.assertIsNone(prior["provenance"]["patch"])
        self.assertIn("unknown", prior["provenance"]["patch_source"])

    def test_previous_export_preserves_declared_patch_and_allows_new_actions_to_be_absent(self):
        raw = generator.encode({"versions": {"patch": "8.13"}, "actions": self.previous["actions"]}).encode()
        selection = copy.deepcopy(self.selection)
        selection["display_names"].append("Future Item Action")
        prior = generator.fixture_snapshot(raw, selection, selection["previous_commit"], previous=True)
        self.assertEqual(prior["provenance"]["patch"], "8.13")
        self.assertEqual(prior["provenance"]["patch_source"], "versions.patch")
        self.assertEqual(prior["actions"], self.previous["actions"])

    def test_unused_and_volatile_fields_are_omitted(self):
        dump = copy.deepcopy(self.current)
        for action in dump["actions"]:
            action["icon"]["loadedItem"] = "opaque code item: out of scope"
            action["icon"]["requiredRank"] = "unused"
            for tag in action["tags"]:
                tag["slot"] = 99
        normalized = generator.normalize_actions(dump, self.selection["display_names"])
        self.assertEqual(normalized, self.current["actions"])

    def test_runtime_excludes_legacy_actions_but_preserves_current_whitespace_ids(self):
        runtime = generator.runtime_snapshot(self.current)
        actions = {action["id"]: action for action in runtime["actions"] if action["codeblock"] == "SET VARIABLE"}
        self.assertNotIn("SetItemFood", actions)
        self.assertNotIn("SetItemName", actions)
        self.assertIn(" SetItemName ", actions)
        self.assertNotIn("legacy_replacement", actions[" SetItemName "])
        legacy_keys = {(action["codeblockName"], action["name"])
                       for action in self.current["actions"] if "legacyReplacement" in action}
        runtime_keys = {(action["codeblock"], action["id"]) for action in runtime["actions"]}
        self.assertTrue(runtime_keys.isdisjoint(legacy_keys))

    def test_reviewed_policy_projection_is_deterministic(self):
        actual = generator.runtime_policy(self.evidence, self.current)
        reordered = copy.deepcopy(self.evidence)
        reordered["components"].reverse()
        for component in reordered["components"]:
            component["fields"].reverse()
            for field in component["fields"]:
                field["sources"].reverse()
        self.assertEqual(generator.encode(actual), generator.encode(generator.runtime_policy(reordered, self.current)))
        self.assertEqual({component["badge"] for component in actual["components"]}, {"DF+", "DFP"})
        runtime_keys = {f"{action['codeblock']}/{action['id']}" for action in generator.runtime_snapshot(self.current)["actions"]}
        for component in actual["components"]:
            self.assertNotIn("detail", component)
            self.assertTrue(set(component["actions"]) <= runtime_keys)

    def test_partial_hints_keep_brief_limitations_not_research_rationale(self):
        policies = generator.runtime_policy(self.evidence, self.current)["components"]
        for component in policies:
            if component["badge"] == "DF+":
                self.assertNotIn("limitation", component)
            else:
                self.assertTrue(component["limitation"].strip())
                self.assertLessEqual(len(component["limitation"]), 160)
        for text in ("", "x" * 161, "First line\nSecond line"):
            with self.subTest(text=text):
                evidence = copy.deepcopy(self.evidence)
                next(component for component in evidence["components"] if component["id"] == "minecraft:break_sound")["limitation"] = text
                with self.assertRaisesRegex(ValueError, "Invalid brief component limitation"):
                    generator.runtime_policy(evidence, self.current)

    def test_invalid_policy_badges_duplicates_and_references_fail(self):
        for kind in ("duplicate", "badge", "reference"):
            with self.subTest(kind=kind):
                evidence = copy.deepcopy(self.evidence)
                if kind == "duplicate":
                    evidence["components"].append(copy.deepcopy(evidence["components"][0]))
                elif kind == "badge":
                    evidence["components"][0]["badge"] = "DF-new"
                else:
                    evidence["components"][0]["fields"][0]["sources"][0]["action"] = "MissingAction"
                with self.assertRaises(ValueError):
                    generator.runtime_policy(evidence, self.current)

    def test_gaps_are_not_promoted_to_complete_coverage(self):
        evidence = copy.deepcopy(self.evidence)
        component = next(component for component in evidence["components"] if component["id"] == "minecraft:break_sound")
        component["badge"] = "DF+"
        with self.assertRaisesRegex(ValueError, "Unexplained field gap"):
            generator.runtime_policy(evidence, self.current)
        component["badge"] = "DFP"
        del component["fields"][1]["uncertainty"]
        with self.assertRaisesRegex(ValueError, "Unexplained field gap"):
            generator.runtime_policy(evidence, self.current)

    def test_legacy_or_uncertain_evidence_alone_cannot_establish_df_plus(self):
        evidence = copy.deepcopy(self.evidence)
        evidence["components"][0]["fields"][0]["sources"] = [{
            "action": "SetItemFlags", "codeblock": "SET VARIABLE", "operation": "write",
            "path": "/tags/0/name", "expected": "Hide Armor Trim",
        }]
        with self.assertRaisesRegex(ValueError, "only legacy"):
            generator.runtime_policy(evidence, self.current)
        evidence = copy.deepcopy(self.evidence)
        component = next(component for component in evidence["components"] if component["id"] == "minecraft:stored_enchantments")
        component["badge"] = "DF+"
        for field in component["fields"]:
            for source in field["sources"]:
                source["operation"] = "uncertain"
                source["restriction"] = "Synthetic case with no positive live or structural evidence."
        with self.assertRaisesRegex(ValueError, "only legacy/uncertain/approximate"):
            generator.runtime_policy(evidence, self.current)


if __name__ == "__main__":
    unittest.main()
