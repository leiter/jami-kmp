#!/usr/bin/env python3
"""Recreate the macOS native-library symlinks in lib-macos/.

Unlike iOS, the macOS libraries do not come from an xcframework. They are the
plain static libraries the jami-daemon contrib system installs into its prefix,
plus libjami.a from the daemon build itself. Both are machine-specific build
products of a sibling jami-client-ios checkout, so they are not committed.

Paths are derived from this file's own location. Three environment variables
override the defaults:

    JAMI_CLIENT_IOS   path to the jami-client-ios checkout (which carries the
                      jami-daemon submodule)
                      (default: a sibling of this repository)
    JAMI_DAEMON       path to the daemon checkout
                      (default: $JAMI_CLIENT_IOS/daemon)
    JAMI_CONTRIB      path to the contrib install prefix
                      (default: the single arm64-apple-darwin* directory under
                      $JAMI_DAEMON/contrib)

The contrib prefix name embeds the host Darwin version (e.g.
arm64-apple-darwin25.6.0), which changes with macOS updates, so it is
discovered rather than hardcoded — the same reason make_sim_links.py discovers
the xcframework slice name instead of hardcoding it.

Build the inputs first:

    cd $JAMI_DAEMON/contrib && mkdir -p native-macos-arm64 && cd native-macos-arm64
    ../bootstrap --disable-plugin --disable-libav --ignore-system-libs && make -j4
    # then the daemon itself, which produces libjami-core.a

--ignore-system-libs is required, not cosmetic: without it contrib finds a
Homebrew GMP via pkg-config and skips building its own, nettle then builds
without GMP support and never produces libhogweed.a, and gnutls fails much
later with a misleading "Libhogweed ... was not found". See the README.

See shared/src/nativeInterop/cinterop/JamiBridge/README.md for the full setup.
"""

import os
import sys
from pathlib import Path
from typing import Optional

REPO_ROOT = Path(__file__).resolve().parent.parent
CINTEROP = REPO_ROOT / 'shared' / 'src' / 'nativeInterop' / 'cinterop'
MACOS_LIB = CINTEROP / 'lib-macos'

client_ios = Path(
    os.environ.get('JAMI_CLIENT_IOS', REPO_ROOT.parent / 'jami-client-ios')
).expanduser()
daemon = Path(os.environ.get('JAMI_DAEMON', client_ios / 'daemon')).expanduser()

# The compiled ObjC++ bridge wrapper is tracked in git, unlike everything else
# linked here. It is built by JamiBridge/build-jamibridge.sh --target=macos.
BRIDGE_LIB = MACOS_LIB / 'libJamiBridge_macos.a'


def find_contrib_prefix() -> Optional[Path]:
    """Locate the contrib install prefix for a native macOS build."""
    override = os.environ.get('JAMI_CONTRIB')
    if override:
        return Path(override).expanduser()
    # contrib creates several sibling directories matching *-apple-darwin*: the
    # install prefix (arm64-apple-darwin25.6.0), its own build tree
    # (build-arm64-apple-darwin25.6.0) and the bootstrap dir (native-macos-arm64).
    # Select on structure — an install prefix is the one with a lib/ holding
    # static libraries — rather than on the name, which varies by host.
    candidates = [
        c for c in sorted((daemon / 'contrib').glob('*-apple-darwin*'))
        if c.is_dir() and any((c / 'lib').glob('*.a'))
    ]
    return candidates[-1] if candidates else None


def link(src: Path, dst: Path) -> None:
    """Replace dst with a symlink to src, tolerating a broken existing link."""
    if dst.is_symlink() or dst.exists():
        dst.unlink()
    dst.symlink_to(src)


def main() -> int:
    prefix = find_contrib_prefix()
    if prefix is None or not (prefix / 'lib').is_dir():
        print(f'error: no contrib install prefix with a lib/ under {daemon / "contrib"}',
              file=sys.stderr)
        print('       Set JAMI_CONTRIB, or build contrib first (see this file\'s docstring).',
              file=sys.stderr)
        return 1

    MACOS_LIB.mkdir(parents=True, exist_ok=True)
    print(f'contrib: {prefix}')
    print(f'target:  {MACOS_LIB}')

    libs = sorted((prefix / 'lib').glob('*.a'))
    if not libs:
        print(f'error: no static libraries under {prefix / "lib"}', file=sys.stderr)
        return 1

    for lib in libs:
        link(lib, MACOS_LIB / lib.name)

    # libjami is a product of the daemon build, not of contrib, so it lives
    # elsewhere. CMake names it libjami-core.a (same as the iOS build), but the
    # .def file, build-jamibridge.sh and the -ljami linker flag all expect the
    # shorter name, so it is linked in as libjami.a — the same rename
    # make_sim_links.py does for the libjami-core xcframework.
    jami_candidates = [
        *daemon.glob('build-macos*/libjami-core.a'),
        *daemon.glob('build-macos*/libjami.a'),
        *daemon.glob('build-macos*/src/libjami-core.a'),
        *daemon.glob('build-macos*/src/libjami.a'),
    ]
    jami = next((p for p in jami_candidates if p.exists()), None)
    if jami is not None:
        link(jami, MACOS_LIB / 'libjami.a')
        print(f'libjami: {jami} -> libjami.a')
    else:
        print('warning: libjami-core.a not found — build the daemon itself after contrib '
              '(see the README section "Then the daemon").', file=sys.stderr)

    print(f'Created {len(libs) + (1 if jami else 0)} symlinks')
    if not BRIDGE_LIB.exists():
        print(f'warning: {BRIDGE_LIB.name} missing — build it with '
              'shared/src/nativeInterop/cinterop/JamiBridge/build-jamibridge.sh --target=macos',
              file=sys.stderr)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
