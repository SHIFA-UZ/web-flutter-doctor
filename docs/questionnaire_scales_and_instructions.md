# Shared Scales and Administration Guidance (Doctor + Patient)

## Purpose

This note standardizes response scales and administration instructions for:

- `doctor_questionnaire.md`
- `patient_questionnaire.md`

Use this guidance to keep data comparable across respondent groups.

## 1) Core response scales

### A. Likert scale for usability and experience items

Use the same 5-point anchors throughout both questionnaires:

- 1 = Strongly disagree
- 2 = Disagree
- 3 = Neutral
- 4 = Agree
- 5 = Strongly agree

Apply this to:
- all SUS items (Questions 5-14),
- app-specific evaluation items (Questions 15-19).

### B. Multiple-choice items

- Single-select for frequency/device/primary role items.
- Multi-select only where explicitly stated ("choose up to 3").

### C. Open-text items

- Keep two open questions in each form (Questions 21-22).
- Do not make open-text fields mandatory if completion burden is a concern.

## 2) SUS scoring guidance

For each completed SUS block (Q5-14):

1. For odd-numbered SUS items (positive wording): score contribution = response - 1.
2. For even-numbered SUS items (negative wording): score contribution = 5 - response.
3. Sum all 10 contributions.
4. Multiply by 2.5 to obtain SUS score (0-100).

This should be calculated separately for doctor and patient cohorts.

## 3) Administration recommendations

- Keep forms anonymous.
- Show estimated completion time (8-12 minutes).
- Collect role/context information before SUS items.
- Keep item order fixed to preserve comparability.
- Use identical wording and anchors in repeated constructs (performance, availability, workflow fit).

## 4) Minimal analysis structure

- Descriptive statistics:
  - SUS mean/median per cohort,
  - distribution of app-specific ratings (Q15-19),
  - frequency ranking of improvement area (Q20).
- Qualitative coding:
  - open responses (Q21-22) grouped into recurring themes.

This combined structure supports both quantitative comparison and qualitative interpretation for thesis evaluation.
