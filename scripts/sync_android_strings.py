#!/usr/bin/env python3
"""Import translations from jami-client-android into the Compose resource catalogue.

The catalogue under shared/src/commonMain/composeResources/ mirrors the Android client's
res/values*/strings*.xml and reuses its key names, so translations can be taken over directly.
The first import (2026-04-10, 29f36b0) was a one-off whose script was never committed; this is
its repeatable replacement.

What it does, for every key in the KMP default catalogue (values/):

  * finds the Android source key — the same name, or the alias in android_string_aliases.txt;
  * takes the key only if the Android English still says the same thing as the KMP English
    (ignoring case, whitespace and %s/%1$s numbering), so a reworded KMP string never receives
    a translation of the old wording;
  * for each Android locale folder, converts the translation to what the Compose reader renders
    and writes it into the KMP file that holds the key in values/ (the KMP file layout decides,
    not Android's).

Only keys the code references (Res.string.* / Res.plurals.* in any .kt file) are synced: the
default catalogue still carries a few hundred imported keys no screen uses, and translating
those only bloats the files. --all-keys lifts that.

By default it only *adds* translations that are missing. --update also refreshes existing
translations that differ from Android's — never in values-de*/ (hand-curated) and never for an
aliased key. --new-locales creates locale folders Android has and KMP does not; run
scripts/generate_shipped_locales.py afterwards.

Usage:
    python3 scripts/sync_android_strings.py [ANDROID] [--dry-run] [--update] [--new-locales]
                                            [--all-keys] [-v]

ANDROID is a jami-client-android checkout, or its res/ directory directly. Default:
$JAMI_CLIENT_ANDROID, else a sibling ../jami-client-android. A sparse clone is enough:
    git clone --depth 1 --filter=blob:none --sparse \\
        https://github.com/savoirfairelinux/jami-client-android.git
    git -C jami-client-android sparse-checkout set jami-android/app/src/main/res

Conversion — Compose's resource build step unescapes only \\n, \\t, \\uXXXX and \\\\
(PrepareComposeResources.handleSpecialCharacters, CMP 1.9.0), unlike AAPT:
  * \\' \\" \\@ \\? are unescaped (they would otherwise render with the backslash);
  * a string wrapped in Android's literal double quotes loses them;
  * <xliff:g> wrappers are dropped, keeping their content;
  * bare %s / %d become positional %1$s / %2$d;
  * whitespace runs collapse to one space, as AAPT does.
A translation is skipped, and counted, if it is empty, uses CDATA or markup the English does not,
or does not carry exactly the English string's format arguments.

Locale folders: only values-ll and values-ll-rRR (the forms Compose's qualifier parser accepts).
Android's values-b+zh+Hans style folders are skipped; Android also ships complete zh-rCN /
zh-rTW / zh-rHK / es folders, and Cyrillic Serbian has no Compose folder name.
"""

from __future__ import annotations

import argparse
import collections
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
KMP_RES = REPO_ROOT / 'shared' / 'src' / 'commonMain' / 'composeResources'
ALIASES = Path(__file__).resolve().parent / 'android_string_aliases.txt'

PROTECTED = {'values-de', 'values-de-rDE'}   # hand-curated German: fill-only, never updated
LOCALE_DIR = re.compile(r'^values-[a-z]{2,3}(-r[A-Z]{2})?$')
ELEMENT = re.compile(r'<(string|plurals)\s+name="([^"]+)"([^>]*)>(.*?)</\1>', re.S)
ITEM = re.compile(r'<item\s+quantity="([^"]+)"\s*>(.*?)</item>', re.S)
FORMAT = re.compile(r'%(?:(\d+)\$)?([sd])')

NEW_FILE = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n'


# ---------------------------------------------------------------------------------------------
# Reading
# ---------------------------------------------------------------------------------------------

class Entry:
    def __init__(self, kind: str, body: str, attrs: str, file: str):
        self.kind = kind      # 'string' or 'plurals'
        self.body = body      # raw inner XML
        self.attrs = attrs
        self.file = file

    @property
    def translatable(self) -> bool:
        return 'translatable="false"' not in self.attrs


def read_folder(folder: Path) -> dict[str, Entry]:
    out: dict[str, Entry] = {}
    for f in sorted(folder.glob('strings*.xml')):
        text = re.sub(r'<!--.*?-->', '', f.read_text(encoding='utf-8'), flags=re.S)
        for m in ELEMENT.finditer(text):
            out[m.group(2)] = Entry(m.group(1), m.group(4), m.group(3), f.name)
    return out


def used_keys() -> set[str]:
    used: set[str] = set()
    pattern = re.compile(r'Res\.(?:string|plurals)\.([A-Za-z0-9_]+)')
    for root in ('shared', 'android-app', 'desktop-app', 'web-app'):
        for f in (REPO_ROOT / root).rglob('*.kt'):
            if 'build' in f.relative_to(REPO_ROOT).parts:
                continue
            used.update(pattern.findall(f.read_text(encoding='utf-8', errors='replace')))
    return used


