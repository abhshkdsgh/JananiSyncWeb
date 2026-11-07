"""Policy release-integrity tests; all mutation tests use temporary workspaces."""

from contextlib import redirect_stdout
import hashlib
import io
import json
from pathlib import Path
import re
import tempfile
import unittest

import generate_privacy_policy as generator


class PolicyReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "docs/privacy-policy.md"
        self.source.parent.mkdir(parents=True)
        published = (generator.ROOT / "docs/privacy-policy.md").read_bytes()
        published_version = re.search(rb"\*\*Version:\*\* ([0-9]+\.[0-9]+)", published)[1].decode()
        fixture = re.sub(rb"\*\*Version:\*\* [0-9]+\.[0-9]+", b"**Version:** 1.0", published)
        fixture = re.sub(rb"\*\*Effective date:\*\* [^\r\n]+", b"**Effective date:** 30 October 2026  ", fixture)
        self.source.write_bytes(fixture)
        self.rules = self.root / "firestore.rules"
        rules = (generator.ROOT / "firestore.rules").read_text()
        rules = rules.replace(hashlib.sha256(published).hexdigest(), hashlib.sha256(fixture).hexdigest())
        self.rules.write_text(rules.replace(f"== '{published_version}'", "== '1.0'"))
        self.run_generator()

    def run_generator(self, *args):
        with redirect_stdout(io.StringIO()):
            generator.main(list(args), root=self.root)

    def test_exact_source_archive_and_runtime_content_have_one_digest(self):
        self.run_generator("--check")
        source = self.source.read_bytes()
        archive = (self.root / "docs/privacy/1.0.md").read_bytes()
        manifest = json.loads((self.root / "docs/privacy/1.0-presentation-2-manifest.json").read_text())
        kotlin = (self.root / generator.KOTLIN_PATH).read_text()
        content = re.search(r'const val CONTENT = """(.*)"""', kotlin, re.DOTALL).group(1)
        self.assertEqual(source, archive)
        self.assertEqual(source, content.replace("${'$'}", "$").encode("utf-8"))
        self.assertEqual(hashlib.sha256(source).hexdigest(), manifest["policySha256"])
        self.assertIn(f'const val SHA256 = "{manifest["policySha256"]}"', kotlin)

    def test_effective_date_version_and_decision_wording_are_archived(self):
        manifest = json.loads((self.root / "docs/privacy/1.0-presentation-2-manifest.json").read_text())
        self.assertEqual("2026-10-30", manifest["effectiveDate"])
        self.assertEqual("1.0", manifest["policyVersion"])
        self.assertEqual(2, manifest["presentationVersion"])
        self.assertIn("It is not consent on behalf of any patient.", manifest["acceptanceStatement"])
        self.assertEqual("Accept and continue", manifest["acceptButton"])
        self.assertEqual("Deny and stop", manifest["denyButton"])

    def test_check_rejects_missing_and_stale_runtime_archive_and_manifest(self):
        for path in generator.artifacts(self.root):
            original = path.read_bytes()
            with self.subTest(path=path.name, mutation="stale"):
                path.write_bytes(original + b"modified")
                with self.assertRaisesRegex(SystemExit, "stale generated policy artifact"):
                    self.run_generator("--check")
                self.assertEqual(original + b"modified", path.read_bytes())
                path.write_bytes(original)
            with self.subTest(path=path.name, mutation="missing"):
                path.unlink()
                with self.assertRaisesRegex(SystemExit, "stale generated policy artifact"):
                    self.run_generator("--check")
                self.assertFalse(path.exists())
                path.write_bytes(original)

    def test_changed_policy_cannot_overwrite_published_version_or_runtime(self):
        archive = self.root / "docs/privacy/1.0.md"
        original_archive = archive.read_bytes()
        runtime = self.root / generator.KOTLIN_PATH
        original_runtime = runtime.read_bytes()
        original_hash = hashlib.sha256(self.source.read_bytes()).hexdigest()
        self.source.write_bytes(self.source.read_bytes() + b"\nAdditional policy text.\n")
        changed_hash = hashlib.sha256(self.source.read_bytes()).hexdigest()
        self.rules.write_text(self.rules.read_text().replace(original_hash, changed_hash))
        with self.assertRaisesRegex(SystemExit, "Refusing to overwrite version snapshot"):
            self.run_generator()
        self.assertEqual(original_archive, archive.read_bytes())
        self.assertEqual(original_runtime, runtime.read_bytes())

    def test_new_version_retains_old_archive_and_uses_new_hash(self):
        old_archive = (self.root / "docs/privacy/1.0.md").read_bytes()
        old_hash = hashlib.sha256(old_archive).hexdigest()
        self.source.write_text(self.source.read_text().replace("**Version:** 1.0", "**Version:** 1.1"))
        new_hash = hashlib.sha256(self.source.read_bytes()).hexdigest()
        self.rules.write_text(self.rules.read_text().replace(old_hash, new_hash).replace("== '1.0'", "== '1.1'"))
        self.run_generator()
        self.run_generator("--check")
        self.assertEqual(old_archive, (self.root / "docs/privacy/1.0.md").read_bytes())
        self.assertEqual(self.source.read_bytes(), (self.root / "docs/privacy/1.1.md").read_bytes())

    def test_both_rule_functions_and_hash_helper_must_pin_current_release(self):
        original = self.rules.read_text()
        manifest = json.loads((self.root / "docs/privacy/1.0-presentation-2-manifest.json").read_text())
        digest = manifest["policySha256"]
        mutations = [
            (f"return '{digest}'", "return '" + "0" * 64 + "'", "currentPrivacyPolicyHash"),
            ("return version == '1.0'", "return version == '0.9'", "currentPrivacyPolicy\\(\\)"),
            ("hash == currentPrivacyPolicyHash()", "hash == '" + digest + "'", "currentPrivacyPolicy\\(\\)"),
            ("receipt.policyVersion == '1.0'", "receipt.policyVersion == '0.9'", "acceptedCurrentPrivacyPolicy"),
            (f"receipt.policySha256 == '{digest}'", "receipt.policySha256 == '" + "0" * 64 + "'", "acceptedCurrentPrivacyPolicy"),
        ]
        for target, replacement, error in mutations:
            with self.subTest(pin=target):
                self.assertIn(target, original)
                self.rules.write_text(original.replace(target, replacement))
                with self.assertRaisesRegex(SystemExit, error):
                    self.run_generator("--check")
        self.rules.write_text(original)

    def test_noncanonical_source_bytes_and_incomplete_fields_are_rejected(self):
        original = self.source.read_bytes()
        for changed, message in [
            (original.replace(b"\n", b"\r\n"), "UTF-8 with LF"),
            (original.rstrip(b"\n"), "final newline"),
            (original + b"\n[UNRESOLVED_FIELD]\n", "incomplete operator fields"),
        ]:
            with self.subTest(message=message):
                self.source.write_bytes(changed)
                with self.assertRaisesRegex(SystemExit, message):
                    self.run_generator("--check")


if __name__ == "__main__":
    unittest.main()
