"""Проверка намерения по произвольной фразе пользователя."""
from __future__ import annotations

import argparse
from pathlib import Path

from ml_common import OUT


def baseline(text: str, top_k: int):
    try:
        import joblib
    except ImportError as e:
        raise SystemExit("Установите requirements-baseline.txt") from e
    file = OUT / "baseline" / "model.joblib"
    if not file.exists():
        raise SystemExit("Сначала запустите: python scripts/train_baseline.py")
    model = joblib.load(file)  # Загружать только свой локальный файл, не скачанные модели .joblib
    probs = model.predict_proba([text])[0]
    classes = model.classes_
    return sorted(zip(classes, map(float, probs)), key=lambda p: p[1], reverse=True)[:top_k]


def rubert(text: str, top_k: int):
    try:
        import torch
        from transformers import AutoTokenizer, AutoModelForSequenceClassification
    except ImportError as e:
        raise SystemExit("Установите requirements-rubert.txt") from e
    folder = OUT / "rubert" / "model"
    if not (folder / "config.json").exists():
        raise SystemExit("Сначала выполните python scripts/train_rubert.py")
    tokenizer = AutoTokenizer.from_pretrained(folder)
    model = AutoModelForSequenceClassification.from_pretrained(folder)
    model.eval()
    batch = tokenizer(text, return_tensors="pt", truncation=True, max_length=128)
    with torch.inference_mode():
        logits = model(**batch).logits
        probs = torch.softmax(logits, dim=-1)[0].tolist()
    return sorted(((str(model.config.id2label[i]), float(p)) for i, p in enumerate(probs)),
                  key=lambda pair: pair[1], reverse=True)[:top_k]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--model", choices=["baseline", "rubert"], default="baseline")
    p.add_argument("--text", required=True)
    p.add_argument("--top-k", type=int, default=3)
    args = p.parse_args()
    if args.top_k < 1:
        p.error("--top-k должен быть положительным")
    result = baseline(args.text, args.top_k) if args.model == "baseline" else rubert(args.text, args.top_k)
    print("Команда:", args.text)
    for label, prob in result:
        print(f"  {label:<24} {prob:6.2%}")
    print("Внимание: это классификация намерения, а не извлечение параметров задачи.")
    print("Оценки вероятностей не гарантируют корректности распознавания живой речи.")


if __name__ == "__main__":
    main()
