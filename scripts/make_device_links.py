#!/usr/bin/env python3
"""Recreate the iOS *device* native-library symlinks in lib/, and check them for drift.

The device libraries are machine-specific build products of a sibling jami-client-ios
checkout (its DEPS/arm64-iPhoneOS/lib/), so they are not committed. lib/ used to be
assembled by hand, and it drifted silently whenever the daemon's dependency set changed
(http_parser dropped for llhttp, yrs added): the symptom is an undefined-symbol error at
the Xcode app link, never a compile error. This script rebuilds lib/ by discovery and
cross-checks the app's OTHER_LDFLAGS[sdk=iphoneos*] in project.pbxproj, which drifts the
same way.

Usage:
    python3 scripts/make_device_links.py          # relink, then check
    python3 scripts/make_device_links.py --check  # change nothing; exit 1 on any drift

Paths are derived from this file's own location. Environment overrides:

    JAMI_CLIENT_IOS   path to the jami-client-ios checkout (default: a sibling of this repo)
    JAMI_DEPS_LIB     path to the device library directory
                      (default: $JAMI_CLIENT_IOS/DEPS/arm64-iPhoneOS/lib)

Only symlinks in lib/ are ever created or removed. Regular files there — the tracked
libJamiBridge_ios.a / libJamiBridge_iossim.a — are never touched.

The simulator counterpart is scripts/make_sim_links.py.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CINTEROP = REPO_ROOT / 'shared' / 'src' / 'nativeInterop' / 'cinterop'
DEVICE_LIB = CINTEROP / 'lib'
PBXPROJ = REPO_ROOT / 'ios-app' / 'iosApp.xcodeproj' / 'project.pbxproj'

client_ios = Path(
    os.environ.get('JAMI_CLIENT_IOS', REPO_ROOT.parent / 'jami-client-ios')
).expanduser()
deps_lib = Path(
    os.environ.get('JAMI_DEPS_LIB', client_ios / 'DEPS' / 'arm64-iPhoneOS' / 'lib')
).expanduser()

# Linked by the app from the SDK or from this repo, not from DEPS.
NOT_FROM_DEPS = {'c++', 'iconv', 'resolv', 'sqlite3', 'z', 'JamiBridge_ios'}

# The .def file, build-jamibridge.sh and OTHER_LDFLAGS (-ljami) all use the short name.
RENAMES = {'libjami-core.a': 'libjami.a'}


def lib_name(file_name: str) -> str:
    """libfoo.a -> foo"""
    return file_name[len('lib'):-len('.a')]


def wanted_links() -> dict[str, Path]:
    """Destination file name in lib/ -> source library in DEPS."""
    return {RENAMES.get(src.name, src.name): src for src in sorted(deps_lib.glob('lib*.a'))}


def pbxproj_device_libs() -> set[str] | None:
    """The -l names in OTHER_LDFLAGS[sdk=iphoneos*], minus system and in-repo libraries."""
    if not PBXPROJ.exists():
        return None
    libs: set[str] = set()
    text = PBXPROJ.read_text()
    for block in re.finditer(r'"OTHER_LDFLAGS\[sdk=iphoneos\*\]"\s*=\s*\((.*?)\);', text, re.S):
        libs.update(re.findall(r'"?-l([^\s",]+)"?', block.group(1)))
    return libs - NOT_FROM_DEPS


def relink(wanted: dict[str, Path]) -> tuple[int, list[str]]:
    DEVICE_LIB.mkdir(parents=True, exist_ok=True)
    made = 0
    for dst_name, src in wanted.items():
        dst = DEVICE_LIB / dst_name
        if dst.exists() and not dst.is_symlink():
            print(f'warning: {dst} is a regular file; left alone', file=sys.stderr)
            continue
        if dst.is_symlink():
            if Path(os.readlink(dst)) == src:
                continue
            dst.unlink()
        dst.symlink_to(src)
        made += 1
    removed = []
    for dst in sorted(DEVICE_LIB.glob('*.a')):
        if dst.is_symlink() and dst.name not in wanted:
            dst.unlink()
            removed.append(dst.name)
    return made, removed


def check(wanted: dict[str, Path]) -> list[str]:
    problems = []
    for dst_name, src in wanted.items():
        dst = DEVICE_LIB / dst_name
        if not dst.is_symlink() and not dst.exists():
            problems.append(f'missing in lib/: {dst_name}')
        elif dst.is_symlink() and Path(os.readlink(dst)) != src:
            problems.append(f'points elsewhere: {dst_name} -> {os.readlink(dst)}')
    for dst in sorted(DEVICE_LIB.glob('*.a')):
        if dst.is_symlink() and not dst.exists():
            problems.append(f'broken symlink: {dst.name} -> {os.readlink(dst)}')
        elif dst.is_symlink() and dst.name not in wanted:
            problems.append(f'not in DEPS any more: {dst.name}')

    available = {lib_name(n) for n in wanted}
    referenced = pbxproj_device_libs()
    if referenced is None:
        problems.append(f'project file not found: {PBXPROJ}')
    else:
        # Referenced but absent: the Xcode device link fails with undefined symbols.
        for name in sorted(referenced - available):
            problems.append(f'OTHER_LDFLAGS[sdk=iphoneos*] links -l{name}, which DEPS does not have')
        # Present but unreferenced: a new daemon dependency the app does not link yet.
        for name in sorted(available - referenced):
            problems.append(f'DEPS has lib{name}.a, which OTHER_LDFLAGS[sdk=iphoneos*] does not link')
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    parser.add_argument('--check', action='store_true',
                        help='report drift without changing anything; exit 1 if there is any')
    args = parser.parse_args()

    if not deps_lib.is_dir():
        print(f'error: device library directory not found: {deps_lib}', file=sys.stderr)
        print('       Set JAMI_CLIENT_IOS or JAMI_DEPS_LIB, and make sure jami-client-ios',
              file=sys.stderr)
        print('       has been built for the device (DEPS/ is a build product).', file=sys.stderr)
        return 1
    wanted = wanted_links()
    if 'libjami.a' not in wanted:
        print(f'error: no libjami-core.a in {deps_lib}', file=sys.stderr)
        return 1

    print(f'DEPS:   {deps_lib} ({len(wanted)} libraries)')
    print(f'target: {DEVICE_LIB}')

    if not args.check:
        made, removed = relink(wanted)
        print(f'Linked {made} new or changed, {len(wanted) - made} already correct')
        if removed:
            print(f'Removed {len(removed)} stale:', ', '.join(removed))

    problems = check(wanted)
    if problems:
        print(f'\n{len(problems)} problem(s):')
        for p in problems:
            print(f'  - {p}')
        if any('OTHER_LDFLAGS' in p for p in problems):
            print('\nOTHER_LDFLAGS problems are fixed in ios-app/iosApp.xcodeproj/project.pbxproj;'
                  ' see the drift section of shared/src/nativeInterop/cinterop/JamiBridge/README.md.')
        elif args.check:
            print('\nRun without --check to relink.')
        return 1
    print('No drift: lib/, DEPS and OTHER_LDFLAGS[sdk=iphoneos*] agree.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
