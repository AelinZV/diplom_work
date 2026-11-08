"""Валидация JSONL, спанов, уникальности, групп шаблонов и holdout."""
import json
import re
from collections import Counter
from pathlib import Path

from generate_dataset import ANNOTATABLE, TEMPLATES

ROOT = Path(__file__).resolve().parents[1]


def read(name):
    return [json.loads(line) for line in (ROOT / "data" / f"{name}.jsonl").read_text(encoding="utf-8").splitlines() if line]


def validate():
    seen, ids, template_splits = set(), set(), {}
    for split in ("train", "validation", "test"):
        rows = read(split)
        counts, styles = Counter(), Counter()
        for row in rows:
            text, intent = row["text"], row["intent"]
            assert intent in TEMPLATES, row
            assert row["source"] == "synthetic"
            assert row["id"].startswith(split + "-")
            assert row["id"] not in ids
            ids.add(row["id"])
            key = re.sub(r"\s+", " ", text.casefold().strip())
            assert key not in seen, f"Повтор фразы: {text}"
            seen.add(key)
            assert len(text) >= 4
            assert not re.search(r"\bна\s+(?:в|во|через|к)\s+", text, flags=re.IGNORECASE), text
            counts[intent] += 1
            styles[row["style"]] += 1
            template_splits.setdefault(row["template_id"], set()).add(split)
            spans = row["slots"]
            assert spans == sorted(spans, key=lambda s: s["start"]), row
            previous_end = 0
            for s in spans:
                assert s["label"] in ANNOTATABLE, row
                assert 0 <= previous_end <= s["start"] < s["end"] <= len(text), row
                assert text[s["start"]:s["end"]] == s["text"], row
                previous_end = s["end"]
        assert set(counts) == set(TEMPLATES), counts
        assert len(set(counts.values())) == 1, counts
        print(f"{split}: {len(rows)} | {min(counts.values())} на класс | стили: {dict(styles)}")
    assert all(len(x) == 1 for x in template_splits.values())

    # Проверяем не только ID шаблона, но и буквальные шаблоны между выборками.
    for label, groups in TEMPLATES.items():
        patterns = {split: {pattern for style, pattern in items} for split, items in groups.items()}
        for a, b in (("train", "validation"), ("train", "test"), ("validation", "test")):
            assert not patterns[a].intersection(patterns[b]), (label, a, b)

    holdout = read("manual_holdout")
    hc = Counter(x["intent"] for x in holdout)
    assert set(hc) == set(TEMPLATES) and len(set(hc.values())) == 1, hc
    for row in holdout:
        assert row["source"] == "manual_authored_holdout"
        assert row["id"] not in ids
        assert not row["slots"], "В holdout слоты намеренно не размечены"
        key = re.sub(r"\s+", " ", row["text"].casefold().strip())
        assert key not in seen, row
        seen.add(key)
    print(f"manual_holdout: {len(holdout)} | {min(hc.values())} на класс")
    print("OK: уникальность, слоты, шаблоны и независимая ручная выборка проверены")


if __name__ == "__main__":
    validate()
