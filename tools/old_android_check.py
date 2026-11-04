"""Find Android classes that older devices don't have, used where they can crash the app.

Usage: python3 tools/old_android_check.py [source folder]

Why: Benflix installs on Android 6 and up (minSdk). Some Android classes only exist on
newer versions (for example DynamicsProcessing, Android 9+). Lint's NewApi check only
looks at *calls*. It misses the case that crashed an Android 7 Fire TV in Nov 2026:
a function returned a DynamicsProcessing? value, so the screen code holding that value
mentioned the class too, without any version check, and Android 7 crashed loading it.

Rule this script checks: a newer-than-minSdk Android class may only be named
  * inside a class/function marked @RequiresApi / @TargetApi, or
  * inside an `if (Build.VERSION.SDK_INT >= ...) { ... }` block, or
  * after an early `if (Build.VERSION.SDK_INT < ...) return` in the same function body.
Naming it in a function signature, a property type or a generic type outside those
places is reported, because that lets the class leak into code with no version check.

The list of when each class was added comes from the Android SDK's api-versions.xml.
Exits 1 when it finds a problem, so the PR build fails.
"""

import glob
import os
import re
import sys
import xml.etree.ElementTree as ET


def read_min_sdk() -> int:
    try:
        with open("gradle/libs.versions.toml") as f:
            m = re.search(r'^minSdk\s*=\s*"(\d+)"', f.read(), re.M)
            if m:
                return int(m.group(1))
    except OSError:
        pass
    return 23


def find_api_versions() -> str | None:
    roots = [os.environ.get(k) for k in ("ANDROID_HOME", "ANDROID_SDK_ROOT")]
    roots += [os.path.expanduser("~/Android/Sdk"), "/usr/local/lib/android/sdk"]
    found = []
    for root in filter(None, roots):
        found += glob.glob(os.path.join(root, "platforms", "android-*", "data", "api-versions.xml"))
    if not found:
        return None

    def level(p: str) -> int:
        m = re.search(r"android-(\d+)", p)
        return int(m.group(1)) if m else 0

    return max(found, key=level)


def load_new_classes(path: str, min_sdk: int) -> dict[str, int]:
    """Top-level android.* classes added after min_sdk -> the Android version that added them"""
    out: dict[str, int] = {}
    for _, el in ET.iterparse(path):
        if el.tag != "class":
            continue
        name = el.get("name", "")
        since = int(el.get("since", "1").split(".")[0])  # can be "36.1"
        el.clear()
        if not name.startswith("android/") or "$" in name or since <= min_sdk:
            continue
        out[name.replace("/", ".")] = since
    return out


GUARD_ANNOTATION = re.compile(r"@(RequiresApi|TargetApi|ChecksSdkIntAtLeast)\b")
SDK_CHECK = re.compile(r"SDK_INT\s*(>=|>)")
SDK_EARLY_RETURN = re.compile(r"SDK_INT\s*(<|<=).*\breturn\b")
IMPORT = re.compile(r"^\s*import\s+(android\.[\w.]+)")


def strip_comments_and_strings(line: str, in_block: bool) -> tuple[str, bool]:
    out = []
    i = 0
    while i < len(line):
        if in_block:
            end = line.find("*/", i)
            if end < 0:
                return "".join(out), True
            i = end + 2
            in_block = False
            continue
        if line.startswith("/*", i):
            in_block = True
            i += 2
            continue
        if line.startswith("//", i):
            break
        if line[i] == '"':
            j = i + 1
            while j < len(line) and line[j] != '"':
                j += 2 if line[j] == "\\" else 1
            out.append('""')
            i = j + 1
            continue
        out.append(line[i])
        i += 1
    return "".join(out), in_block


def check_file(path: str, new_classes: dict[str, int]) -> list[str]:
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()

    watched: dict[str, int] = {}
    for line in lines:
        m = IMPORT.match(line)
        if m and m.group(1) in new_classes:
            watched[m.group(1).rsplit(".", 1)[1]] = new_classes[m.group(1)]
    if not watched:
        return []
    name_re = re.compile(r"\b(" + "|".join(map(re.escape, watched)) + r")\b")

    problems = []
    # Each open brace pushes whether the code inside it is protected by a version check
    stack: list[bool] = [False]
    pending_guard = False  # @RequiresApi / SDK_INT if seen, waiting for its opening brace
    in_block = False
    for num, raw in enumerate(lines, 1):
        if IMPORT.match(raw):
            continue
        code, in_block = strip_comments_and_strings(raw, in_block)
        sdk_if = bool(SDK_CHECK.search(code))
        if GUARD_ANNOTATION.search(code) or sdk_if:
            pending_guard = True
        guarded_here = stack[-1] or pending_guard
        if SDK_EARLY_RETURN.search(code):
            # Everything after this in the same body only runs on new enough Android
            stack[-1] = True
        m = name_re.search(code)
        if m and not guarded_here:
            cls = m.group(1)
            problems.append(
                f"{path}:{num}: {cls} only exists on Android API {watched[cls]}+, but is named here "
                f"without a version guard. Move it into a @RequiresApi class and hand out a plain "
                f"interface instead.\n    {raw.strip()}"
            )
        for ch in code:
            if ch == "{":
                stack.append(stack[-1] or pending_guard)
                pending_guard = False
            elif ch == "}" and len(stack) > 1:
                stack.pop()
        if sdk_if and "{" not in code:
            # A one-line `if (SDK_INT >= X) doThing()` only guards its own line
            pending_guard = False
    return problems


def main(src: str) -> int:
    min_sdk = read_min_sdk()
    api = find_api_versions()
    if api is None:
        print("::warning::Android SDK api-versions.xml not found, old Android check skipped")
        return 0
    new_classes = load_new_classes(api, min_sdk)
    print(f"Checking for Android classes newer than API {min_sdk} ({len(new_classes)} known, from {api})")
    problems = []
    for path in sorted(glob.glob(os.path.join(src, "**", "*.kt"), recursive=True)):
        problems += check_file(path, new_classes)
    for p in problems:
        print("::error::" + p.split("\n")[0])
        print(p)
    if problems:
        print(f"\n{len(problems)} place(s) can crash on older Android (below API 28 etc.).")
        return 1
    print("No unguarded newer Android classes found.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"))
