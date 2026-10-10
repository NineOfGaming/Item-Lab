#!/usr/bin/env python3
"""Import the pinned df-info subset; normal builds and gameplay never use Git/network/Python."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/assets/itemlab/diamondfire"
FIXTURES = ROOT / "src/test/resources/diamondfire"
SELECTION = Path(__file__).with_name("snapshot-selection.json")
EVIDENCE = FIXTURES / "component-evidence.json"
FORMATTING = re.compile(r"<[^<>]+>|(?:&|\N{SECTION SIGN})[0-9A-FK-ORa-fk-or]")
ICON_FIELDS = (
    "material", "name", "description", "deprecatedNote", "worksWith", "additionalInfo",
    "arguments", "returnValues",
)


def plain(value: str) -> str:
    return FORMATTING.sub("", value)


def icon_subset(icon: dict[str, Any]) -> dict[str, Any]:
    return {key: icon[key] for key in ICON_FIELDS if key in icon}


def normalize_actions(dump: dict[str, Any], names: list[str], *, allow_missing: bool = False) -> list[dict[str, Any]]:
    if len(set(names)) != len(names):
        raise ValueError("Duplicate selected display names")
    wanted = set(names)
    found = set()
    seen = set()
    result = []
    for action in dump["actions"]:
        identity = (action["codeblockName"], action["name"])
        if identity in seen:
            raise ValueError(f"Duplicate action ID: {identity!r}")
        seen.add(identity)
        display = plain(action.get("icon", {}).get("name", ""))
        if display not in wanted:
            continue
        found.add(display)
        record = {
            # Whitespace is significant: df-info has both 'SetItemFlags' and ' SetItemFlags '.
            "name": action["name"],
            "codeblockName": action["codeblockName"],
            "aliases": sorted(action.get("aliases", [])),
            "icon": icon_subset(action.get("icon", {})),
            "tags": [],
        }
        if "legacyReplacement" in action:
            record["legacyReplacement"] = action["legacyReplacement"]
        for tag in action.get("tags", []):
            record["tags"].append({
                "name": tag["name"],
                "defaultOption": tag["defaultOption"],
                "options": [{
                    "name": option["name"],
                    "aliases": sorted(option.get("aliases", [])),
                    "icon": icon_subset(option.get("icon", {})),
                } for option in tag["options"]],
            })
        result.append(record)
    missing = wanted - found
    if missing and not allow_missing:
        raise ValueError(f"Selected display names absent from pinned dump: {sorted(missing)}")
    return sorted(result, key=lambda action: (action["codeblockName"], action["name"]))


def runtime_snapshot(fixture: dict[str, Any]) -> dict[str, Any]:
    return {
        "schema_version": 2,
        "provenance": fixture["provenance"],
        "actions": [{
            "id": action["name"],
            "codeblock": action["codeblockName"],
            "display_name": plain(action["icon"]["name"]),
            "aliases": action["aliases"],
        } for action in fixture["actions"] if "legacyReplacement" not in action],
    }


def runtime_policy(evidence: dict[str, Any], fixture: dict[str, Any]) -> dict[str, Any]:
    """Project reviewed semantics into runtime hints; never infer coverage from action names."""
    if evidence["schema_version"] != 1:
        raise ValueError("Unknown field-evidence schema")
    action_records = {f"{action['codeblockName']}/{action['name']}": action for action in fixture["actions"]}
    # Preserve the existing UI's curated order, rather than sorting getters before setters by ID.
    display_order = {name: index for index, name in enumerate(json.loads(SELECTION.read_text(encoding="utf-8"))["display_names"])}
    components = []
    seen = set()
    for component in evidence["components"]:
        identity = component["id"]
        if identity in seen or component["badge"] not in {"DF+", "DFP", "DF?"}:
            raise ValueError(f"Duplicate component or unknown policy badge: {identity}")
        seen.add(identity)
        if not component["rationale"].strip() or not component["fields"]:
            raise ValueError(f"Empty reviewed component policy: {identity}")
        fields = set()
        for field in component["fields"]:
            if not field["path"].strip() or field["path"] in fields:
                raise ValueError(f"Blank or duplicate reviewed field: {identity}")
            fields.add(field["path"])
            if not field["sources"] and (component["badge"] == "DF+" or not field.get("uncertainty", "").strip()):
                raise ValueError(f"Unexplained field gap: {identity}/{field['path']}")
        references = {f"{source['codeblock']}/{source['action']}"
                      for field in component["fields"] for source in field["sources"]}
        if not references or references - action_records.keys():
            raise ValueError(f"Policy references absent action records: {identity}")
        for field in component["fields"]:
            exposed = False
            for source in field["sources"]:
                operation = source["operation"]
                if operation not in {"read", "write", "clear", "uncertain", "approximate"}:
                    raise ValueError(f"Unknown field operation: {identity}")
                if operation in {"uncertain", "approximate"} and not source.get("restriction", "").strip():
                    raise ValueError(f"Uncertainty needs an explicit limitation: {identity}")
                action = action_records[f"{source['codeblock']}/{source['action']}"]
                if operation in {"read", "write", "clear"} and "legacyReplacement" not in action:
                    exposed = True
            if component["badge"] == "DF+" and not exposed:
                raise ValueError(f"DF+ field has only legacy/uncertain/approximate evidence: {identity}/{field['path']}")
        current_references = {key for key in references if "legacyReplacement" not in action_records[key]}
        ordered_actions = sorted(current_references, key=lambda key: (
            display_order.get(plain(action_records[key]["icon"]["name"]), len(display_order)), key))
        if "action_order" in component:
            ordered_actions = component["action_order"]
            if len(set(ordered_actions)) != len(ordered_actions) or set(ordered_actions) != current_references:
                raise ValueError(f"UI action order does not match reviewed current actions: {identity}")
        limitation = component.get("limitation", "")
        if (len(limitation) > 160 or "\n" in limitation
                or (bool(limitation) if component["badge"] == "DF+" else not limitation.strip())):
            raise ValueError(f"Invalid brief component limitation: {identity}")
        components.append({
            "id": identity,
            "badge": component["badge"],
            **({"limitation": limitation} if limitation else {}),
            "actions": ordered_actions,
        })
        if not components[-1]["actions"]:
            raise ValueError(f"Policy has no current actions: {identity}")
    return {"schema_version": 2, "evidence_commit": fixture["provenance"]["commit"],
            "policy_source": "src/test/resources/diamondfire/component-evidence.json",
            "components": sorted(components, key=lambda component: component["id"])}


def encode(snapshot: dict[str, Any]) -> str:
    return json.dumps(snapshot, ensure_ascii=False, sort_keys=True, indent=2) + "\n"


def runtime_formats(policy: dict[str, Any]) -> dict[str, Any]:
    """Only reviewed encoding differences become UI warnings, not registry-value additions."""
    baseline = re.search(r"^minecraft_version=([0-9]+\.[0-9]+(?:\.[0-9]+)?)$",
                         (DATA / "server.properties").read_text(encoding="utf-8"), re.MULTILINE)
    if baseline is None:
        raise ValueError("Missing DiamondFire server baseline")
    server_version = baseline.group(1)
    if policy["schema_version"] != 1 or policy["server_version"] != server_version:
        raise ValueError("Wrong codec policy baseline")
    server = json.loads((DATA / "server-registry.json").read_text(encoding="utf-8"))
    if server["minecraft_version"] != server_version:
        raise ValueError("Server registry differs from the maintained baseline")
    server_ids = {component["id"] for component in server["components"]}
    versions = {}
    for comparison in policy["comparisons"]:
        version = comparison["client_version"]
        if (not re.fullmatch(r"[0-9]+\.[0-9]+(?:\.[0-9]+)?", version)
                or version in versions or comparison["fixture"] != f"codec-comparison-{version}.json"):
            raise ValueError("Duplicate version or wrong codec comparison fixture")
        client_parts = tuple(map(int, version.split(".")))
        server_parts = tuple(map(int, server_version.split(".")))
        if client_parts + (0,) * (3 - len(client_parts)) < server_parts + (0,) * (3 - len(server_parts)):
            raise ValueError("Older clients have no DiamondFire compatibility profiles")
        report = json.loads((FIXTURES / comparison["fixture"]).read_text(encoding="utf-8"))
        formats, registry = comparison["formats"], comparison["registry_value_only"]
        if (report["schema_version"] != 1 or report["server_version"] != server_version
                or report["client_version"] != version or not comparison["reason"].strip()
                or len(formats) != len(set(formats)) or len(registry) != len(set(registry))
                or set(formats) & set(registry)
                or set(formats + registry) != set(report["codec_differences"])
                or not set(formats + registry) <= server_ids):
            raise ValueError(f"Unreviewed or invalid codec differences: {version}")
        versions[version] = sorted(formats)
    if not versions:
        raise ValueError("Empty codec comparison policy")
    return {"schema_version": 1, "server_version": server_version,
            "policy_source": "src/test/resources/diamondfire/codec-format-policy.json", "client_versions": versions}


def read_pinned_dump(checkout: Path, commit: str, dump_path: str) -> bytes:
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("The df-info revision must be a full commit SHA")
    return subprocess.run([
        "git", "-c", f"safe.directory={checkout.resolve().as_posix()}", "-C", str(checkout),
        "show", f"{commit}:{dump_path}",
    ], check=True, capture_output=True).stdout


def fixture_snapshot(raw: bytes, selection: dict[str, Any], commit: str, *, previous: bool = False) -> dict[str, Any]:
    dump = json.loads(raw)
    patch = dump.get("versions", {}).get("patch")
    if not previous and (not isinstance(patch, str) or not patch.strip()):
        raise ValueError("Current actiondump must declare versions.patch")
    return {
        "schema_version": 1,
        "provenance": {
            "repository": selection["repository"],
            "branch": selection["branch"] if not previous else None,
            "commit": commit,
            "dump": selection["dump"],
            "dump_sha256": hashlib.sha256(raw).hexdigest(),
            "patch": patch,
            "patch_source": "versions.patch" if patch else "absent in historical export; unknown",
        },
        "actions": normalize_actions(dump, selection["display_names"], allow_missing=previous),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--df-info", type=Path, help="Local df-info checkout containing the pinned revisions")
    parser.add_argument("--check", action="store_true", help="Verify generated files without changing them")
    args = parser.parse_args()
    selection = json.loads(SELECTION.read_text(encoding="utf-8"))
    if args.df_info is not None:
        current = fixture_snapshot(read_pinned_dump(args.df_info, selection["commit"], selection["dump"]), selection, selection["commit"])
        previous = fixture_snapshot(read_pinned_dump(args.df_info, selection["previous_commit"], selection["dump"]), selection, selection["previous_commit"], previous=True)
    elif args.check:
        # Local normalized fixtures are sufficient for offline determinism checks.
        current = json.loads((FIXTURES / "actiondump-current.json").read_text(encoding="utf-8"))
        previous = json.loads((FIXTURES / "actiondump-previous.json").read_text(encoding="utf-8"))
        if current["provenance"]["commit"] != selection["commit"] or previous["provenance"]["commit"] != selection["previous_commit"]:
            raise ValueError("Fixture revisions differ from snapshot-selection.json")
        for fixture in (current, previous):
            fixture["actions"] = normalize_actions(fixture, selection["display_names"], allow_missing=fixture is previous)
    else:
        parser.error("Generation requires --df-info; offline --check can use checked-in fixtures")
    outputs = {
        DATA / "actiondump.json": runtime_snapshot(current),
        DATA / "component-policy.json": runtime_policy(json.loads(EVIDENCE.read_text(encoding="utf-8")), current),
        DATA / "component-formats.json": runtime_formats(json.loads((FIXTURES / "codec-format-policy.json").read_text(encoding="utf-8"))),
        FIXTURES / "actiondump-current.json": current,
        FIXTURES / "actiondump-previous.json": previous,
    }
    for destination, snapshot in outputs.items():
        encoded = encode(snapshot)
        if args.check:
            if not destination.exists() or destination.read_text(encoding="utf-8") != encoded:
                raise ValueError(f"Generated snapshot differs: {destination.relative_to(ROOT)}")
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(encoded, encoding="utf-8", newline="\n")
    print(f"{'Verified' if args.check else 'Generated'} {len(current['actions'])} current and {len(previous['actions'])} prior action records")


if __name__ == "__main__":
    main()
