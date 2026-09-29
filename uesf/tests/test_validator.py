"""
UESF validator test suite (stdlib unittest — no external dependencies).

Run from the framework root:
    python3 -m unittest discover -s tests -v
"""

import os
import sys
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from tools.validate_framework import (  # noqa: E402
    load_skill,
    parse_frontmatter,
    validate_framework,
    validate_skills,
)

FIXTURES = os.path.join(ROOT, "tests", "fixtures")


def skill(rel):
    return load_skill(os.path.join(FIXTURES, rel))


def errors_of(records):
    errors, _warnings = validate_skills(records)
    return errors


class TestFrontmatterParser(unittest.TestCase):
    def test_scalars_lists_maps_and_block_scalar(self):
        text = """---
id: uesf-te-test-one
name: Parser Test
version: 1.0.0
category: te
kind: engineering
dependencies: []
scores:
  complexity: 2
  maintainability_score: 4
triggers:
  - condition: "A condition"
  - example_prompt: "A prompt"
inputs:
  - "one"
description: |
  Line one.
  Line two.
---
"""
        fm = parse_frontmatter(text)
        self.assertEqual(fm["id"], "uesf-te-test-one")
        self.assertEqual(fm["dependencies"], [])
        self.assertEqual(fm["scores"]["complexity"], 2)
        self.assertEqual(fm["scores"]["maintainability_score"], 4)
        self.assertEqual(fm["triggers"], [{"condition": "A condition"},
                                          {"example_prompt": "A prompt"}])
        self.assertEqual(fm["inputs"], ["one"])
        self.assertIn("Line one.", fm["description"])
        self.assertIn("Line two.", fm["description"])

    def test_parse_error_is_reported_not_raised(self):
        rec = skill("valid/ok/SKILL.md")
        self.assertIn("frontmatter", rec)


class TestWholeFramework(unittest.TestCase):
    def test_framework_passes(self):
        report = validate_framework()
        self.assertEqual(report["errors"], [])
        self.assertGreaterEqual(report["skills_scanned"], 39)


class TestValidationRules(unittest.TestCase):
    def test_valid_fixture_passes(self):
        self.assertEqual(errors_of([skill("valid/ok/SKILL.md")]), [])

    def test_missing_required_field_fails(self):
        errs = errors_of([skill("invalid/missing-field/SKILL.md")])
        self.assertTrue(any("missing required field" in e and "outputs" in e for e in errs))

    def test_bad_version_fails(self):
        errs = errors_of([skill("invalid/bad-version/SKILL.md")])
        self.assertTrue(any("invalid version" in e for e in errs))

    def test_unknown_category_fails(self):
        errs = errors_of([skill("invalid/unknown-category/SKILL.md")])
        self.assertTrue(any("unknown category" in e for e in errs))

    def test_unresolved_dependency_fails(self):
        errs = errors_of([skill("invalid/missing-dep/SKILL.md")])
        self.assertTrue(any("unresolved dependency" in e for e in errs))

    def test_missing_body_section_fails(self):
        errs = errors_of([skill("invalid/missing-section/SKILL.md")])
        self.assertTrue(any("missing body section" in e and "Anti-patterns" in e for e in errs))

    def test_dependency_cycle_detected(self):
        errs = errors_of([skill("invalid/cycle/a/SKILL.md"),
                          skill("invalid/cycle/b/SKILL.md")])
        self.assertTrue(any("dependency cycle" in e for e in errs),
                        "expected a cycle error, got: %r" % errs)

    def test_duplicate_id_fails(self):
        errs = errors_of([skill("valid/ok/SKILL.md"), skill("valid/ok/SKILL.md")])
        self.assertTrue(any("duplicate skill id" in e for e in errs))

    def test_self_dependency_fails_without_cycle_noise(self):
        rec = skill("valid/ok/SKILL.md")
        rec["frontmatter"]["dependencies"] = [rec["frontmatter"]["id"]]
        errs = errors_of([rec])
        self.assertTrue(any("self-dependency" in e for e in errs))
        self.assertFalse(any("dependency cycle" in e for e in errs),
                         "self-dependency must not double-report as a cycle")

    def test_oversized_description_fails(self):
        rec = skill("valid/ok/SKILL.md")
        rec["frontmatter"]["description"] = "x" * 2000
        errs = errors_of([rec])
        self.assertTrue(any("1024" in e for e in errs))

    def test_kind_example_requires_category_ex(self):
        with open(os.path.join(FIXTURES, "valid/ok/SKILL.md"), encoding="utf-8") as fh:
            text = fh.read()
        text = text.replace("kind: engineering", "kind: example")
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            p = os.path.join(tmp, "SKILL.md")
            with open(p, "w", encoding="utf-8") as fh:
                fh.write(text)
            errs = errors_of([load_skill(p)])
        self.assertTrue(any("kind=example requires category=ex" in e for e in errs))


if __name__ == "__main__":
    unittest.main()
