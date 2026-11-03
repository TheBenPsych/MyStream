"""Print Android lint problems in plain text so they show up in the build log.

Usage: python3 tools/lint_summary.py app/build/reports/lint-results-defaultDebug.xml

Problems that crash on older Android versions (NewApi and friends) are listed
first. The script fails the step only when one of those crash problems is found.
"""

import collections
import sys
import xml.etree.ElementTree as ET

CRASH_IDS = {
    "NewApi",
    "InlinedApi",
    "WrongConstant",
    "MissingPermission",
    "UnspecifiedRegisterReceiverFlag",
    "ScopedStorage",
    "ResourceType",
    "StringFormatInvalid",
    "StringFormatCount",
    "StringFormatMatches",
    "MissingDefaultResource",
    "Range",
}


def main(path: str) -> int:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as e:
        print(f"No lint report found ({e}).")
        return 0
    issues = []
    for issue in root.iter("issue"):
        loc = issue.find("location")
        where = ""
        if loc is not None:
            f = loc.get("file", "")
            f = f.split("/src/main/")[-1] if "/src/main/" in f else f
            where = f"{f}:{loc.get('line', '?')}"
        issues.append((issue.get("id"), issue.get("severity"), where, issue.get("message")))

    crash = [i for i in issues if i[0] in CRASH_IDS and i[1] in ("Error", "Fatal")]
    print(f"== Crash risks on older Android: {len(crash)}")
    for id_, sev, where, msg in crash:
        print(f"[{id_}] {where}\n    {msg}")

    print("\n== Everything else, by type:")
    counts = collections.Counter((i[0], i[1]) for i in issues if i not in crash)
    for (id_, sev), n in counts.most_common():
        print(f"{n:5d}  {sev:8s} {id_}")
    for id_, sev, where, msg in issues:
        if sev in ("Error", "Fatal") and id_ not in CRASH_IDS:
            print(f"[{id_}] {where}\n    {msg}")
    return 1 if crash else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
