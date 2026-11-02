"""Build-independent checks, runnable locally without JDK or Android SDK."""
from pathlib import Path
import re
import struct
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def check():
    catalog = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text(encoding='utf-8'))
    for group in ('plugins', 'libraries'):
        for item in catalog[group].values():
            version = item.get('version')
            if isinstance(version, dict):
                assert version['ref'] in catalog['versions'], item
            elif isinstance(version, str):
                assert not any(marker in version for marker in ('+', 'SNAPSHOT', '[', ']')), item
    assert (ROOT / 'app/src/AndroidManifest.xml').is_file()
    for path in (ROOT / 'app').rglob('*'):
        if 'build' in path.relative_to(ROOT).parts:
            continue
        assert 'main' not in path.relative_to(ROOT).parts, path
        assert path.suffix not in ('.p12', '.pfx', '.jks', '.keystore'), path
        if path.suffix == '.xml':
            ET.parse(path)
    for path in (ROOT / 'app/src/code').rglob('*.kt'):
        source = path.read_text(encoding='utf-8')
        assert 'github.com/' not in source and 'https://' not in source, path
        assert re.search(r'^package local\.daybook(?:\.[a-z]+)?$', source, re.MULTILINE), path
    for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        data = (ROOT / f'app/src/resources/mipmap-{density}/ic_launcher.png').read_bytes()
        assert struct.unpack('>II', data[16:24]) == (size, size), density
    fingerprint = (ROOT / '.github/signing-certificate.sha256').read_text().strip()
    assert re.fullmatch(r'[0-9a-f]{64}', fingerprint)
    for path in list((ROOT / '.github/workflows').glob('*.yml')) + [ROOT / '.github/actions/android/action.yml']:
        for reference in re.findall(r'^\s*- uses: (\S+)', path.read_text(encoding='utf-8'), re.MULTILINE):
            if reference.startswith('./'):
                assert (ROOT / reference / 'action.yml').is_file(), reference
            else:
                assert re.fullmatch(r'[\w-]+/[\w-]+@[a-f0-9]{40}', reference), reference
    gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
    assert 'applicationIdSuffix = ".debug"' in gradle
    assert 'check(!requireSigning || hasSigning)' in gradle
    package_workflow = (ROOT / '.github/workflows/package.yml').read_text(encoding='utf-8')
    assert '  push:' in package_workflow and '  workflow_dispatch:' in package_workflow
    assert 'pull_request' not in package_workflow
    assert 'environment: signing' in package_workflow
    assert "github.event.repository.default_branch" in package_workflow
    assert '-PrequireSigning=true' in package_workflow
    ci = (ROOT / '.github/workflows/ci.yml').read_text(encoding='utf-8')
    assert 'secrets.' not in ci
    assert 'upload-artifact@' not in ci
    assert ':app:testReleaseUnitTest :app:lintRelease' in ci
    assert ':app:testReleaseUnitTest :app:lintRelease' in package_workflow
    workflows = list((ROOT / '.github/workflows').glob('*.yml'))
    uploads = []
    for path in workflows:
        text = path.read_text(encoding='utf-8')
        assert not re.search(r':app:(?:assembleDebug|testDebugUnitTest|lintDebug)\b', text)
        if path.name != 'package.yml':
            assert ':app:assembleRelease' not in text
        uploads.extend(re.findall(r'uses: actions/upload-artifact@', text))
    assert len(uploads) == 1, 'Only one signed APK artifact upload is allowed'
    assert 'path: app/build/distribution/*.apk' in package_workflow
    assert 'signed-checks' not in package_workflow
    print('PASS: project structure, XML, catalog references, icon dimensions and pinned actions/certificate')


if __name__ == '__main__':
    check()
