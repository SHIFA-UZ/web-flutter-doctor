# Anonymized Qualitative Content Analysis (Mayring)

## Data Basis and Anonymization

- Source file: Survey of doctors on telemedicine experience.
- Material type: 15 structured interview/questionnaire transcripts (Q1-Q30).
- Anonymization actions performed:
  - Removed direct identifiers (names, exact clinic names, ages in profile headers).
  - Replaced geographic references with `[LOCATION]`.
  - Replaced doctor labels with participant IDs `P01` to `P15`.
  - Removed duplicated transcript segment.
- Output corpus: `docs/survey_doctors_anonymized.txt`.

## Methodological Approach (Mayring)

This analysis follows Mayring's qualitative content analysis using a **deductive-inductive structuring approach**:

1. **Definition of material**: Full anonymized corpus (15 participants, all 30 questions).
2. **Direction of analysis**: Identify perceived enablers, barriers, and implementation needs for telemedicine in clinical practice.
3. **Units of analysis**:
   - Coding unit: Meaningful statement/sentence in answer text.
   - Context unit: Full answer block for each question.
   - Evaluation unit: Participant-level transcript (`P01` to `P15`).
4. **Category development**:
   - Deductive categories from interview domains (usage, videocalls, scheduling, data handling, legal aspects).
   - Inductive refinement from recurring themes in statements.
5. **Revision and reduction**: Categories merged where semantically overlapping; frequencies counted at participant level.
6. **Interpretation**: Cross-category synthesis into implementation implications.

## Category System (Final)

| Main Category | Description | Participants (n=15) |
|---|---|---:|
| C1 Infrastructure and access constraints | Internet instability, low digital literacy, lack of devices, rural access barriers | 15 |
| C2 Informal platform dependence | Use of Telegram/WhatsApp/phone as de-facto telemedicine channels | 15 |
| C3 Telemedicine as supplementary care | Remote care useful for follow-up/triage but not equivalent to physical exam | 15 |
| C4 Documentation and workflow burden | Double documentation, paper fallback, manual archiving | 15 |
| C5 Data protection and privacy risk | Security concerns, leakage risk, personal-device vulnerability | 15 |
| C6 Legal and regulatory uncertainty | Unclear liability, unclear status of online advice/prescriptions | 15 |
| C7 Boundary strain and workload shift | After-hours messages, expectation of immediate response | 10 |
| C8 Demand for institutional solution | Need for national platform, standards, training, ministry guidance | 15 |
| C9 Uneven maturity by setting | Advanced digital use in a minority vs mostly low-maturity settings | 15 |

## Coding Rules and Anchor Examples

### C1 Infrastructure and access constraints
- Rule: Code statements about connectivity, devices, digital literacy, or regional infrastructure limitations.
- Example: "The biggest issue is connectivity. Many patients have weak mobile signals or do not have smartphones."

### C2 Informal platform dependence
- Rule: Code mentions of consumer apps as primary clinical communication tools.
- Example: "Not special medical apps, just Telegram."

### C3 Telemedicine as supplementary care
- Rule: Code statements that define remote care as follow-up support rather than diagnostic replacement.
- Example: "It is good for monitoring and guidance but cannot replace a physical examination."

### C4 Documentation and workflow burden
- Rule: Code references to manual notes, printing chats, dual systems, extra admin work.
- Example: "I spend extra time documenting things twice -- once digitally and once on paper."

### C5 Data protection and privacy risk
- Rule: Code explicit security, confidentiality, access control, data loss, unauthorized forwarding concerns.
- Example: "Telegram is convenient but not designed for medical data."

### C6 Legal and regulatory uncertainty
- Rule: Code uncertainty on legality, liability, billing, prescribing, and formal recognition of teleconsultations.
- Example: "I'm always unsure whether it's legally safe to provide advice through Telegram."

### C7 Boundary strain and workload shift
- Rule: Code blurred work/personal boundaries and after-hours demand.
- Example: "Patients often message late at night, expecting immediate replies."

### C8 Demand for institutional solution
- Rule: Code explicit requests for official platform, standards, training, and ministry-led implementation.
- Example: "We need clear national regulations, institutional training, and official software approved by the Ministry of Health."

### C9 Uneven maturity by setting
- Rule: Code variability in digital adoption between participants/settings.
- Example (high maturity): "40% of my patient interactions are conducted online."
- Example (low maturity): "Never. We don't have such a system."

## Case Summary (Cross-Category Interpretation)

1. **Systemic gap between demand and infrastructure**
   - Doctors see practical value in telemedicine (follow-up, triage, continuity), but implementation is constrained by connectivity and absent institutional platforms.

2. **Shadow digitalization is already happening**
   - Even where formal systems are missing, care has moved into consumer apps. This creates clinical utility but shifts risk to doctors and patients.

3. **Safety-governance mismatch**
   - Security and legal concerns are not peripheral; they are central adoption barriers. Doctors self-limit remote advice to avoid liability.

4. **Administrative paradox**
   - Digital communication can reduce patient travel but often increases clinician workload because documentation remains parallel (chat + paper).

5. **Two-speed adoption pattern**
   - A minority of participants show integrated digital workflows (scheduled video visits, EHR-linked communication), while most remain at informal, low-integration stages.

## Mayring-Conformant Conclusion

From a Mayring perspective, the central content structure is clear: **telemedicine is perceived as useful and necessary, but currently practiced in a legally and technically under-institutionalized form**. The dominant latent pattern is not resistance to digital care itself, but resistance to **unsafe and unsupported digital practice conditions**.

## Practical Implications for Your Thesis

- Prioritize a staged model: informal messaging -> secure communication -> integrated telemedicine platform.
- Treat legal framework and documentation automation as core implementation pillars, not secondary add-ons.
- Include rural connectivity and patient digital literacy as primary context variables in your discussion.
- Separate "clinical suitability" (follow-up vs diagnostics) by specialty in your interpretation chapter.

## Files Produced

- Anonymized corpus: `docs/survey_doctors_anonymized.txt`
- Category frequency sheet: `docs/survey_mayring_category_counts.txt`
- This report: `docs/survey_doctors_mayring_analysis.md`
