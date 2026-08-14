# Scale source packages

The machine-readable technical regression registry is
[`scale-adaptation-registry.json`](./scale-adaptation-registry.json). Validate
its package paths, SHA-256 values, algorithm/template metadata and Golden Case
membership with:

```bash
python3 scripts/validate_scale_adaptation_registry.py
```

Run the registry-driven source-package pass and write a machine-readable
report. This command intentionally reports only source validation; it does
not claim PostgreSQL, scoring-trace, Web/export, or clinical approval coverage:

```bash
python3 scripts/run_scale_adaptation_registry.py \
  --mode source \
  --report /tmp/scale-adaptation-regression.json
```

When an isolated PostgreSQL/application stack is already running and its
fixtures are loaded, and `PSY_E2E_SCHEMA`, `PSY_E2E_DB_HOST`,
`PSY_E2E_DB_PORT`, `PSY_E2E_DB_NAME`, and `PSY_E2E_DB_USERNAME` identify that
schema, the same runner can execute the exact Playwright selector and the
registered PostgreSQL evidence script for each package:

```bash
python3 scripts/run_scale_adaptation_registry.py \
  --mode playwright \
  --report /tmp/scale-adaptation-playwright.json
```

The Playwright mode executes PostgreSQL, scoring-trace, export and historical
compatibility evidence only when the isolated environment is supplied; absent
evidence remains `NOT_RUN`. It never establishes professional approval or
business acceptance. A run is valid only for the checks it actually executes.
If the wrapper fails before the registry runner starts (for example, PostgreSQL
is unreachable), it writes a machine-readable preflight failure under
`build/reports/scale-adaptation/failures/` with the phase, exit code and data
impact instead of silently losing the temporary logs.

The registry deliberately includes technical candidates whose governance status
is still blocked; membership does not mean formal clinical approval. A registry
driven regression run is recorded separately from the historical evidence of a
single-package technical run. The report fingerprint covers the immutable
package, algorithm, selector and Golden Case inputs; mutable support,
governance and last-run evidence fields are excluded so recording a completed
run or an external review does not invalidate the evidence artifact. Each registry entry also fixes
the expected result-rule codes, derived-metric codes, high-risk-rule codes and
the canonical SHA-256 of every Golden Case expectation; the validator rejects
drift between those fields and the source package. It also rejects a
`TECHNICALLY_VERIFIED` or `FULLY_SUPPORTED` entry unless its latest recorded
registry regression is `PASS`, preventing a stale or partial report from being
presented as completed support.

## Fast adaptation path for known scales

Known single-choice scales that can be expressed by
`GENERIC_SCORE_CALCULATOR:1` use the reusable `GENERIC_SINGLE_CHOICE`
technical-closure profile. They do not need a copied Controller, Service,
report page, export renderer, or scale-specific Playwright file. A new package
provides its immutable content and one valid closure Golden Case, then the
registry points to the shared
`admin-web/e2e/registered-generic-scale-technical-closure.spec.ts` selector.
The shared run covers controlled import, all Golden Cases, synthetic workflow
approval, publication, three-locale questions/results, Web, Word/PDF/text,
idempotent submission, concurrent submission, append-only rescore history,
new-version isolation and task locking.

The profile also uses one shared structural validator and one shared
PostgreSQL evidence script. The latter recomputes every persisted raw,
reverse, weighted and effective item score from the selected option and scale
definition, verifies dimension aggregation, and compares registry-supplied
closure totals, risks and derived metrics. Adding a compatible scale must not
add a `scaleCode` branch:

```json
{
  "sourceValidator": "scripts/validate_generic_scale_package.py",
  "sourceValidatorArgs": ["doc/scale-packages/example.json"],
  "postgresEvidenceScript": "admin-web/e2e/fixtures/assert-generic-scale-registry-closure.sql"
}
```

The minimum registry addition is:

```json
{
  "technicalClosure": {
    "profile": "GENERIC_SINGLE_CHOICE",
    "closureGoldenCaseCode": "A_VALID_BOUNDARY_CASE",
    "taskNamePrefix": "SHORT_CODE"
  },
  "runtimeEvidenceSelectors": {
    "playwrightSpec": "admin-web/e2e/registered-generic-scale-technical-closure.spec.ts",
    "playwrightTitle": "registered generic ScalePackage completes the reusable technical closure"
  }
}
```

The registry validator rejects this profile unless every question is
single-choice, the package uses the generic algorithm, and the selected Golden
Case is valid with an expected total and risk level. Complex instruments keep
an explicit restricted profile and specialized evidence; they must not be
forced through the quick path merely to reduce implementation work.

## K6 official free-use package