def read_aliases() -> tuple[dict[str, str], set[tuple[str, str]]]:
    aliases: dict[str, str] = {}
    excludes: set[tuple[str, str]] = set()
    for n, raw in enumerate(ALIASES.read_text(encoding='utf-8').splitlines(), 1):
        line = raw.split('#', 1)[0].strip()
        if not line:
            continue
        if line.startswith('!'):
            parts = line[1:].split()
            if len(parts) != 2:
                sys.exit(f'{ALIASES.name}:{n}: expected "! <values-folder> <kmp_key>"')
            excludes.add((parts[0], parts[1]))
            continue
        if '=' not in line:
            sys.exit(f'{ALIASES.name}:{n}: expected "kmp_key = android_key"')
        kmp, android = (s.strip() for s in line.split('=', 1))
        if kmp in aliases:
            sys.exit(f'{ALIASES.name}:{n}: {kmp} aliased twice')
        aliases[kmp] = android
    return aliases, excludes


# ---------------------------------------------------------------------------------------------
# Conversion
# ---------------------------------------------------------------------------------------------

def positional(text: str) -> str:
    """%s %d -> %1$s %2$d, numbering the bare ones in order; numbered ones are left alone."""
    counter = iter(range(1, 100))
    return FORMAT.sub(lambda m: m.group(0) if m.group(1) else f'%{next(counter)}${m.group(2)}',
                      text.replace('%%', '\0')).replace('\0', '%%')


def convert_text(text: str) -> str | None:
    """Android string body -> Compose string body, or None if it cannot be taken over."""
    if '<![CDATA[' in text:
        return None
    text = re.sub(r'</?xliff:g[^>]*>', '', text)
    text = ' '.join(text.split())
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"' and not text.endswith('\\"'):
        text = text[1:-1]
    text = re.sub(r'(?<!\\)\\([\'"@?])', r'\1', text)
    return positional(text)


def convert(entry: Entry) -> str | None:
    if entry.kind == 'string':
        return convert_text(entry.body)
    items = []
    for quantity, body in ITEM.findall(entry.body):
        converted = convert_text(body)
        if converted is None:
            return None
        items.append(f'        <item quantity="{quantity}">{converted}</item>')
    return '\n' + '\n'.join(items) + '\n    ' if items else None


def comparable(entry: Entry) -> str:
    """The English meaning: converted, case-folded, numbering-blind."""
    text = convert(entry) or ''
    return ' '.join(FORMAT.sub(r'%\2', text).split()).casefold()


def format_args(text: str) -> collections.Counter:
    return collections.Counter(f'{m.group(1)}{m.group(2)}' for m in FORMAT.finditer(text))


def has_markup(text: str) -> bool:
    return re.search(r'<[a-zA-Z/]', text) is not None


def acceptable(translation: str, english: str) -> str | None:
    """Reason to reject a converted translation, or None."""
    if not re.sub(r'<[^>]+>', '', translation).strip():
        return 'empty'
    if has_markup(translation) and not has_markup(english):
        return 'markup'
    if format_args(translation) != format_args(english):
        return 'format arguments differ'
    return None


# ---------------------------------------------------------------------------------------------
# Writing
# ---------------------------------------------------------------------------------------------

def element(kind: str, key: str, body: str) -> str:
    return f'    <{kind} name="{key}">{body}</{kind}>'


def write_changes(folder: Path, adds: dict[str, list[str]],
                  updates: dict[str, dict[str, tuple[str, str]]]) -> None:
    folder.mkdir(exist_ok=True)
    for file in sorted(set(adds) | set(updates)):
        path = folder / file
        text = path.read_text(encoding='utf-8') if path.exists() else NEW_FILE
        for key, (kind, body) in updates.get(file, {}).items():
            pattern = re.compile(rf'(<{kind}\s+name="{re.escape(key)}"[^>]*>)(.*?)(</{kind}>)', re.S)
            text, n = pattern.subn(lambda m: m.group(1) + body + m.group(3), text, count=1)
            assert n == 1, f'{path}: could not update {key}'
        if file in adds:
            end = text.rindex('</resources>')
            text = text[:end].rstrip('\n') + '\n' + '\n'.join(adds[file]) + '\n' + text[end:]
        path.write_text(text, encoding='utf-8')


# ---------------------------------------------------------------------------------------------

