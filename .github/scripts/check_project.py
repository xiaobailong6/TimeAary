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
    print('PASS: project structure, XML, catalog references, icon dimensions and pinned actions/certificate')


if __name__ == '__main__':
    check()
