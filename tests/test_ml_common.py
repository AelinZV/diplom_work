import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from ml_common import load_split, class_names


class MLDataTests(unittest.TestCase):
    def test_splits_class_count(self):
        for split, size in (("train", 6000), ("validation", 1000), ("test", 1000)):
            rows = load_split(split)
            self.assertEqual(len(rows), size)
            self.assertEqual(len(class_names(rows)), 10)

    def test_class_set_same_for_every_split(self):
        classes = class_names(load_split("train"))
        self.assertEqual(classes, class_names(load_split("validation")))
        self.assertEqual(classes, class_names(load_split("test")))


if __name__ == "__main__":
    unittest.main()
