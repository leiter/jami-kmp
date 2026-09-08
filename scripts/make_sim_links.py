#!/usr/bin/env python3
"""Recreate the iOS *simulator* native-library symlinks in lib-sim/.

The simulator libraries are machine-specific build products of a sibling
jami-client-ios checkout, so they are not committed. This script relinks them.

Paths are derived from this file's own location, so the script works from any
checkout. Two environment variables override the defaults:

    JAMI_CLIENT_IOS   path to the jami-client-ios checkout
                      (default: a sibling of this repository)
    JAMI_XCFRAMEWORK  path to its xcframework directory
                      (default: $JAMI_CLIENT_IOS/xcframework)

Note that jami-client-ios must have been *built* — xcframework/ is a build
product, not a checked-in source directory.

See shared/src/nativeInterop/cinterop/JamiBridge/README.md for the full setup,
including the device-side symlinks in lib/, which this script does not touch.
"""

import os
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CINTEROP = REPO_ROOT / 'shared' / 'src' / 'nativeInterop' / 'cinterop'
SIM_LIB = CINTEROP / 'lib-sim'

client_ios = Path(
    os.environ.get('JAMI_CLIENT_IOS', REPO_ROOT.parent / 'jami-client-ios')
).expanduser()
xcfw_root = Path(
    os.environ.get('JAMI_XCFRAMEWORK', client_ios / 'xcframework')
).expanduser()

# The compiled ObjC++ bridge wrapper is tracked in git, unlike everything else
# linked here; lib-sim/ just needs a link to it alongside the daemon libraries.
BRIDGE_LIB = CINTEROP / 'lib' / 'libJamiBridge_iossim.a'

# The library set and the slice name are both discovered from the xcframework
# directory rather than hardcoded. Both have changed under us before:
#   - http_parser was dropped upstream in favour of llhttp
#   - the simulator slice is named ios-arm64-simulator when only arm64 is built,
#     but ios-arm64_x86_64-simulator when compile-ios.sh --platform=all builds both
# A hardcoded list silently mismatches after a daemon update; discovery does not.
def is_arm64_simulator_slice(name: str) -> bool:
    """Match ios-arm64-simulator and ios-arm64_x86_64-simulator, not ios-x86_64-simulator."""
    return 'simulator' in name and 'arm64' in name


def link(src: Path, dst: Path) -> None:
    """Replace dst with a symlink to src, tolerating a broken existing link."""
    if dst.is_symlink() or dst.exists():
        dst.unlink()
    dst.symlink_to(src)


def main() -> int:
    if not xcfw_root.is_dir():
        print(f'error: xcframework directory not found: {xcfw_root}', file=sys.stderr)
        print('       Set JAMI_CLIENT_IOS or JAMI_XCFRAMEWORK, and make sure', file=sys.stderr)
        print('       jami-client-ios has been built (xcframework/ is a build product).',
              file=sys.stderr)
        return 1

    SIM_LIB.mkdir(parents=True, exist_ok=True)
    print(f'xcframework: {xcfw_root}')
    print(f'target:      {SIM_LIB}')

    ok, no_slice = 0, []
    frameworks = sorted(xcfw_root.glob('*.xcframework'))
    if not frameworks:
        print(f'error: no .xcframework bundles under {xcfw_root}', file=sys.stderr)
        return 1

    for fw in frameworks:
        name = fw.name[: -len('.xcframework')]
        slice_dir = next((d for d in sorted(fw.iterdir())
                          if d.is_dir() and is_arm64_simulator_slice(d.name)), None)
        if slice_dir is None:
            no_slice.append(name)
            continue
        src = slice_dir / f'{name}.framework' / name
        if not src.exists():
            no_slice.append(name)
            continue
        # The .def file and build-jamibridge.sh both expect the shorter name.
        dst_name = 'libjami.a' if name == 'libjami-core' else f'{name}.a'
        link(src, SIM_LIB / dst_name)
        ok += 1

    if BRIDGE_LIB.exists():
        link(BRIDGE_LIB, SIM_LIB / BRIDGE_LIB.name)
        ok += 1
    else:
        print(f'error: {BRIDGE_LIB} not found — build it with '
              'shared/src/nativeInterop/cinterop/JamiBridge/build-jamibridge.sh',
              file=sys.stderr)
        return 1

    print(f'Created {ok} symlinks')
    if no_slice:
        # Not fatal: some libraries legitimately have no arm64-simulator slice
        # (libvpx does not support the arm64 simulator, so ffmpeg is configured
        # with --disable-libvpx there and nothing references its symbols).
        print(f'No arm64-simulator slice ({len(no_slice)}):', ', '.join(no_slice))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
