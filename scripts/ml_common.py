"""Общие функции загрузки выборок и метрик (этап 4.2)."""
from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
OUT = ROOT / "outputs"


def load_split(split: str) -> list[dict[str, Any]]:
    if split not in {"train", "validation", "test"}:
        raise ValueError("Неизвестная выборка: " + split)
    path = DATA / f"{split}.jsonl"
    if not path.is_file():
        raise FileNotFoundError(f"Нет файла {path}; запустите scripts/generate_dataset.py")
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            if not line.strip():
                continue
            row = json.loads(line)
            if not isinstance(row.get("text"), str) or not isinstance(row.get("intent"), str):
                raise ValueError(f"Некорректная строка {number}: {path}")
            rows.append(row)
    if not rows:
        raise ValueError(f"Пустой набор: {path}")
    return rows


def class_names(rows: list[dict[str, Any]]) -> list[str]:
    return sorted({row["intent"] for row in rows})


def evaluate(y_true: list[str], y_pred: list[str], labels: list[str], out: Path,
             rows: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score

    out.mkdir(parents=True, exist_ok=True)
    if not set(y_true).issubset(labels) or not set(y_pred).issubset(labels):
        raise ValueError("Обнаружены классы вне известных меток")

    report = classification_report(
        y_true, y_pred, labels=labels, output_dict=True, zero_division=0
    )
    metrics = {
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "macro_f1": float(f1_score(y_true, y_pred, average="macro", labels=labels, zero_division=0)),
        "weighted_f1": float(f1_score(y_true, y_pred, average="weighted", labels=labels, zero_division=0)),
        "per_class": report,
        "n_samples": len(y_true),
    }
    (out / "metrics.json").write_text(json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8")
    (out / "report.txt").write_text(
        classification_report(y_true, y_pred, labels=labels, zero_division=0), encoding="utf-8"
    )

    matrix = confusion_matrix(y_true, y_pred, labels=labels)
    with (out / "confusion_matrix.csv").open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["actual / predicted", *labels])
        for label, row in zip(labels, matrix):
            writer.writerow([label, *row.tolist()])

    if rows is not None:
        with (out / "predictions.csv").open("w", encoding="utf-8-sig", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["text", "actual", "predicted", "correct"])
            for row, true, predicted in zip(rows, y_true, y_pred):
                writer.writerow([row["text"], true, predicted, int(true == predicted)])
    return metrics
