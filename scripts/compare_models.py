"""Сравнение уже полученных метрик baseline и RuBERT."""
import json
from ml_common import OUT


def main():
    print(f"{'Модель':<14} {'Выборка':<12} {'Accuracy':>9} {'Macro F1':>10}")
    print("-" * 50)
    for model in ("baseline", "rubert"):
        for split in ("validation", "test"):
            file = OUT / model / split / "metrics.json"
            if not file.exists():
                continue
            scores = json.loads(file.read_text(encoding="utf-8"))
            print(f"{model:<14} {split:<12} {scores['accuracy']:>9.4f} {scores['macro_f1']:>10.4f}")
    print("Не используйте синтетический тест как единственную оценку качества на реальных запросах.")


if __name__ == "__main__":
    main()
