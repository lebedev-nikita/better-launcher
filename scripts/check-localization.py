#!/usr/bin/env python3
"""Check app-owned translations for missing entries and incompatible format arguments."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
resources = root / "app/src/main/res"

def entries(directory):
    return {node.get("name"): node for node in ET.parse(resources / directory / "strings.xml").getroot()}

english = entries("values")
russian = entries("values-ru")
translatable = {name for name, node in english.items() if node.get("translatable") != "false"}
assert translatable == set(russian), f"Translation keys differ: {translatable ^ set(russian)}"
pattern = re.compile(r"%(\d+)\$([dsf])")

def arguments(text):
    return sorted(pattern.findall(text or ""))

for name in translatable:
    en, ru = english[name], russian[name]
    assert en.tag == ru.tag, f"Resource type differs: {name}"
    if en.tag == "plurals":
        assert {item.get("quantity") for item in en} >= {"one", "other"}, name
        assert {item.get("quantity") for item in ru} >= {"one", "few", "many", "other"}, name
        expected = arguments(en.find("item").text)
        assert all(arguments(item.text) == expected for item in [*en, *ru]), name
    else:
        assert arguments(en.text) == arguments(ru.text), f"Format arguments differ: {name}"
    assert all((item.text or "").strip() for item in ([en] if en.tag == "string" else list(en))), name
    assert not re.search("[А-Яа-яЁё]", "".join(en.itertext())), f"Russian text in English resource: {name}"

print(f"PASS: {len(translatable)} resource entries have complete English and Russian translations")
