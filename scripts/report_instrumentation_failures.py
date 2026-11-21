#!/usr/bin/env python3
"""Surface failing Android instrumentation tests as readable CI annotations.

The emulator step exits non-zero without saying which test failed. This script
reads the Gradle connectedDebugAndroidTest XML results and:
  * emits one ::error:: annotation per failing test (GitHub caps these at 10),
  * writes the full list to the job summary when GITHUB_STEP_SUMMARY is set,
  * never changes the exit status of the caller, so red CI stays red.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

RESULT_GLOB = "app/build/outputs/androidTest-results/**/*.xml"
MAX_ANNOTATIONS = 10


def escape(value: str) -> str:
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def collect():
    totals = {"tests": 0, "failed": 0, "skipped": 0}
    failures = []
    files = sorted(glob.glob(RESULT_GLOB, recursive=True))
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            totals["tests"] += 1
            if case.find("skipped") is not None:
                totals["skipped"] += 1
            bad = case.find("failure")
            if bad is None:
                bad = case.find("error")
            if bad is not None:
                totals["failed"] += 1
                text = (bad.get("message") or bad.text or "").strip()
                failures.append((case.get("classname", "?"), case.get("name", "?"), text))
    return files, totals, failures


def main() -> int:
    files, totals, failures = collect()
    if not files:
        print("::warning::No instrumentation result XML was found; the emulator or Gradle step likely failed before running tests.")
        return 0
    print(f"Instrumentation results: {totals['tests']} tests, {totals['failed']} failed, {totals['skipped']} skipped")
    for cls, name, text in failures[:MAX_ANNOTATIONS]:
        first = text.splitlines()[0][:400] if text else "no message"
        print(f"::error title=Instrumentation failure::{escape(cls)}#{escape(name)} - {escape(first)}")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write("## Android instrumentation result\n\n")
            out.write(f"{totals['tests']} tests, {totals['failed']} failed, {totals['skipped']} skipped.\n\n")
            for cls, name, text in failures:
                first = text.splitlines()[0][:600] if text else "no message"
                out.write(f"- `{cls}#{name}`: {first}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
