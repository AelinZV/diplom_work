import json
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPT))
from generate_dataset import TEMPLATES, fill_template, make_dataset
from build_manual_holdout import make_records
import random


class DatasetTests(unittest.TestCase):
    def test_balanced(self):
        d = make_dataset(20, 10, 10, 17)
        for split, per_intent in (("train", 20), ("validation", 10), ("test", 10)):
            counts = {i: sum(r["intent"] == i for r in d[split]) for i in TEMPLATES}
            self.assertEqual(set(counts.values()), {per_intent})

    def test_no_cross_split_text_overlap(self):
        d = make_dataset(20, 10, 10, 17)
        t = [{r["text"].casefold() for r in d[s]} for s in ("train", "validation", "test")]
        self.assertFalse((t[0] & t[1]) | (t[0] & t[2]) | (t[1] & t[2]))

    def test_offsets(self):
        d = make_dataset(20, 10, 10, 17)
        for rows in d.values():
            for row in rows:
                for s in row["slots"]:
                    self.assertEqual(row["text"][s["start"]:s["end"]], s["text"])

    def test_reproducibility(self):
        self.assertEqual(make_dataset(20, 10, 10, 15), make_dataset(20, 10, 10, 15))

    def test_manual_holdout(self):
        rows = make_records()
        self.assertEqual(len(rows), 120)
        self.assertEqual({r["intent"] for r in rows}, set(TEMPLATES))

    def test_morphology_sensitive_slots(self):
        txt, spans = fill_template("перенеси {EVENT} на {DATE_ON}", random.Random(2))
        self.assertIn(" на ", txt)
        self.assertEqual([s["label"] for s in spans], ["TASK", "DATE"])


if __name__ == "__main__":
    unittest.main()
