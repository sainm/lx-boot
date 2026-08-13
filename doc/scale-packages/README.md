# SCL-90 source package

`scl90-v1-source-draft.json` is the first real-scale input for the closure work. It is deliberately a reviewable source artifact, not a production seed and not proof of copyright authorization.

It contains:

- 90 normalized question records, 10 dimension mappings (9 primary factors plus the seven additional sleep/eating items grouped as `OTHER`);
- Chinese, Japanese and English draft text for the scale, instructions, dimensions, questions and five response options;
- the canonical 0–4 scoring convention and the restricted `SCL90_PROFILE` algorithm binding for GSI, PST and PSDI;
- item 15 and item 63 safety signals, source-text correction notes, source references, and four Golden Case inputs/expectations;
- explicit blockers for rights, translation review, population-specific norms, global result bands, professional review and crisis-response ownership.

Validate the artifact locally:

```bash
python3 scripts/generate_scl90_source_package.py
python3 scripts/validate_scl90_source_package.py
```

The generator is deterministic. Do not edit generated JSON by hand; change the source arrays and regenerate it. The artifact is not accepted by the existing `PSY_SCALE_PACKAGE` database import endpoint yet because it intentionally has no tenant-specific IDs or release fingerprint. The controlled next step is to load it into a draft scale through the existing import/preview/confirm flow, then fill the three-language review matrix, source authorization, population norm metadata, Golden Case runs, and the two independent approvals.

The artifact currently records 14 public references. They are evidence inputs, not a license:

- instrument/scoring structure: [Pearson Q-global score entry](https://qglobal.pearsonclinical.com/qg/static/Product/en/SCL-90-R/SCL-90-R_Enter_Scores.htm), [Pearson SCL-90-R scales](https://www.pearsonassessments.com/content/dam/school/global/clinical/us/assets/scl-90-r/scl-90-r-scales.pdf), [NIH GAP record](https://www.ncbi.nlm.nih.gov/projects/gap/cgi-bin/document.cgi?phd=2412&study_id=phs000222.v3.p2) and [Scielo psychometric/normative report](https://www.scielo.cl/scielo.php?pid=S0718-48082008000100004&script=sci_arttext);
- cross-language evidence: [Japanese reliability/validity study](https://pmc.ncbi.nlm.nih.gov/articles/PMC2582234/), [ITC translation and adaptation guidelines](https://www.intestcom.org/files/guideline_test_adaptation_2ed.pdf), [Chinese item-text study](https://pmc.ncbi.nlm.nih.gov/articles/PMC7982195/) and [Chinese undergraduate psychometric study](https://pubmed.ncbi.nlm.nih.gov/30465457/);
- norms: [Chinese norm-change study](https://pmc.ncbi.nlm.nih.gov/articles/PMC7873442/) and [Chinese undergraduate norms](https://pmc.ncbi.nlm.nih.gov/articles/PMC7579932/);
- rights and clinical safety: [Pearson permission guidance](https://www.pearson.com/en-us/global-permission-granting.html), [NIMH adult outpatient brief suicide safety assessment](https://www.nimh.nih.gov/research/research-conducted-at-nimh/asq-toolkit-materials/adult-outpatient/adult-outpatient-brief-suicide-safety-assessment-guide) and [NIMH clinical pathway](https://www.nimh.nih.gov/news/science-updates/2022/a-clinical-pathway-for-suicide-risk-screening-in-adult-primary-care).

These references support a review checklist: the Chinese wording cannot be assumed to be a single canonical version, norms must be tied to a population and time period, and a positive self-harm item needs a trained-professional response path. They do not grant permission to reproduce the instrument, translate it, use a norm table, or copy report templates. Pearson's permission process therefore remains a publication blocker, as do professional translation/norm review and a named crisis-response owner/SLA.
