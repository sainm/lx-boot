# Scale source packages

The machine-readable technical regression registry is
[`scale-adaptation-registry.json`](./scale-adaptation-registry.json). Validate
its package paths, SHA-256 values, algorithm/template metadata and Golden Case
membership with:

```bash
python3 scripts/validate_scale_adaptation_registry.py
```

The source-independent generic calculator contract is maintained in
[`generic-score-method-registry.json`](./generic-score-method-registry.json)
and validated independently:

```bash
python3 scripts/validate_generic_score_method_registry.py
```

The source-independent dimension/time recoding contract is maintained in
[`generic-recode-method-registry.json`](./generic-recode-method-registry.json)
and validated independently:

```bash
python3 scripts/validate_generic_recode_method_registry.py
```

It declares only three reusable technical rules (`RECODE_SUM_TO_0_3`,
`SLEEP_DURATION_RECODE_0_3`, and `SLEEP_EFFICIENCY_RECODE_0_3`). Their evidence
uses synthetic inputs only; no original scale questions, translations, norms,
or clinical interpretation are embedded.

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

CI and local release checks can verify the newest Playwright report before it is
accepted as an artifact:

```bash
python3 scripts/verify_scale_adaptation_regression_report.py \
  --report-dir build/reports/scale-adaptation
```

The verifier requires the immutable registry fingerprint, every active entry's
required checks, zero `NOT_RUN` runtime checks, the synthetic five-method SQL
marker for every declared method, the disposable PostgreSQL scope, and the explicit Android/clinical-
approval scope. It does not establish authorization, professional approval or
business acceptance.

The CI workflow also uploads the post-run registry snapshot beside the report.
The independent `scale-adaptation-artifact-gate` job downloads that artifact,
restores the snapshot, and runs the same verifier again with
`--require-current-pointers`; a missing report or snapshot fails the gate.

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

## Candidate capability catalog (no source content)

[`scale-capability-catalog.json`](./scale-capability-catalog.json) is the fast
intake map for the remaining candidate instruments. It records only reusable
technical profiles, expected report shape and the input evidence still needed;
it deliberately contains no questions, translations, thresholds, norms, Golden
Cases or authorization claims. Validate it with:

```bash
python3 scripts/validate_scale_capability_catalog.py
```

`GENERIC_SINGLE_CHOICE` maps the current sum/reverse/weight/average engine to
single-choice candidates, while `GENERIC_TIME_RECODE` is explicitly synthetic-
fixture-only until a real manual defines the time fields and component rules.
`SCL90_RESTRICTED_PROFILE` is a separately catalogued restricted algorithm
profile with explicit source-package, missing-answer and governance inputs; it
does not make normative or formal-support claims. The registry validator
cross-checks every executable closure profile against this catalog.
Rater/interview candidates remain `UNSUPPORTED` and are not hidden behind the
self-report path. A candidate is not added to the executable scale registry
until its immutable versioned source package and external governance inputs
arrive; after that import, the normal full active-scale regression is mandatory.

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

## Generic score-method matrix (synthetic technical fixture)

`admin-web/e2e/generic-score-method-matrix.spec.ts` is deliberately a test-only
fixture: it contains no real scale questions and is not a registry entry or a
formal support claim. It exercises the shared ScalePackage/import/publication,
task, result and report path for `SIMPLE_SUM`, `REVERSE_SUM`, `WEIGHTED_SUM`,
`AVERAGE` and `WEIGHTED_AVERAGE`, including reverse-score, positive-weight and
non-unit `scoreCoefficient=1.25` metadata. `admin-web/e2e/fixtures/assert-generic-score-method-matrix.sql`
recomputes the persisted item trace, dimension aggregation, result precision
and report row in the same disposable PostgreSQL schema.

The five methods and their declared `REJECT`/`ALLOW`/`PRORATE` missing-answer
policies are machine-readable in `generic-score-method-registry.json`; for
`REJECT`, the contract explicitly means
`REJECT_SUBMISSION_WITHOUT_RESULT`. The runner requires a PostgreSQL evidence
marker for every declared method. This contract contains no original instrument
questions or interpretation text.

The latest full wrapper run `REG-PLAYWRIGHT-20260815-130746` records
`genericScoreMethodMatrix=PASS` and `genericQualityPolicyMatrix=PASS`; its
PostgreSQL markers include every declared method plus all fifteen method/policy
combinations (`REJECT`, `ALLOW` and `PRORATE`), `all_methods_policies`,
`policy_REJECT`, `policy_ALLOW`, `policy_PRORATE` and `all_policies`. REG-027 also locks the calculation
semantic that unweighted PRORATE uses question count while weighted methods
use declared weight, and carries ALLOW/PRORATE into the scoring trace. REG-028
locks `AVERAGE` and `WEIGHTED_AVERAGE` to average only answered items/weights
without an additional PRORATE multiplier. This
proves the reusable calculation path only. It does not import original
questions, establish a real scale, grant authorization, or replace
professional review and business acceptance. The same run records
`genericRecodeMethodMatrix=PASS`, with Playwright and PostgreSQL markers for
all three declared recoding rules, the `TIME`/`SLIDER` technical path, and
synthetic `SINGLE_CHOICE`/`MULTI_SELECT`/`MATRIX`/`TEXT_WITH_OPTION`/`TEXT`
input paths. The registry records this as seven question types only; it does
not establish support for PSQI, PSS-10, or any other candidate instrument.

