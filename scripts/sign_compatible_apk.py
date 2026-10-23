#!/usr/bin/env python3
"""Sign offline with a recovered key; publish output only after static update checks."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile

from apk_identity import identity, tool


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, default=Path('dist/nelo-tv-0.1.0-debug.apk'))
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--keystore', required=True, type=Path)
    args = parser.parse_args()
    for variable in ('NELO_KEY_ALIAS', 'NELO_STORE_PASSWORD', 'NELO_KEY_PASSWORD'):
        if not os.environ.get(variable):
            raise ValueError(f'Missing environment variable: {variable}')
    if args.output.exists():
        raise ValueError('Output already exists; refusing to replace it')
    reference = identity(args.reference)
    candidate = identity(args.input)
    if reference['application_id'] != 'tv.nelo.launcher' or candidate['application_id'] != 'tv.nelo.launcher':
        raise ValueError('Package must remain tv.nelo.launcher')
    if candidate['version_code'] <= reference['version_code'] or candidate['min_sdk'] > 30:
        raise ValueError('Require increasing versionCode and Android 11 compatibility')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.nelo-sign-', dir=args.output.parent) as directory:
        signed = Path(directory) / 'signed.apk'
        # apksigner replaces the input signatures; it does not modify the private key or certificate.
        command = [tool('apksigner'), 'sign', '--ks', str(args.keystore),
                   '--ks-key-alias', os.environ['NELO_KEY_ALIAS'],
                   '--ks-pass', 'env:NELO_STORE_PASSWORD', '--key-pass', 'env:NELO_KEY_PASSWORD',
                   '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true',
                   '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
                   '--out', str(signed), str(args.input)]
        result = subprocess.run(command, capture_output=True, text=True)
        if result.returncode:
            # Avoid reflecting signing-tool diagnostics into CI logs containing secret context.
            raise ValueError('Signing failed; check keystore credentials privately')
        actual = identity(signed)
        if actual['certificate_sha256'] != reference['certificate_sha256']:
            raise ValueError('Recovered key does not match the reference; no APK published')
        if any(actual[key] != candidate[key] for key in ('application_id', 'version_code', 'version_name', 'min_sdk')):
            raise ValueError('APK metadata changed during signing; no APK published')
        # Exclusive creation also avoids replacing an existing APK if another process created it.
        os.link(signed, args.output)
    print('Signed APK verified against reference. Installation and data retention remain untested.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
