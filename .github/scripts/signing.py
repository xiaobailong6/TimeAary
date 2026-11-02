"""Verify fixed signing identity and stage only the signed APK for distribution."""
from pathlib import Path
import base64
import binascii
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def signing_directory():
    return Path(os.environ['RUNNER_TEMP']).resolve() / 'app-signing'


def prepare():
    names = ('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD',
             'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')
    missing = [name for name in names if not os.environ.get(name, '').strip()]
    if missing:
        raise ValueError('Missing GitHub Secrets: ' + ', '.join(missing))
    encoded = ''.join(os.environ[names[0]].split())
    if len(encoded) > 64_000:
        raise ValueError('Keystore exceeds allowed size')
    try:
        decoded = base64.b64decode(encoded, validate=True)
    except binascii.Error as error:
        raise ValueError('Keystore secret must contain valid Base64 text') from error
    if not decoded:
        raise ValueError('Keystore is empty')
    folder = signing_directory()
    folder.mkdir(mode=0o700, exist_ok=False)
    store = folder / 'app-signing.p12'
    store.write_bytes(decoded)
    store.chmod(0o600)
    with open(os.environ['GITHUB_ENV'], 'a', encoding='utf-8') as output:
        output.write(f'ANDROID_KEYSTORE_PATH={store}\n')
    print('Signing material prepared outside workspace; secrets are not logged.')


def verify():
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '35.0.0'
    packages = list((ROOT / 'app/build/outputs/apk/release').glob('*.apk'))
    if len(packages) != 1:
        raise ValueError('Expected exactly one release APK')
    apk = packages[0]
    result = subprocess.run([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)],
                            check=True, capture_output=True, text=True)
    expected = (ROOT / '.github/signing-certificate.sha256').read_text().strip().lower()
    actual = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', result.stdout)
    if not re.fullmatch(r'[0-9a-f]{64}', expected) or [value.lower() for value in actual] != [expected]:
        raise ValueError('APK signer does not match pinned certificate')
    badging = subprocess.run([str(tools / 'aapt'), 'dump', 'badging', str(apk)],
                             check=True, capture_output=True, text=True).stdout
    metadata = json.loads((apk.parent / 'output-metadata.json').read_text())
    if metadata['applicationId'] != 'local.daybook' or len(metadata['elements']) != 1:
        raise ValueError('Unexpected application identity or split output')
    element = metadata['elements'][0]
    version = element['versionName']
    code = element['versionCode']
    version_properties = dict(line.strip().split('=', 1) for line in
                              (ROOT / 'version.properties').read_text().splitlines() if '=' in line)
    if version != version_properties['versionName'] + '-dev' or code != int(version_properties['versionCode']):
        raise ValueError('APK version differs from version source')
    expected_package = f"package: name='local.daybook' versionCode='{code}' versionName='{version}'"
    if not badging.startswith(expected_package):
        raise ValueError('Manifest package or version does not match metadata')
    if "sdkVersion:'26'" not in badging or "targetSdkVersion:'35'" not in badging:
        raise ValueError('Unexpected Android SDK constraints')
    with zipfile.ZipFile(apk) as archive:
        native = [name for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')]
    if any(not name.startswith('lib/arm64-v8a/') for name in native):
        raise ValueError('APK contains a non-ARM64 native library')
    if not re.fullmatch(r'\d+\.\d+\.\d+-dev', version):
        raise ValueError('Unexpected development version syntax')
    folder = ROOT / 'app/build/distribution'
    folder.mkdir(parents=True, exist_ok=False)
    destination = folder / f'app-{version}-{code}-arm64-v8a.apk'
    shutil.copyfile(apk, destination)
    checksum = hashlib.sha256(destination.read_bytes()).hexdigest()
    print(f'Verified signed development APK: {destination.name}')
    print(f'Application: local.daybook; version: {version} ({code})')
    print(f'Certificate SHA-256: {expected}')
    print(f'APK SHA-256: {checksum}')


def cleanup():
    folder = signing_directory()
    if folder.exists():
        shutil.rmtree(folder)
    print('Temporary signing material removed.')


if __name__ == '__main__':
    commands = {'prepare': prepare, 'verify': verify, 'cleanup': cleanup}
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        raise SystemExit('Usage: signing.py prepare|verify|cleanup')
    try:
        commands[sys.argv[1]]()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        # Avoid subprocess output or environment dumps that could expose signing secrets.
        print(f'Signing step failed: {error}', file=sys.stderr)
        raise SystemExit(1)