The security- and audit-aware rerun `REG-PLAYWRIGHT-20260815-130746` supersedes
that technical baseline for the registry: all seven active entries pass 16/16
required checks, including effective question-set/skip-path and normative-
semantics markers, the shared cross-tenant, anonymous and respondent-role
boundary marker `security_boundaries` and per-scale `security_audit`
evidence for import, dual-review, rescore, report view, TEXT/PDF/Word export and
high-risk warning routing, including `PENDING` status and same-tenant warning-to-task
chain evidence, plus persisted `VALID` quality status with zero missing ratio and
no quality issue codes. The same report carries `export_semantics=PASS` for
the shared `ExportServiceTest` XML (seven tests, no skips/failures/errors) on
every active entry, covering the four controlled report templates in
TEXT/PDF/Word. The disposable PostgreSQL schema was removed after the run,
and wrapper cleanup failure is a hard failure. This remains technical evidence
only.

The wrapper records the verified run ID into every active registry entry only
after the application checks, core/publication/observability closure and
temporary-schema cleanup all succeed. `record_scale_adaptation_regression.py`
writes those mutable pointers atomically, while CI uses
`--require-current-pointers` to reject a report whose active entries still point
to an older run.

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

`REG-PLAYWRIGHT-20260815-094237` passes all sixteen required checks for K10 and
the six other active versions in one disposable PostgreSQL schema. The run
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

## SCS-SF official research-permission technical package

`scs-sf-v1-source-official-draft.json` is the seventh active versioned package.
Its governance record binds the [official SCS researcher's page](https://self-compassion.org/self-compassion-scales-for-researchers/),
the [SCS-SF information sheet](https://self-compassion.org/wp-content/uploads/2021/03/SCS-SF-information.pdf),
the [official English short form](https://self-compassion.org/wp-content/uploads/2020/01/ShortSCS.pdf),
and the posted [Chinese full-form material](https://self-compassion.org/wp-content/uploads/2018/06/ChineseSCS.pdf)
and [Japanese full-form material](https://self-compassion.org/wp-content/uploads/2018/05/JapaneseSCS.pdf).
The information sheet states that the scales may be used for research, clinical
work, or teaching, while translations require validation; that permission
record is preserved as technical governance evidence and does not replace this
project's professional review or business acceptance.

The package locks the 12-item, 1–5 SCS-SF. It groups the items into six
two-item dimensions: Self-Kindness, Self-Judgment, Common Humanity, Isolation,
Mindfulness, and Over-Identification. The three negative dimensions are
reverse-scored (`6 - response`), each dimension is averaged, and the total is
the mean of the six dimension means. The package deliberately exposes only
non-diagnostic self-observation descriptions: 1.00–2.49 low, 2.50–3.50
moderate, and 3.51–5.00 high. It does not load clinical norms or infer a
diagnosis.

Generate and validate it locally:

```bash
python3 scripts/generate_scs_sf_source_package.py
python3 scripts/validate_generic_scale_package.py doc/scale-packages/scs-sf-v1-source-official-draft.json
```

The shared generic closure now accepts the backend-supported method set
`SIMPLE_SUM`, `REVERSE_SUM`, `WEIGHTED_SUM`, `AVERAGE`, and
`WEIGHTED_AVERAGE`; the
SCS-SF package adds no scale-specific Controller, Service, renderer, Playwright
flow, or PostgreSQL branch. `REG-PLAYWRIGHT-20260815-094237` passes all sixteen
required checks for SCS-SF and the six other active versions in one disposable
PostgreSQL schema, including every persisted item trace, six dimension means,
reverse-score evidence, three-locale Web/results, Word/PDF/text exports, task
locking, immutable history, idempotency, concurrency, and append-only rescore.
The SCS-SF registry entry remains `TECHNICALLY_VERIFIED/BLOCKED_EXTERNAL` until
formal translation review, target-population/norms review, professional dual
approval, and business acceptance are recorded.

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
branch. `REG-PLAYWRIGHT-20260815-094237` passes all sixteen registered checks for
GAD-7, K10, K6, WHO-5, PHQ-9, SCL90-v2 and SCS-SF in one disposable PostgreSQL schema; that is
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

`REG-PLAYWRIGHT-20260815-094237` records PHQ-9 16/16 required checks in an
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
artifact below. `REG-PLAYWRIGHT-20260815-094237` verifies all five Golden Cases,
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
