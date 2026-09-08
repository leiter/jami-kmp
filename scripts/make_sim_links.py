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

LIBS = [
    'libargon2', 'libavcodec', 'libavdevice', 'libavfilter', 'libavformat', 'libavutil',
    'libcrypto', 'libdhtnet', 'libfmt', 'libgit2', 'libgmp', 'libgnutls', 'libhogweed',
    'libhttp_parser', 'libixml', 'libjami-core', 'libjsoncpp', 'libllhttp', 'libnatpmp',
    'libnettle', 'libopendht', 'libopus', 'libpj', 'libpjlib-util', 'libpjmedia-audiodev',
    'libpjmedia-codec', 'libpjmedia-videodev', 'libpjmedia', 'libpjnath', 'libpjsip-simple',
    'libpjsip-ua', 'libpjsip', 'libpjsua', 'libpjsua2', 'libsecp256k1', 'libsimdutf',
    'libspeex', 'libspeexdsp', 'libsrtp', 'libssl', 'libswresample', 'libswscale', 'libtls',
    'libupnp', 'libvpx', 'libx264', 'libyaml-cpp', 'libyuv',
]


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

    ok, missing = 0, []
    for lib in LIBS:
        src = xcfw_root / f'{lib}.xcframework' / 'ios-arm64-simulator' / f'{lib}.framework' / lib
        if not src.exists():
            missing.append(lib)
            continue
        # The .def file and build-jamibridge.sh both expect the shorter name.
        dst_name = 'libjami.a' if lib == 'libjami-core' else f'{lib}.a'
        link(src, SIM_LIB / dst_name)
        ok += 1

    if BRIDGE_LIB.exists():
        link(BRIDGE_LIB, SIM_LIB / BRIDGE_LIB.name)
        ok += 1
    else:
        missing.append(BRIDGE_LIB.name)

    print(f'Created {ok} symlinks')
    if missing:
        print('MISSING:', ', '.join(missing))
        return 1
    print('All libs found')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
