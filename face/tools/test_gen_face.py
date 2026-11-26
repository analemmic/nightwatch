#!/usr/bin/env python3
"""Smoke test for the generated face. Catches what otherwise only fails on the watch (blank face, 'fail to parseInt').

Run:  python3 face/tools/test_gen_face.py      (regenerates nothing; checks the generator's output in memory)
"""
import re
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_face as g  # noqa: E402

RES = Path(__file__).resolve().parent.parent / "src" / "main" / "res"
INT_ATTRS = ("x", "y", "width", "height")
INT_ELEMENTS = {"Group", "PartDraw", "PartText", "PartImage", "ComplicationSlot", "BoundingBox"}


class GeneratedFace(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.xml = g.watchface_xml()
        cls.root = ET.fromstring(cls.xml)

    def test_at_most_eight_slots_with_unique_ids(self):
        ids = [s.get("slotId") for s in self.root.iter("ComplicationSlot")]
        self.assertLessEqual(len(ids), 8)
        self.assertEqual(len(ids), len(set(ids)))

    def test_slot_providers_match_the_companion(self):
        manifest = (Path(g.ASTRO) / "AndroidManifest.xml").read_text()
        for _, cls, _ in g.SLOTS:
            self.assertIn(f'android:name=".{cls}"', manifest, f"{cls} missing from the companion manifest")

    def test_every_reference_used_is_defined_once(self):
        defined = [r.get("name") for r in self.root.iter("Reference")]
        self.assertEqual(len(defined), len(set(defined)), "duplicate Reference names")
        used = set(re.findall(r"\[REFERENCE\.(\w+)\]", self.xml))
        self.assertFalse(used - set(defined), f"undefined references: {used - set(defined)}")

    def test_geometry_attributes_are_integers(self):
        for el in self.root.iter():
            if el.tag in INT_ELEMENTS:
                for k in INT_ATTRS:
                    v = el.get(k)
                    if v is not None:
                        self.assertRegex(v, r"^-?\d+$", f"{el.tag} {el.get('name')} {k}={v}")

    def test_every_image_exists(self):
        for img in self.root.iter("Image"):
            res = img.get("resource")
            self.assertTrue((RES / "drawable-nodpi" / f"{res}.png").exists(), f"missing sprite {res}.png")

    def test_centre_tap_launches_an_alias_per_palette_option(self):
        manifest = (Path(g.ASTRO) / "AndroidManifest.xml").read_text()
        targets = [l.get("target") for l in self.root.iter("Launch")]
        self.assertEqual(len(targets), len(g.PALETTE_MODES))
        for _, _, alias in g.PALETTE_MODES:
            self.assertIn(f"{g.ASTRO_PKG}/{g.ASTRO_PKG}.{alias}", targets)
            self.assertIn(f'android:name=".{alias}"', manifest, f"{alias} alias missing from the companion manifest")

    def test_palette_options_match_the_configuration(self):
        decl = [o.get("id") for o in self.root.find("UserConfigurations").iter("ListOption")]
        self.assertEqual(decl, [i for i, _, _ in g.PALETTE_MODES])
        self.assertIn(g.PALETTE_CFG, self.xml)

    def test_every_palette_colour_is_opaque_hex(self):
        for pal in (g.NIGHT, g.NIGHT_AMB, g.DAY, g.DAY_AMB):
            for k, v in pal.items():
                for c in v if isinstance(v, list) else [v]:
                    if k != "name":
                        self.assertRegex(c, r"^#[0-9a-f]{6}$", f"{pal['name']}.{k}")


if __name__ == "__main__":
    unittest.main(verbosity=1)
