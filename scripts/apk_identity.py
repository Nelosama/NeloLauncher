#!/usr/bin/env python3
"""Inspect APKs without installing them; conservatively check an Android 11 update."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def tool(name):
    override = os.environ.get(name.upper())
    if override:
        return override
    found = shutil.which(name)
    if found:
        return found
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if sdk:
        # Match the build-tools used by the workflow, without selecting an arbitrary version.
        candidate = Path(sdk) / 'build-tools' / '34.0.0' / name
        if candidate.is_file():
            return str(candidate)
    raise ValueError(f'{name} not found; set {name.upper()} or ANDROID_HOME')


def run(name, *args):
    result = subprocess.run([tool(name), *map(str, args)], capture_output=True, text=True)
    if result.returncode:
        raise ValueError(f'{name} rejected the APK: {result.stderr.strip()}')
    return result.stdout


def identity(path):
    path = Path(path)
    # Validate the signature for the target Android 11 API before trusting its certificate.
    certs = run('apksigner', 'verify', '--min-sdk-version', '30', '--print-certs', path)
    digests = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', certs, re.M)
    if len(digests) != 1:
        raise ValueError('Exactly one current signer is required by this conservative checker')
    badging = run('aapt', 'dump', 'badging', path)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, re.M)
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    if not package or not minimum:
        raise ValueError(f'Cannot read package/version/minSdk: {path}')
    return dict(file=path.as_posix(), sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                certificate_sha256=digests[0].lower(), application_id=package[1],
                version_code=int(package[2]), version_name=package[3], min_sdk=int(minimum[1]),
                debuggable='application-debuggable' in badging.splitlines())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    manifest = commands.add_parser('manifest')
    manifest.add_argument('--output-dir', required=True, type=Path)
    manifest.add_argument('--source-commit')
    manifest.add_argument('--build-commit')
    manifest.add_argument('--run-id')
    manifest.add_argument('apks', nargs='+')
    compare = commands.add_parser('compare')
    compare.add_argument('installed_reference')
    compare.add_argument('candidate')
    args = parser.parse_args()
    if args.command == 'manifest':
        records = [identity(path) for path in args.apks]
        args.output_dir.mkdir(parents=True, exist_ok=True)
        report = dict(source_commit=args.source_commit, build_commit=args.build_commit,
                      github_run_id=args.run_id, signature_verified_for_api=30, apks=records)
        (args.output_dir / 'APK-IDENTITY.json').write_text(json.dumps(report, indent=2) + '\n')
        (args.output_dir / 'SHA256SUMS').write_text(''.join(f"{r['sha256']}  {r['file']}\n" for r in records))
        print(json.dumps(report, indent=2))
        return 0
    original, candidate = identity(args.installed_reference), identity(args.candidate)
    errors = []
    if original['application_id'] != 'tv.nelo.launcher' or candidate['application_id'] != 'tv.nelo.launcher':
        errors.append('applicationId must remain tv.nelo.launcher')
    if original['certificate_sha256'] != candidate['certificate_sha256']:
        errors.append('Signing certificate differs; update rejected')
    if candidate['version_code'] <= original['version_code']:
        errors.append('Candidate versionCode must strictly increase')
    if candidate['min_sdk'] > 30:
        errors.append('Candidate does not support Android 11')
    print(json.dumps(dict(original=original, candidate=candidate,
                         static_update_compatible=not errors, errors=errors,
                         limitation='Static APK checks only; installation and data retention are not tested.'), indent=2))
    return 1 if errors else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(2)
