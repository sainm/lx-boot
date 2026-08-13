# Scale source packages

## K6 official free-use package

`k6-v1-source-official-draft.json` is the current real-scale candidate for the
end-to-end publication closure. Its governance record cites the official
Harvard K6/K10 page, which states that use is free without formal permission,
and the officially posted English, Mandarin and Japanese forms. It contains
six questions, the official 0–24 scoring conversion, two population-scoped
result bands and the capability-aware Golden Cases required by this runtime.

Generate and validate it locally:

```bash
python3 scripts/generate_k6_source_package.py
python3 scripts/validate_k6_source_package.py
```

The rights evidence removes the permission-request blocker; it does not
fabricate local professional approval. Translation wording, target population,
the population-specific 13+ cut point and report language must still be
reviewed through the database-backed professional/business approval flow before
publication. Until those reviews exist, the package intentionally stays DRAFT.

The repository now has a disposable PostgreSQL/Playwright technical-closure
case in `admin-web/e2e/k6-technical-closure.spec.ts`. It proves the real source
artifact can traverse preview/import, three-locale content checks, all six
Golden Cases, independent role approvals, publication, task version locking,
the 13-point boundary score, Japanese scale-specific interpretation, Web
presentation, and text/PDF/Word export. The approvals created by that test are
synthetic workflow evidence in a schema that is deleted after the run; they are
not professional sign-off or production business acceptance.

Production approvals are append-only records bound to the scale release
fingerprint. Every approval requires the reviewer's immutable name snapshot, a
controlled evidence reference, and an explicit review scope; a professional
approval additionally requires a qualification-record reference. V26 enforces
those requirements for new `APPROVED` rows at the PostgreSQL boundary while
leaving legacy records intact and blocked from publication until replaced by a
complete review. Reusing a review token with different evidence is rejected.

The generated report snapshot retains both the approved locale-specific
non-diagnostic statement from the scale version and the system safety notice.
PDF export refuses to silently fall back to a font that cannot encode the
report. Production must either provide an embeddable CJK font at
`PSY_PDF_FONT_PATH` or install one of the supported platform fonts; absence is
reported as `EXPORT_PDF_FONT_MISSING` instead of producing a broken file.

## SCL-90 source package

`scl90-v1-source-draft.json` is the first real-scale input for the closure work. It is deliberately a reviewable source artifact, not a production seed and not proof of copyright authorization.

It contains:

- 90 normalized question records, 10 dimension mappings (9 primary factors plus the seven additional sleep/eating items grouped as `OTHER`);
- Chinese, Japanese and English draft text for the scale, instructions, dimensions, questions and five response options;
- the canonical 0–4 scoring convention and the restricted `SCL90_PROFILE` algorithm binding for GSI, PST and PSDI;
- item 15 and item 63 safety signals, source-text correction notes, source references, and four Golden Case inputs/expectations;
- explicit blockers for rights, translation review, population-specific norms, global result bands, professional review and crisis-response ownership.

Validate the SCL-90 artifact locally:

```bash
python3 scripts/generate_scl90_source_package.py
python3 scripts/validate_scl90_source_package.py
```

The generator is deterministic. Do not edit generated JSON by hand; change the source arrays and regenerate it. The artifact is accepted by the controlled source-package preview/confirm flow and is imported only as a tenant-owned DRAFT; the flow creates tenant-specific IDs instead of trusting IDs or release fingerprints from the source file. The remaining work is to complete the three-language review matrix, source authorization, population norm metadata, Golden Case runs, and the two independent approvals before publication.

The artifact currently records 14 public references. They are evidence inputs, not a license:

- instrument/scoring structure: [Pearson Q-global score entry](https://qglobal.pearsonclinical.com/qg/static/Product/en/SCL-90-R/SCL-90-R_Enter_Scores.htm), [Pearson SCL-90-R scales](https://www.pearsonassessments.com/content/dam/school/global/clinical/us/assets/scl-90-r/scl-90-r-scales.pdf), [NIH GAP record](https://www.ncbi.nlm.nih.gov/projects/gap/cgi-bin/document.cgi?phd=2412&study_id=phs000222.v3.p2) and [Scielo psychometric/normative report](https://www.scielo.cl/scielo.php?pid=S0718-48082008000100004&script=sci_arttext);
- cross-language evidence: [Japanese reliability/validity study](https://pmc.ncbi.nlm.nih.gov/articles/PMC2582234/), [ITC translation and adaptation guidelines](https://www.intestcom.org/files/guideline_test_adaptation_2ed.pdf), [Chinese item-text study](https://pmc.ncbi.nlm.nih.gov/articles/PMC7982195/) and [Chinese undergraduate psychometric study](https://pubmed.ncbi.nlm.nih.gov/30465457/);
- norms: [Chinese norm-change study](https://pmc.ncbi.nlm.nih.gov/articles/PMC7873442/) and [Chinese undergraduate norms](https://pmc.ncbi.nlm.nih.gov/articles/PMC7579932/);
- rights and clinical safety: [Pearson permission guidance](https://www.pearson.com/en-us/global-permission-granting.html), [NIMH adult outpatient brief suicide safety assessment](https://www.nimh.nih.gov/research/research-conducted-at-nimh/asq-toolkit-materials/adult-outpatient/adult-outpatient-brief-suicide-safety-assessment-guide) and [NIMH clinical pathway](https://www.nimh.nih.gov/news/science-updates/2022/a-clinical-pathway-for-suicide-risk-screening-in-adult-primary-care).

These references support a review checklist: the Chinese wording cannot be assumed to be a single canonical version, norms must be tied to a population and time period, and a positive self-harm item needs a trained-professional response path. They do not grant permission to reproduce the instrument, translate it, use a norm table, or copy report templates. Pearson's permission process therefore remains a publication blocker, as do professional translation/norm review and a named crisis-response owner/SLA.
