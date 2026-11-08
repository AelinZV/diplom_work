# First baseline run — GreenPlanner AI v2

The baseline was trained using scikit-learn 1.8.0 on the expanded dataset in this archive.

- v2 validation (1000 synthetic examples): Accuracy 0.9040; Macro F1 0.9020
- v2 test (1000 synthetic examples): Accuracy 0.9190; Macro F1 0.9196
- v2 manual_holdout (120 manually authored examples): Accuracy 0.8333; Macro F1 0.8093
- v1 baseline on exactly the same 120 manually authored holdout: Accuracy 0.6583; Macro F1 0.6520

**Interpretation:** Improvements on a shared holdout are encouraging. Manual phrases were written by the dataset author and are not independent user submissions. The old and new synthetic test splits are different and must not be compared as if they were the same dataset. No new RuBERT training was performed in this package.