`k6-v1-source-official-draft.json` is a registered real-scale package for the
end-to-end publication closure. Its governance record cites the official
Harvard K6/K10 page, which states that use is free without formal permission,
and the officially posted English, Mandarin and Japanese forms. It contains
six questions, the official 0–24 scoring conversion, two population-scoped
result bands and the capability-aware Golden Cases required by this runtime.

Generate and validate it locally:

```bash
python3 scripts/generate_k6_source_package.py
python3 scripts/validate_generic_scale_package.py doc/scale-packages/k6-v1-source-official-draft.json
```

The rights evidence removes the permission-request blocker; it does not
fabricate local professional approval. Translation wording, target population,
the population-specific 13+ cut point and report language must still be
reviewed through the database-backed professional/business approval flow before
publication. Until those reviews exist, the package intentionally stays DRAFT.

The repository now runs the disposable PostgreSQL/Playwright technical closure
through the shared `GENERIC_SINGLE_CHOICE` selector. It proves the real source
artifact can traverse preview/import, three-locale content checks, all six
Golden Cases, independent role approvals, publication, task version locking,
the 13-point boundary score, all three submission-locale result semantics,
complete persisted scoring-trace fields, Japanese scale-specific
interpretation, Web presentation, and text/PDF/Word export. The approvals created by that test are
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

## K10 official free-use technical package

