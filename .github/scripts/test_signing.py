"""Unit tests use synthetic fixtures; they do not validate a real APK signature."""
from pathlib import Path
import base64
import contextlib
import importlib.util
import io
import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('signing', Path(__file__).with_name('signing.py'))
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SigningTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=os.environ.get('PI_SCRATCH_DIR'))
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.workspace = self.root / 'workspace'
        self.workspace.mkdir()
        self.environment_file = self.root / 'github-env'
        (self.root / 'runner').mkdir()
        self.values = {
            'RUNNER_TEMP': str(self.root / 'runner'),
            'GITHUB_ENV': str(self.environment_file),
            'ANDROID_KEYSTORE_BASE64': base64.b64encode(b'test-only-keystore').decode(),
            'ANDROID_KEYSTORE_PASSWORD': 'test-only-password',
            'ANDROID_KEY_ALIAS': 'app',
            'ANDROID_KEY_PASSWORD': 'test-only-password',
            'ANDROID_HOME': str(self.root / 'sdk'),
        }
        self.addCleanup(patch.stopall)
        patch.dict(os.environ, self.values, clear=True).start()
        patch.object(signing, 'ROOT', self.workspace).start()
        self.output = io.StringIO()
        self.redirect = contextlib.redirect_stdout(self.output)
        self.redirect.__enter__()
        self.addCleanup(self.redirect.__exit__, None, None, None)

    def test_missing_secrets_rejected_before_any_file_is_written(self):
        del os.environ['ANDROID_KEY_PASSWORD']
        with self.assertRaisesRegex(ValueError, 'ANDROID_KEY_PASSWORD'):
            signing.prepare()
        self.assertFalse(signing.signing_directory().exists())

    def test_invalid_base64_rejected(self):
        os.environ['ANDROID_KEYSTORE_BASE64'] = 'invalid%%'
        with self.assertRaises(ValueError):
            signing.prepare()
        self.assertFalse(self.environment_file.exists())

    def test_oversized_secret_rejected(self):
        environment = dict(os.environ, ANDROID_KEYSTORE_BASE64='A' * 64_001)
        with patch.object(signing.os, 'environ', environment):
            with self.assertRaises(ValueError):
                signing.prepare()

    def test_prepare_exports_path_without_logging_secrets_and_cleanup_is_idempotent(self):
        signing.prepare()
        store = signing.signing_directory() / 'app-signing.p12'
        self.assertEqual(b'test-only-keystore', store.read_bytes())
        self.assertEqual(f'ANDROID_KEYSTORE_PATH={store}\n', self.environment_file.read_text())
        for name in ('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_PASSWORD'):
            self.assertNotIn(self.values[name], self.output.getvalue())
        signing.cleanup()
        signing.cleanup()
        self.assertFalse(store.exists())

    def package_fixture(self, signer=None, abi='arm64-v8a', package='local.daybook'):
        fingerprint = 'a' * 64
        config = self.workspace / '.github'
        config.mkdir()
        (config / 'signing-certificate.sha256').write_text(fingerprint)
        release = self.workspace / 'app/build/outputs/apk/release'
        release.mkdir(parents=True)
        with zipfile.ZipFile(release / 'app-release.apk', 'w') as archive:
            archive.writestr('AndroidManifest.xml', b'test-only-manifest')
            archive.writestr(f'lib/{abi}/libtest.so', b'test-only-native')
        metadata = {'applicationId': package, 'elements': [{'versionName': '1.0.0-dev', 'versionCode': 1}]}
        (release / 'output-metadata.json').write_text(json.dumps(metadata))
        (self.workspace / 'version.properties').write_text('versionName=1.0.0\nversionCode=1\n')
        (self.workspace / 'LICENSE').write_text('test-only-license')
        (self.workspace / 'THIRD_PARTY_NOTICES.md').write_text('test-only-notices')
        results = [
            subprocess.CompletedProcess([], 0, f'Signer #1 certificate SHA-256 digest: {signer or fingerprint}\n'),
            subprocess.CompletedProcess([], 0,
                f"package: name='{package}' versionCode='1' versionName='1.0.0-dev'\n"
                "sdkVersion:'26'\ntargetSdkVersion:'35'\n"),
        ]
        return patch.object(signing.subprocess, 'run', side_effect=results)

    def test_fixture_package_is_copied_with_checksum(self):
        with self.package_fixture():
            signing.verify()
        distribution = self.workspace / 'app/build/distribution'
        apk = distribution / 'app-1.0.0-dev-1-arm64-v8a.apk'
        self.assertTrue(apk.exists())
        info = json.loads((distribution / 'build-info.json').read_text())
        self.assertFalse(info['fullFeatureRelease'])
        self.assertIn(info['sha256'], (distribution / 'SHA256SUMS.txt').read_text())
        self.assertFalse(any(path.suffix == '.p12' for path in distribution.iterdir()))

    def test_wrong_signer_rejected(self):
        with self.package_fixture(signer='b' * 64):
            with self.assertRaisesRegex(ValueError, 'signer'):
                signing.verify()
        self.assertFalse((self.workspace / 'app/build/distribution').exists())

    def test_wrong_native_architecture_rejected(self):
        with self.package_fixture(abi='x86_64'):
            with self.assertRaisesRegex(ValueError, 'non-ARM64'):
                signing.verify()

    def test_wrong_application_identity_rejected(self):
        with self.package_fixture(package='local.other'):
            with self.assertRaisesRegex(ValueError, 'identity'):
                signing.verify()

    def test_signature_tool_failure_prevents_distribution(self):
        with self.package_fixture() as tool:
            tool.side_effect = subprocess.CalledProcessError(1, ['apksigner', 'verify'])
            with self.assertRaises(subprocess.CalledProcessError):
                signing.verify()
        self.assertFalse((self.workspace / 'app/build/distribution').exists())

    def test_version_mismatch_prevents_distribution(self):
        with self.package_fixture():
            (self.workspace / 'version.properties').write_text('versionName=1.0.0\nversionCode=2\n')
            with self.assertRaisesRegex(ValueError, 'version'):
                signing.verify()
        self.assertFalse((self.workspace / 'app/build/distribution').exists())


if __name__ == '__main__':
    unittest.main()
