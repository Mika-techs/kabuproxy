# kabuProxy

A nicer, always-logged-in mirror of [digikabu.de](https://www.digikabu.de) (the timetable portal of BSZ Wiesau).

A crawler logs in to digikabu on behalf of every linked user, stores the **timetable, exam plan (Schulaufgabenplan)
and absences (Fehlzeiten)** in MySQL, and serves them through its own UI. Login goes through **Authentik (OIDC)**,
so there is no 30-minute digikabu cookie anymore – you stay logged in for weeks.

Stack (same as taa, without company code): Java 25 · TomEE 11 (MicroProfile) · JSF/MyFaces + PrimeFaces 16 ·
Hibernate 7 · Liquibase · MySQL 8 · log4j2 · Lombok · Jsoup.

## Features

- Week grid (desktop) / day cards (mobile), light + dark mode, "jetzt" marker on the running lesson
- Per-user settings page (Einstellungen, stored in the DB so they follow you across devices): light/dark/system
  mode, accent colour (presets or free pick, auto-lightened in dark mode) and the base theme colours (background,
  surfaces, text, change/cancel/new/holiday markers) separately for light and dark
- Own lesson colours per subject, optionally per teacher for subjects with several (side bar in the timetable; teacher →
  subject → accent colour, lightened in dark mode)
- Own display names per subject or subject + teacher, e.g. "AEuP" taught by RAH → "Web" (one digikabu subject that is
  really several)
- Changes (Änderung, new/cancelled lessons) highlighted, plus "N changes since your last visit"
- **Änderungen** page: the whole change log of the class per lesson day (from today or all), unseen changes marked
- History: digikabu only shows ±1 week, kabuProxy keeps every week it has seen
- Exams and holidays as a clean list; block-schedule "kein Unterricht" weeks merged into ranges
- Own absences only (admins never see other users' absences)
- Multi-user: users log in via Authentik and link their digikabu account under **Einstellungen** (or an admin does it
  for them); the password is verified by a real login and stored AES-256-GCM encrypted. New users stay pending until
  an admin activates them – linking alone doesn't activate, and nothing is crawled before activation. Self-service
  test logins are limited to 5 per 15 min per user.

## Quick start (local)

```bash
cp .env.example .env
#   KABU_CRED_KEY=$(openssl rand -base64 32)
#   KABU_DEV_AUTH=true      # skip Authentik until it is configured
./scripts/dev.sh            # MySQL in docker + TomEE embedded on http://localhost:8080
```

Open <http://localhost:8080>, go to **Einstellungen → digikabu-Zugang** (or **Admin → Verknüpfen**), enter your
digikabu login. The first crawl starts
immediately; afterwards every 30 min between 06:00 and 22:00.

> `dev.sh` runs `tomee-embedded:run`, which stops when its stdin closes. Run it in a terminal (not with `&`).
> Don't run another Maven build into `target/` while it is up – stop it first (Ctrl+C / `quit`).

## IntelliJ (also works as Flatpak)

Run configurations live in `.run/` and show up automatically. They need no docker CLI and no shell scripts, only
Maven and IntelliJ's Docker plugin, which talks to the Docker socket directly. That matters in the Flatpak sandbox,
where `docker`/`docker compose` don't exist.

| Configuration | What it does |
|---|---|
| **MySQL** | starts `mysql:8.4` as container `kabuproxy-mysql` on `127.0.0.1:3306` (same data volume as the compose `localdb` profile – run only one of the two) |
| **kabuProxy (dev server)** | `clean package tomee-embedded:run` → <http://localhost:8080>. Settings come from `./.env` (read by the app itself, see below), DB from the pom defaults `kabuproxy/kabuproxy@localhost:3306` |
| **build** / **tests** | `clean package` / `test` |
| **docker libs** | copies Hibernate + MySQL driver into `target/docker-lib` |
| **Docker image** | runs *build* + *docker libs*, then builds `kabuproxy:latest` via the Docker plugin |

One-time setup:
- *Settings → Build → Docker*: a Docker connection named **`Docker`** pointing to your socket, e.g.
  `unix:///run/user/1000/docker.sock` for rootless Docker. The Flatpak needs access to it; the default
  `xdg-run/docker.sock` permission covers `/run/user/<uid>/docker.sock`.
- *Project Structure → SDK*: a JDK 25 (Maven runs with the project JDK).

Order: **MySQL** → wait a few seconds → **kabuProxy (dev server)**. Stop the dev server before running *build*,
*tests* or *Docker image*: they rebuild `target/`, which the running server serves from.

`.env` in the project root is loaded as a low-priority config source (`DotEnvConfigSource`). Real environment variables
always win, and the container has no `.env`, so nothing secret has to go into the committed run configurations.

## Production (Docker Compose)

```bash
cp .env.example .env    # fill in DB, KABU_CRED_KEY, OIDC, KABU_PUBLIC_URL; KABU_DEV_AUTH=false
./scripts/build-image.sh                     # mvn package on the host + docker build (no Maven inside Docker)
docker compose up -d                         # external DB (KABU_DB_* in .env)
docker compose --profile localdb up -d       # or with the bundled MySQL
```

The image is `tomee:11.0.0-M1-jre25-alpine-microprofile` + the war + Hibernate/MySQL jars. The container
listens on `127.0.0.1:8080` only – put your TLS reverse proxy in front (see docs/AUTHENTIK.md §6). The DB settings
are applied at container start by `docker/entrypoint.sh`, so any characters are allowed in `KABU_DB_PASSWORD`.

Authentik setup: **[docs/AUTHENTIK.md](docs/AUTHENTIK.md)**.

## Configuration (env vars)

| Variable | Default | |
|---|---|---|
| `KABU_DB_HOST` / `_PORT` / `_DATABASE` / `_USERNAME` / `_PASSWORD` | – / 3306 | MySQL; schema is created by Liquibase on start |
| `KABU_DB_JDBC_PARAMS` | – | extra JDBC params, e.g. `useSSL=true&requireSSL=true` |
| `KABU_CRED_KEY` | – | **required**, 32 bytes base64 (`openssl rand -base64 32`). Losing it = all stored passwords unreadable |
| `KABU_OIDC_PROVIDER_URI` / `_CLIENT_ID` / `_CLIENT_SECRET` | – | Authentik issuer + client |
| `KABU_PUBLIC_URL` | – | public base URL (for the OIDC redirect behind a TLS proxy) |
| `KABU_OIDC_ADMIN_GROUP` | `kabuproxy-admin` | Authentik group → admin |
| `KABU_DEV_AUTH` | `false` | `true` = no login at all, everybody is admin. **Local only.** |
| `KABU_CRAWL_ENABLED` | `true` | |
| `KABU_CRAWL_INTERVAL_MINUTES` | `30` | |
| `KABU_CRAWL_ACTIVE_FROM` / `_TO` | `06:00` / `22:00` | Europe/Berlin |
| `KABU_REFRESH_COOLDOWN_MINUTES` | `5` | rate limit of the "Jetzt aktualisieren" button |
| `KABU_DIGIKABU_BASE_URL` | `https://www.digikabu.de` | |

## How crawling works

Per cycle, for every active account (sequentially, ~400 ms between requests):
login → navbar (name + class) → **once per class**: timetable of this/previous/next week (`POST /Stundenplan/StdPlanStd`,
week switched via `ChangeDate ±7` in the digikabu session) + exam plan → **per user**: absences.

Failures are classified:

| | Reaction |
|---|---|
| wrong password | account `AUTH_FAILED`, **paused** until the user (or an admin) re-enters credentials (avoids account lock) |
| network / 5xx / session lost | `UNAVAILABLE`, exponential back-off 30 min → 8 h |
| unexpected HTML | `PARSE_ERROR`, raw page stored in `crawl_debug` (7 days), old data kept |

The UI always shows "Stand …" and a banner while an account has a problem.

## Development

```bash
mvn -B clean package        # checkstyle (validate) + unit tests + war
mvn test                    # unit tests only: parsers vs. anonymized fixtures, crypto, diff, HTTP flow against a stub
```

Code style is taa's checkstyle (Allman braces, `@Inject private Logger logger;`, no star imports) – a violation fails
the build. Test fixtures under `src/test/resources/fixtures` are real digikabu pages with personal data replaced.

## License

Copyright (C) 2026 Mika Schmid. Licensed under the [GNU General Public License v3.0](LICENSE) or later, with an
additional permission (GPLv3 section 7) to combine it with Liquibase, which is FSL-licensed since 5.0 – see
[NOTICE](NOTICE).