`k10-v1-source-official-draft.json` is the sixth active versioned package. Its
source record is bound to the [official Harvard K10/K6 page](https://rckessler.scholars.harvard.edu/k10-and-k6-scales), the
[official self-administered form](https://rckessler.scholars.harvard.edu/sites/g/files/omnuum8166/files/2026-03/Self%20admin_K10.pdf),
the [official scoring FAQ](https://rckessler.scholars.harvard.edu/sites/g/files/omnuum8166/files/2026-03/Scoring_K6_K10.pdf),
and the posted [Mandarin](https://rckessler.scholars.harvard.edu/sites/g/files/omnuum8166/files/2026-03/Chinese_Mandarin_K10.pdf)
and [Japanese](https://rckessler.scholars.harvard.edu/sites/g/files/omnuum8166/files/2026-03/Japanese_K10.pdf)
forms. The Harvard page records free use without formal permission while
requiring the stated copyright notice/citation; the package preserves that
requirement and does not claim that technical registration is formal approval.

This version locks the self-administered 30-day/5-point displayed response
order, recodes displayed answers through `reverseScore` to effective scores
1–5, sums 10–50, and uses the selected 10–19 / 20–24 / 25–29 / 30–50
non-diagnostic bands. The [AIHW K-10 value domain](https://meteor.aihw.gov.au/content/376091/download/pdf)
is recorded for those band definitions; other official materials contain
population- or purpose-specific variants, so the cut points remain a review
blocker. The package contains three-language technical text and nine Golden
Cases, including the explicit reverse-score case required by the shared
publication gate.

Generate and validate it locally:

```bash
python3 scripts/generate_k10_source_package.py
python3 scripts/validate_generic_scale_package.py doc/scale-packages/k10-v1-source-official-draft.json
```

`REG-PLAYWRIGHT-20260814-135509` passes all ten required checks for K10 and
the five other active versions in one disposable PostgreSQL schema. The run
includes persisted scoring traces, all result boundaries, three-locale Web,
Word/PDF/text exports, task locking, immutable history, idempotency,
concurrency and append-only rescore. Professional dual approval, formal
translation review, population/cutoff scope and business acceptance are
still external blockers, so the registry state remains
`TECHNICALLY_VERIFIED/BLOCKED_EXTERNAL`.

The next candidate is PSS-10, but it is intentionally not registered as a
source package yet. [Mapi/ePROVIDE's current PSS-10 record](https://eprovide.mapi-trust.org/instruments/perceived-stress-scale-10-items)
and the [Cohen laboratory permission page](https://www.cmu.edu/dietrich/psychology/stress-immunity-disease-lab/scales/index.html)
route use and electronic implementation through controlled conditions. Until
the project has a verifiable original version, electronic-use scope and
approved Chinese/Japanese materials, the tracker keeps `SCALE-PSS10-001` at
`INPUT_PENDING`; no copied or reconstructed PSS-10 source package is created.

## GAD-7 free-use technical package

`gad7-v1-source-draft.json` is the fifth registered package and the first
package added after the reusable quick-adaptation profile was complete. Its
source record cites Pfizer's statement that the PHQ and GAD-7 are available
without copyright restriction and at no charge, the English instrument and
instruction manual, the original validation, and Chinese/Japanese validation
evidence. Exact local Chinese/Japanese wording, adult/population scope,
severity interpretation, legal use scope and production acceptance remain
explicit blockers.

The package contains seven required 0–3 single-choice items, four non-diagnostic
severity bands, three-language content, and eight Golden Cases covering 0/4/5,
the original 10-point cutoff, 15, 21, missing answers and illegal options.
It adds no GAD-7 Controller, Service, renderer, Playwright flow or PostgreSQL
branch. `REG-PLAYWRIGHT-20260814-135509` passed all ten registered checks for
GAD-7, K10, K6, WHO-5, PHQ-9 and SCL90-v2 in one disposable PostgreSQL schema; that is
technical evidence only, not professional approval.

Generate and validate it locally:

```bash
python3 scripts/generate_gad7_source_package.py
python3 scripts/validate_generic_scale_package.py doc/scale-packages/gad7-v1-source-draft.json
```

## PHQ-9 public-domain severity package

`phq9-v1-source-draft.json` is a registered versioned package. Its
source record binds the nine-item 0–3 severity form, the 0–27 total, the
5/10/15/20 result boundaries, and the public-domain/no-permission-required
statement recorded in the Pfizer/PHQ instructions. The project Japanese
wording remains a draft: a Japanese electronic-use rights review and formal
Chinese/Japanese translation review are still required.

The package contains nine required single-choice items, five non-diagnostic
severity bands, three-language draft content, and ten Golden Cases covering
all result boundaries, missing/invalid input, and the item-9 positive signal.
Item 9 is represented as a controlled generic `scoreThreshold` high-risk rule
(`PHQ9_ITEM9_POSITIVE`). It raises an explicit human-review signal and does
not diagnose, assign a crisis level, or replace a named responder and SLA.
The shared generic closure verifies the high-risk rule, complete persisted
scoring trace, all locale/report/export semantics, task lock, idempotency,
concurrency, and append-only rescore history without adding PHQ-9-specific
business code.

Generate and validate it locally:

```bash
python3 scripts/generate_phq9_source_package.py
python3 scripts/validate_generic_scale_package.py doc/scale-packages/phq9-v1-source-draft.json
```

`REG-PLAYWRIGHT-20260814-135509` records PHQ-9 10/10 required checks in an
isolated PostgreSQL schema. The registry status is intentionally
`TECHNICALLY_VERIFIED/BLOCKED_EXTERNAL`; real professional dual approval,
formal trilingual review, population scope, item-9 crisis ownership/SLA and
business acceptance are not present.

## SCL-90 restricted technical profile package

`scl90-v2-source-technical.json` is the immutable technical version
`SCL90_USER_AUTHORIZED@authorized-profile-v1`. It is scoped to the user's
stated personal self-observation and non-commercial algorithm research. That
scope is recorded as project input only; it is not presented as a public
reproduction, adaptation, or translation license.

This version keeps the original 90-item, 0–4 response model and ten dimension
mappings, binds the restricted `SCL90_PROFILE:1` implementation, and exposes
only a profile-level `SCL90_PROFILE_ONLY` result (0–360 raw-score envelope).
It calculates GSI, PST, PSDI and ten dimension averages, and carries item 15
and item 63 as human-review signals. It deliberately loads no population norm
rows and invents no clinical cut-points. The zh-CN and ja-JP strings remain
technical drafts rather than approved translations.

Generate and validate it locally:

```bash
python3 scripts/generate_scl90_technical_package.py
python3 scripts/validate_scl90_source_package.py doc/scale-packages/scl90-v2-source-technical.json
```

The registered closure is separate from the historical `SCL90_USER_DRAFT@v1`
artifact below. `REG-PLAYWRIGHT-20260814-135509` verifies all five Golden Cases,
90-item and dimension scoring traces, GSI/PST/PSDI, high-risk signals, all
three locales, Web/Word/PDF/text output, task-version locking, immutable
history, idempotency, concurrency and append-only rescore. The PostgreSQL
schema is disposable and removed after the run; the synthetic approval rows
are workflow evidence only.

Formal support remains blocked by authorization-scope archiving, translation
rights/review, professional dual approval, population-specific norms,
crisis-response ownership/SLA and business acceptance. Pearson's [product
information](https://www.pearsonassessments.com/store/en/usd/p/100000645.html),
[global permission guidance](https://www.pearson.com/global-permission-granting.html/)
and [scales reference](https://www.pearsonassessments.com/content/dam/school/global/clinical/us/assets/scl-90-r/scl-90-r-scales.pdf)
are technical boundary references, not project clearance.

## SCL-90 source package

`scl90-v1-source-draft.json` is the first real-scale input for the closure work. It is deliberately a reviewable source artifact, not a production seed and not proof of copyright authorization.

It contains:

- 90 normalized question records, 10 dimension mappings (9 primary factors plus the seven additional sleep/eating items grouped as `OTHER`);
- Chinese, Japanese and English draft text for the scale, instructions, dimensions, questions and five response options;
- the canonical 0–4 scoring convention and the restricted `SCL90_PROFILE` algorithm binding for GSI, PST and PSDI;
- item 15 and item 63 safety signals, source-text correction notes, source references, and five Golden Case inputs/expectations (including invalid-option handling);
- explicit blockers for rights, translation review, population-specific norms, global result bands, professional review and crisis-response ownership.

Validate the SCL-90 artifact locally:

```bash
python3 scripts/generate_scl90_source_package.py
python3 scripts/validate_scl90_source_package.py
```

The generator is deterministic. Do not edit generated JSON by hand; change the source arrays and regenerate it. The artifact is accepted by the controlled source-package preview/confirm flow and is imported only as a tenant-owned DRAFT; the flow creates tenant-specific IDs instead of trusting IDs or release fingerprints from the source file. The technical Golden Case and scoring-trace checks are complete; publication still requires the three-language review matrix, source authorization, population norm metadata, formal result bands, and two independent approvals.

The artifact currently records 14 public references. They are evidence inputs, not a license:

- instrument/scoring structure: [Pearson Q-global score entry](https://qglobal.pearsonclinical.com/qg/static/Product/en/SCL-90-R/SCL-90-R_Enter_Scores.htm), [Pearson SCL-90-R scales](https://www.pearsonassessments.com/content/dam/school/global/clinical/us/assets/scl-90-r/scl-90-r-scales.pdf), [NIH GAP record](https://www.ncbi.nlm.nih.gov/projects/gap/cgi-bin/document.cgi?phd=2412&study_id=phs000222.v3.p2) and [Scielo psychometric/normative report](https://www.scielo.cl/scielo.php?pid=S0718-48082008000100004&script=sci_arttext);
- cross-language evidence: [Japanese reliability/validity study](https://pmc.ncbi.nlm.nih.gov/articles/PMC2582234/), [ITC translation and adaptation guidelines](https://www.intestcom.org/files/guideline_test_adaptation_2ed.pdf), [Chinese item-text study](https://pmc.ncbi.nlm.nih.gov/articles/PMC7982195/) and [Chinese undergraduate psychometric study](https://pubmed.ncbi.nlm.nih.gov/30465457/);
- norms: [Chinese norm-change study](https://pmc.ncbi.nlm.nih.gov/articles/PMC7873442/) and [Chinese undergraduate norms](https://pmc.ncbi.nlm.nih.gov/articles/PMC7579932/);
- rights and clinical safety: [Pearson permission guidance](https://www.pearson.com/en-us/global-permission-granting.html), [NIMH adult outpatient brief suicide safety assessment](https://www.nimh.nih.gov/research/research-conducted-at-nimh/asq-toolkit-materials/adult-outpatient/adult-outpatient-brief-suicide-safety-assessment-guide) and [NIMH clinical pathway](https://www.nimh.nih.gov/news/science-updates/2022/a-clinical-pathway-for-suicide-risk-screening-in-adult-primary-care).

The historical draft's isolated regression evidence remains limited to source import, five Golden Cases and three valid `SCL90_PROFILE` scoring traces; it is not in the active full-regression set. The v2 profile-only package above is the version that completes the reusable technical closure. These are technical checks only. The Chinese wording cannot be assumed to be a single canonical version, norms must be tied to a population and time period, and a positive self-harm item needs a trained-professional response path. The references do not grant permission to reproduce the instrument, translate it, use a norm table, or copy report templates. Pearson's permission process therefore remains a publication blocker, as do professional translation/norm review, population-specific result governance and a named crisis-response owner/SLA.

## WHO-5 open-access package

`who5-v1-source-draft.json` is the next versioned source package. It is based
on the WHO publication and its English, Chinese and Japanese materials; the
package records the CC BY-NC-SA 3.0 IGO scope and keeps the local governance
state as `DRAFT`. The English original is the binding reference for the
translated wording, and the source package does not claim WHO endorsement or
commercial-use clearance.

It contains:

- five self-report questions for the past two weeks and six response options scored 0–5;
- one `WHO5_TOTAL` dimension and the explicit generic metric `WHO5_PERCENTAGE_SCORE` (raw total × 4, 0–100);
- localized non-diagnostic text, result bands 0–12 / 13–25, and six Golden Cases including both boundaries, missing data and an invalid option;
- a publication blocker for professional review, translation review, license-scope review, cutoff scope and business acceptance.

Generate and validate it locally:

```bash
python3 scripts/generate_who5_source_package.py
python3 scripts/validate_who5_source_package.py
```

The current isolated regression proves source import, three-locale content,
the generic percentage binding, six Golden Case runs, complete scoring-trace
field shape, a published task with version lock, result/report semantics in
all three submission locales, and Word/PDF/text exports. It does not mark
WHO-5 as formally approved: the result cutoff, item-level follow-up
interpretation, translation wording and actual professional/business
approvals remain external gates.
