#!/usr/bin/env python3
"""Add translations to the Compose string catalogue without overwriting anything.

Input: one or more TSV files, one translation per line:

    <locale folder suffix>\t<key>\t<text>

e.g. "fr\tdate_month_1\tjanvier" or "pt-rBR\timport_failed\tFalha na importação". Lines that are
empty or start with '#' are ignored. The text is written as Compose reads it: '&', '<' and '>'
are escaped, apostrophes and quotes stay bare (see CLAUDE.md "Escaping"); a literal newline is
written as \\n.

Each key goes into the locale file that holds it in values/ (the default catalogue decides the
file). A key already present in that locale file is left alone, so hand-made translations win.
A key that is not in values/, or whose %N$x arguments differ from the English, is reported and
skipped.

Usage:
    python3 scripts/insert_translations.py FILE.tsv [FILE.tsv ...] [--dry-run]
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "shared/src/commonMain/composeResources"
STRING_RE = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
ARG_RE = re.compile(r"%(\d+)\$([sd])")


def default_catalogue() -> dict[str, tuple[str, str]]:
    """key -> (file name, English text)"""
    out: dict[str, tuple[str, str]] = {}
    for f in sorted((ROOT / "values").glob("strings*.xml")):
        for m in STRING_RE.finditer(f.read_text(encoding="utf-8")):
            out[m.group(1)] = (f.name, m.group(2))
    return out


def escape(text: str) -> str:
    return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "\\n"))


def main(argv: list[str]) -> int:
    dry = "--dry-run" in argv
    files = [a for a in argv if not a.startswith("--")]
    if not files:
        print(__doc__)
        return 2
    catalogue = default_catalogue()
    pending: dict[Path, list[tuple[str, str]]] = {}
    problems = 0
    for tsv in files:
        for n, line in enumerate(Path(tsv).read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != 3:
                print(f"{tsv}:{n}: expected 3 tab-separated fields")
                problems += 1
                continue
            folder, key, text = parts[0], parts[1], parts[2].replace("\\n", "\n")
            if key not in catalogue:
                print(f"{tsv}:{n}: {key} is not in values/")
                problems += 1
                continue
            fname, english = catalogue[key]
            if sorted(ARG_RE.findall(english)) != sorted(ARG_RE.findall(text)):
                print(f"{tsv}:{n}: {folder} {key}: arguments differ from English: {text!r}")
                problems += 1
                continue
            target = ROOT / f"values-{folder}" / fname
            if not target.parent.is_dir():
                print(f"{tsv}:{n}: no folder values-{folder}")
                problems += 1
                continue
            pending.setdefault(target, []).append((key, text))

    added = kept = 0
    for target, entries in sorted(pending.items()):
        content = target.read_text(encoding="utf-8")
        present = {m.group(1) for m in STRING_RE.finditer(content)}
        lines = []
        for key, text in entries:
            if key in present:
                kept += 1
                continue
            present.add(key)
            lines.append(f'    <string name="{key}">{escape(text)}</string>\n')
        if not lines:
            continue
        added += len(lines)
        idx = content.rindex("</resources>")
        content = content[:idx] + "".join(lines) + content[idx:]
        if not dry:
            target.write_text(content, encoding="utf-8")
    verb = "would add" if dry else "added"
    print(f"{verb}: {added}, already present (kept): {kept}, problems: {problems}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
