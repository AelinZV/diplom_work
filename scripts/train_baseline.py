"""Обучение TF-IDF + Logistic Regression; тест без подбора по test.jsonl."""
from __future__ import annotations

import argparse
import json
import time

from ml_common import OUT, class_names, evaluate, load_split


def main():
    parser = argparse.ArgumentParser(description="Baseline классификации команд GreenPlanner")
    parser.add_argument("--max-iter", type=int, default=2000)
    args = parser.parse_args()
    if args.max_iter < 1:
        parser.error("--max-iter должен быть положительным")

    try:
        import joblib
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.linear_model import LogisticRegression
        from sklearn.pipeline import Pipeline
    except ImportError as e:
        raise SystemExit("Установите зависимости: python -m pip install -r requirements-baseline.txt") from e

    train, valid, test = (load_split(split) for split in ("train", "validation", "test"))
    labels = class_names(train)
    for split, rows in (("validation", valid), ("test", test)):
        if set(class_names(rows)) != set(labels):
            raise RuntimeError(f"В {split} обнаружено другое множество классов")

    model = Pipeline([
        ("tfidf", TfidfVectorizer(analyzer="char", ngram_range=(2, 5), min_df=2, sublinear_tf=True)),
        ("classifier", LogisticRegression(max_iter=args.max_iter, class_weight="balanced", random_state=42)),
    ])
    start = time.perf_counter()
    print("Обучение TF-IDF + Logistic Regression...")
    model.fit([r["text"] for r in train], [r["intent"] for r in train])
    elapsed = time.perf_counter() - start

    folder = OUT / "baseline"
    folder.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, folder / "model.joblib")
    (folder / "classes.json").write_text(json.dumps(labels, ensure_ascii=False, indent=2), encoding="utf-8")
    for name, data in (("validation", valid), ("test", test)):
        truth = [r["intent"] for r in data]
        pred = model.predict([r["text"] for r in data]).tolist()
        scores = evaluate(truth, pred, labels, folder / name, rows=data)
        print(f"{name}: accuracy={scores['accuracy']:.4f}, macro F1={scores['macro_f1']:.4f}")

    (folder / "training_info.json").write_text(
        json.dumps({"algorithm": "TF-IDF character 2-5 + LogisticRegression", "train_samples": len(train),
                    "training_seconds": round(elapsed, 3), "train_source": "synthetic"},
                   ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"Модель: {folder / 'model.joblib'}")
    print("Метрики и таблицы ошибок сохранены в outputs/baseline")


if __name__ == "__main__":
    main()
