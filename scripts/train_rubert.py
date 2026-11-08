"""Дообучение RuBERT для 10 классов команд GreenPlanner (только intent)."""
from __future__ import annotations

import argparse
import json
import random

from ml_common import OUT, class_names, evaluate, load_split


def main():
    p = argparse.ArgumentParser(description="Дообучение русскоязычного BERT для классификации намерений")
    p.add_argument("--checkpoint", default="DeepPavlov/rubert-base-cased")
    p.add_argument("--epochs", type=int, default=3)
    p.add_argument("--batch-size", type=int, default=8)
    p.add_argument("--grad-accumulation", type=int, default=1)
    p.add_argument("--max-length", type=int, default=96)
    p.add_argument("--lr", type=float, default=2e-5)
    p.add_argument("--device", choices=("auto", "cpu", "cuda"), default="auto")
    args = p.parse_args()
    if args.epochs < 1 or args.batch_size < 1 or args.grad_accumulation < 1 or args.max_length < 16 or args.lr <= 0:
        p.error("Некорректные параметры обучения")

    try:
        import numpy as np
        import torch
        from torch.utils.data import Dataset, DataLoader
        from transformers import AutoTokenizer, AutoModelForSequenceClassification, get_linear_schedule_with_warmup
    except ImportError as e:
        raise SystemExit("Установите зависимости: python -m pip install -r requirements-rubert.txt") from e

    random.seed(42)
    np.random.seed(42)
    torch.manual_seed(42)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(42)
    if args.device == "cuda" and not torch.cuda.is_available():
        raise SystemExit("CUDA недоступна: используйте --device cpu или установите совместимый PyTorch/CUDA")
    device = torch.device("cuda" if args.device == "cuda" or (args.device == "auto" and torch.cuda.is_available()) else "cpu")
    print("Устройство:", device)
    if device.type == "cpu":
        print("RuBERT — крупная модель. Для обучения на CPU потребуется значительно больше ресурсов, чем для baseline.")

    train, valid, test = (load_split(name) for name in ("train", "validation", "test"))
    labels = class_names(train)
    if set(class_names(valid)) != set(labels) or set(class_names(test)) != set(labels):
        raise SystemExit("Список классов в выборках отличается")
    label2id = {label: i for i, label in enumerate(labels)}
    id2label = {i: label for label, i in label2id.items()}

    print("Загрузка токенизатора и предобученных весов:", args.checkpoint)
    print("На первом запуске необходим интернет для скачивания модели.")
    tokenizer = AutoTokenizer.from_pretrained(args.checkpoint)
    model = AutoModelForSequenceClassification.from_pretrained(
        args.checkpoint, num_labels=len(labels), label2id=label2id, id2label=id2label
    )
    # Сообщение о случайной инициализации классификационной головы при первом запуске нормально.
    model.to(device)

    class CommandDataset(Dataset):
        def __init__(self, rows):
            self.enc = tokenizer(
                [r["text"] for r in rows], padding="max_length", truncation=True,
                max_length=args.max_length, return_tensors="pt"
            )
            self.labels = torch.tensor([label2id[r["intent"]] for r in rows], dtype=torch.long)

        def __len__(self):
            return len(self.labels)

        def __getitem__(self, index):
            item = {key: val[index] for key, val in self.enc.items()}
            item["labels"] = self.labels[index]
            return item

    train_loader = DataLoader(CommandDataset(train), batch_size=args.batch_size, shuffle=True, num_workers=0)
    valid_loader = DataLoader(CommandDataset(valid), batch_size=args.batch_size, shuffle=False, num_workers=0)
    test_loader = DataLoader(CommandDataset(test), batch_size=args.batch_size, shuffle=False, num_workers=0)

    def predictions(loader):
        model.eval()
        result = []
        with torch.inference_mode():
            for batch in loader:
                batch = {k: v.to(device) for k, v in batch.items()}
                logits = model(**batch).logits
                result.extend(torch.argmax(logits, dim=-1).cpu().tolist())
        return [id2label[n] for n in result]

    from math import ceil
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    steps_per_epoch = ceil(len(train_loader) / args.grad_accumulation)
    total_steps = steps_per_epoch * args.epochs
    scheduler = get_linear_schedule_with_warmup(
        optimizer, num_warmup_steps=max(1, round(total_steps * 0.1)), num_training_steps=total_steps
    )
    out = OUT / "rubert"
    out.mkdir(parents=True, exist_ok=True)
    truth_valid = [row["intent"] for row in valid]
    best_f1 = -1.0
    history = []

    for epoch in range(1, args.epochs + 1):
        model.train()
        optimizer.zero_grad(set_to_none=True)
        sum_loss = 0.0
        for step, batch in enumerate(train_loader, 1):
            batch = {k: v.to(device) for k, v in batch.items()}
            loss = model(**batch).loss
            sum_loss += loss.item()
            (loss / args.grad_accumulation).backward()
            if step % args.grad_accumulation == 0 or step == len(train_loader):
                torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
                optimizer.step()
                scheduler.step()
                optimizer.zero_grad(set_to_none=True)
            if step % 25 == 0 or step == len(train_loader):
                print(f"Эпоха {epoch}/{args.epochs}, батч {step}/{len(train_loader)}")

        predicted_valid = predictions(valid_loader)
        valid_metrics = evaluate(truth_valid, predicted_valid, labels, out / "validation", rows=valid)
        entry = {"epoch": epoch, "train_avg_loss": round(sum_loss / len(train_loader), 6),
                 "val_accuracy": valid_metrics["accuracy"], "val_macro_f1": valid_metrics["macro_f1"]}
        history.append(entry)
        print(f"Эпоха {epoch}: loss={entry['train_avg_loss']:.4f}; val macro F1={entry['val_macro_f1']:.4f}")
        if valid_metrics["macro_f1"] > best_f1:
            best_f1 = valid_metrics["macro_f1"]
            checkpoint_dir = out / "model"
            model.save_pretrained(checkpoint_dir, safe_serialization=True)
            tokenizer.save_pretrained(checkpoint_dir)
            print("Сохранена лучшая модель на validation")
        # После evaluate() возвращаем train() в начале следующей эпохи.

    (out / "history.json").write_text(json.dumps(history, ensure_ascii=False, indent=2), encoding="utf-8")

    # Возвращаемся к сохранённой лучшей по validation модели; только потом оцениваем test.
    model = AutoModelForSequenceClassification.from_pretrained(out / "model").to(device)
    best_valid_pred = predictions(valid_loader)
    metrics_valid = evaluate(truth_valid, best_valid_pred, labels, out / "validation", rows=valid)
    test_pred = predictions(test_loader)
    metrics_test = evaluate([r["intent"] for r in test], test_pred, labels, out / "test", rows=test)
    (out / "training_info.json").write_text(json.dumps({
        "checkpoint": args.checkpoint,
        "train_source": "synthetic",
        "seed": 42,
        "best_val_macro_f1": best_f1,
        "device": str(device),
        "epochs": args.epochs,
        "batch_size": args.batch_size,
        "grad_accumulation": args.grad_accumulation,
        "max_length": args.max_length,
        "learning_rate": args.lr,
        "train_samples": len(train),
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Готово. val macro F1={metrics_valid['macro_f1']:.4f}; test macro F1={metrics_test['macro_f1']:.4f}")
    print("Модель и токенизатор:", out / "model")
    print("Тест оценён только после выбора модели по validation.")


if __name__ == "__main__":
    main()