def find_android_res(arg: str | None) -> Path:
    base = Path(arg or os.environ.get('JAMI_CLIENT_ANDROID', REPO_ROOT.parent / 'jami-client-android'))
    base = base.expanduser()
    for candidate in (base, base / 'jami-android' / 'app' / 'src' / 'main' / 'res'):
        if (candidate / 'values' / 'strings.xml').is_file():
            return candidate
    sys.exit(f'error: no Android res/ found at {base} (see --help for a sparse clone)')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('android', nargs='?', help='jami-client-android checkout or its res/')
    parser.add_argument('--dry-run', action='store_true', help='report, write nothing')
    parser.add_argument('--update', action='store_true',
                        help='also refresh existing translations that differ (not German, not aliases)')
    parser.add_argument('--new-locales', action='store_true',
                        help='create locale folders that only Android has')
    parser.add_argument('--all-keys', action='store_true',
                        help='also sync keys no Kotlin code references')
    parser.add_argument('--verbose', '-v', action='store_true', help='list every skipped string')
    args = parser.parse_args()

    android_res = find_android_res(args.android)
    aliases, excludes = read_aliases()
    kmp_en = read_folder(KMP_RES / 'values')
    android_en = {k: e for k, e in read_folder(android_res / 'values').items() if e.translatable}

    # Which KMP keys take which Android key.
    used = None if args.all_keys else used_keys()
    source: dict[str, str] = {}
    drift: list[str] = []
    for key, entry in kmp_en.items():
        if not entry.translatable or (used is not None and key not in used):
            continue
        android_key = aliases.get(key, key)
        a = android_en.get(android_key)
        if a is None:
            if key in aliases:
                drift.append(f'{key} = {android_key}: Android key no longer exists')
            continue
        if a.kind != entry.kind or comparable(a) != comparable(entry):
            drift.append(f'{key}' + (f' = {android_key}' if key in aliases else '') +
                         f': English differs ("{convert(entry)}" vs Android "{convert(a)}")')
            continue
        source[key] = android_key
    for key, android_key in aliases.items():
        if key not in kmp_en:
            drift.append(f'{key} = {android_key}: not in the KMP default catalogue')

    print(f'Android res: {android_res}')
    print(f'KMP keys: {len(kmp_en)}' + ('' if used is None else f', {len(used & kmp_en.keys())} used in code') +
          f'; taking translations for {len(source)} '
          f'({sum(1 for k in source if k in aliases)} via aliases)')

    totals = collections.Counter()
    skipped = collections.Counter()
    skipped_detail: list[str] = []
    new_folders: list[str] = []

    for a_dir in sorted(p for p in android_res.iterdir() if p.is_dir() and LOCALE_DIR.match(p.name)):
        k_dir = KMP_RES / a_dir.name
        if not k_dir.is_dir():
            if not args.new_locales:
                totals['folders only Android has (skipped)'] += 1
                continue
            new_folders.append(a_dir.name)
        android_loc = read_folder(a_dir)
        kmp_loc = read_folder(k_dir) if k_dir.is_dir() else {}
        adds: dict[str, list[str]] = collections.defaultdict(list)
        updates: dict[str, dict[str, tuple[str, str]]] = collections.defaultdict(dict)

        for key, android_key in source.items():
            if (a_dir.name, key) in excludes or android_key not in android_loc:
                continue
            english = kmp_en[key]
            converted = convert(android_loc[android_key])
            reason = 'markup or CDATA' if converted is None else acceptable(converted, convert(english))
            if reason:
                skipped[reason] += 1
                skipped_detail.append(f'{a_dir.name}/{key}: {reason}')
                continue
            existing = kmp_loc.get(key)
            if existing is None:
                adds[english.file].append(element(english.kind, key, converted))
                totals['added'] += 1
            elif convert(existing) != converted:
                if args.update and a_dir.name not in PROTECTED and key not in aliases:
                    updates[existing.file][key] = (english.kind, converted)
                    totals['updated'] += 1
                else:
                    totals['differ, kept'] += 1

        if (adds or updates) and not args.dry_run:
            write_changes(k_dir, adds, updates)
        n = sum(len(v) for v in adds.values()) + sum(len(v) for v in updates.values())
        if n:
            totals['folders changed'] += 1
            if args.verbose:
                print(f'  {a_dir.name}: +{sum(len(v) for v in adds.values())}'
                      f' ~{sum(len(v) for v in updates.values())}')

    print('\n' + ('Would change' if args.dry_run else 'Changed') + ':')
    for label in ('added', 'updated', 'differ, kept', 'folders changed',
                  'folders only Android has (skipped)'):
        if totals[label]:
            print(f'  {label}: {totals[label]}')
    if totals['differ, kept'] and not args.update:
        print('    (existing translations that differ from Android; --update refreshes them)')
    if skipped:
        print('Skipped translations: ' + ', '.join(f'{r} {n}' for r, n in skipped.items()))
        if args.verbose:
            for line in skipped_detail:
                print(f'    {line}')
    if drift:
        print(f'\nKeys whose English no longer matches their Android source ({len(drift)}):')
        for line in drift:
            print(f'  {line}')
    if new_folders:
        print('\nNew locale folders:', ', '.join(new_folders))
        print('Run scripts/generate_shipped_locales.py to put them in the language picker.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
