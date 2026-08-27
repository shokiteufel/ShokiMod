"""Zaehlt die Versionsnummer eine Stelle hoch.

Gezaehlt wird zur Basis 20 statt zur Basis 10: eine Stelle laeuft von 0 bis 19
und traegt danach in die naechsthoehere weiter. 1.0.19 wird also zu 1.1.0,
nicht zu 1.0.20, und 1.19.0 wird zu 2.0.0.

Aufruf im Projektverzeichnis:  python bump-version.py
Nur anzeigen, nichts aendern:  python bump-version.py --dry-run
"""
import io
import re
import sys

PROPERTIES = "gradle.properties"
BASE = 20


def bump(major, minor, patch):
    patch += 1
    if patch >= BASE:
        patch = 0
        minor += 1
    if minor >= BASE:
        minor = 0
        major += 1
    return major, minor, patch


def main():
    text = io.open(PROPERTIES, encoding="utf-8").read()
    match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)(\+.*)?$", text, re.M)
    if not match:
        print("mod_version nicht gefunden oder unerwartetes Format", file=sys.stderr)
        return 1

    major, minor, patch = (int(g) for g in match.group(1, 2, 3))
    suffix = match.group(4) or ""
    old = "%d.%d.%d%s" % (major, minor, patch, suffix)
    new = "%d.%d.%d%s" % (bump(major, minor, patch) + (suffix,))

    print("%s -> %s" % (old, new))
    if "--dry-run" in sys.argv:
        return 0

    io.open(PROPERTIES, "w", encoding="utf-8").write(
        text.replace("mod_version=" + old, "mod_version=" + new, 1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
