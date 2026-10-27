# Job Radar

A Spring Boot service that watches company job boards, throws out postings you can't apply to
(clearance, no sponsorship, 8+ years, staff/principal titles), scores the rest against your resume,
and keeps your application pipeline in one place.

It never submits applications. It finds, screens and ranks; you decide and apply.

```
             ┌──────────────┐   cron / "Fetch new jobs"
             │  Scheduler   │──────────────┐
             └──────────────┘              ▼
 Greenhouse API ─┐                ┌──────────────────┐     ┌──────────────────┐
                 ├─ JobSource ───▶│ IngestionService │────▶│ DealBreakerScanner│ regex rules
 Lever API ──────┘                │  dedupe + filter │     └──────────────────┘
                                  │                  │     ┌──────────────────┐
                                  │                  │────▶│  ScoringService  │ Claude API, or
                                  └────────┬─────────┘     └──────────────────┘ keyword fallback
                                           ▼
                                  PostgreSQL (Flyway)  ◀──── REST API ◀──── static web UI
```

## Stack

Java 21 · Spring Boot 3.3 (Web, Data JPA, Validation, Actuator) · Flyway · PostgreSQL (H2 for local dev)
· Spring `RestClient` · Claude Messages API · JUnit 5, Mockito, MockMvc · Docker · GitHub Actions

## Run it

**Quickest, no database to install** (uses an H2 file in `./data`):

```bash
mvn spring-boot:run
# open http://localhost:8080
```

**With PostgreSQL in Docker:**

```bash
cp .env.example .env        # optionally add ANTHROPIC_API_KEY
docker compose up --build
```

**Tests:**

```bash
mvn verify
```

## Apply agent

Press **Apply** on a job (or **Apply to a job** and paste a link and description). The agent:

1. **Tailors your resume** with Claude, choosing and reordering bullets and skills for the posting. A fact
   guard rejects any number or skill that isn't in your master resume, and employers, titles and dates
   are always copied from it.
2. **Renders a one-page PDF** in the standard layout, shrinking type and then trimming the oldest bullets
   until it fits. Saved under `applications/<date>_<company>_<title>/`.
3. **Opens the application in a Chromium window** (Playwright) and fills it: uploads the resume, answers
   contact and standard questions from `config/candidate.yml`, and drafts open-ended answers with Claude.
   Supports Greenhouse, Lever and Ashby (single page) and Workday (waits for you to sign in, then walks
   each step).
4. **Stops before Submit.** It never clicks a submit button. The Applications tab shows every answer,
   flags drafted or uncertain ones, and lists required questions it left for you. You review, submit,
   then press **I submitted it** (the job moves to Applied with a follow-up date a week out).

What it never answers on its own: legal attestations, background or clearance questions, and anything
about work authorization or self-identification that isn't set in `config/candidate.yml`.

### Set up

```bash
cp config/candidate.example.yml config/candidate.yml     # fill in your details and master resume
mvn exec:java -Dexec.args="install chromium"             # one-time browser download
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run          # key optional; without it, no tailoring or drafts
```

`config/candidate.yml` is git-ignored and the app listens on `127.0.0.1` only, since it drives a browser
with your personal details. The apply agent needs a desktop session, so it doesn't run inside Docker.

## Scoring

- With `ANTHROPIC_API_KEY` set, each posting that passes screening is sent to Claude along with the
  resume summary in `jobradar.profile`. Claude returns `{score, reason, matched}` as JSON.
- Without a key, or when a call fails (rate limit, timeout), the keyword scorer counts whole-word
  matches against `jobradar.skills`. A job is never dropped because the LLM was unavailable.
- Postings with a deal-breaker are marked `SKIP` without calling the LLM, which keeps costs down.

Verdicts: 70+ → `APPLY`, 45–69 → `MAYBE`, below 45 or any deal-breaker → `SKIP`.

## Configure

Everything lives in `src/main/resources/application.yml` under `jobradar:`:

| Key | What it does |
|---|---|
| `boards` | Companies to watch: `{source: greenhouse or lever, token: board slug, company: display name}` |
| `title-include` / `title-exclude` | Title keyword filters applied before screening |
| `skills` | Your skills, used by the keyword scorer |
| `profile` | Resume summary sent to Claude |
| `schedule.cron` / `schedule.zone` | When ingestion runs (default 8:00 AM ET, weekdays) |
| `claude.model` | Model name (env `CLAUDE_MODEL`) |

To find a company's board slug, open its careers page. `boards.greenhouse.io/<slug>` or
`jobs.lever.co/<slug>` is the token.

## API

| Method | Path | |
|---|---|---|
| GET | `/api/jobs?status=NEW` | List jobs (status optional), best fit first |
| GET | `/api/jobs/{id}` | One job, with full description |
| PATCH | `/api/jobs/{id}` | Update `status`, `notes`, `followUp` (or `clearFollowUp: true`) |
| POST | `/api/jobs` | Add a job by hand: `company`, `title`, `description`, optional `url`, `location` |
| DELETE | `/api/jobs/{id}` | Remove a job |
| POST | `/api/ingest/run` | Fetch from all boards now |
| POST | `/api/applications` | Start the apply agent: `jobId`, or `url` + `company` + `title` + `description` |
| GET | `/api/applications/{id}` | Status, message and the fill report |
| POST | `/api/applications/{id}/fill` | Fill the page that's open now (after signing in or moving to the next step) |
| POST | `/api/applications/{id}/submitted` | You submitted it: mark the job Applied |
| GET | `/api/applications/{id}/resume` | The tailored resume PDF |
| GET | `/actuator/health` | Health check |

Errors come back as RFC 7807 problem details.

## Design notes

- **Idempotent ingestion.** `(source, external_id)` is unique, so re-running a fetch never duplicates jobs.
- **Fault isolation.** Each board is fetched independently; one failing API is reported in the run
  summary and the rest still load.
- **Pluggable sources and rules.** Add a job board by implementing `JobSource`; add a screening rule by
  implementing `DealBreakerRule`.
- **Schema owned by Flyway.** Hibernate never generates DDL (`ddl-auto: none`); the same migration runs
  on H2 and PostgreSQL.
