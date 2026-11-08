"""Независимая оценка intent-классификатора на ручном наборе из 120 фраз.

Пример: python scripts/evaluate_manual.py --model rubert
Пример сравнения с v1: python scripts/evaluate_manual.py --model rubert
    --model-path ../GreenPlannerAI_Stage4_2/outputs/rubert/model --tag rubert_v1
"""
import argparse
import json
from pathlib import Path

from ml_common import OUT, evaluate

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--model", choices=("baseline", "rubert"), default="rubert")
    p.add_argument("--model-path", help="Путь к готовой модели; необязателен")
    p.add_argument("--tag", default=None, help="Папка под outputs/manual_holdout")
    p.add_argument("--batch-size", type=int, default=16)
    p.add_argument("--max-length", type=int, default=128)
    args = p.parse_args()
    if args.batch_size < 1:
        p.error("--batch-size должен быть >=1")

    rows = [json.loads(s) for s in (ROOT / "data/manual_holdout.jsonl").read_text(encoding="utf-8").splitlines() if s]
    x = [r["text"] for r in rows]
    y = [r["intent"] for r in rows]
    path = Path(args.model_path).resolve() if args.model_path else OUT / args.model / ("model" if args.model == "rubert" else "model.joblib")
    if not path.exists():
        raise SystemExit(f"Не найдена модель: {path}")

    if args.model == "baseline":
        import joblib
        model = joblib.load(path)  # Только собственный доверенный файл joblib.
        pred = model.predict(x).tolist()
        labels = sorted(map(str, model.classes_))
    else:
        import torch
        from transformers import AutoTokenizer, AutoModelForSequenceClassification
        tokenizer = AutoTokenizer.from_pretrained(path)
        model = AutoModelForSequenceClassification.from_pretrained(path)
        model.eval()
        labels = sorted(str(v) for v in model.config.id2label.values())
        pred = []
        with torch.inference_mode():
            for i in range(0, len(x), args.batch_size):
                batch = tokenizer(x[i:i+args.batch_size], padding=True, truncation=True,
                                  max_length=args.max_length, return_tensors="pt")
                pred.extend(str(model.config.id2label[k]) for k in model(**batch).logits.argmax(dim=-1).tolist())
    tag = args.tag or (args.model + "_v2")
    if not tag.replace("_", "").replace("-", "").isalnum():
        p.error("--tag должен состоять из букв, цифр, '_' или '-'")
    metrics = evaluate(y, pred, labels, OUT / "manual_holdout" / tag, rows=rows)
    print(f"Manual holdout ({tag}), {len(y)} фраз: accuracy={metrics['accuracy']:.4f}, macro F1={metrics['macro_f1']:.4f}")
    print("Ошибки: outputs/manual_holdout/" + tag + "/predictions.csv")
    print("Важно: эти фразы написаны вручную автором набора, не являются реальными пользовательскими записями.")


if __name__ == "__main__":
    main()
