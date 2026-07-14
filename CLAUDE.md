# Iceland camper blog

Static Astro site + Vue map island, plus a companion Android app in `android/`.
Read `README.md` for commands and workflow, `project-plan.md` for design
decisions and build status, `android/README.md` for the app.

## Key facts

- Site: Astro 5 content collections. Posts = markdown in `src/content/posts/`
  with schema-validated frontmatter (`src/content.config.ts`). Build must pass
  before pushing: `npm run build`.
- The map (`src/components/IcelandMap.vue`) is the only client-side JS. Zoom =
  animating the SVG viewBox; sizes multiply by `u = vb.w / 1000` to stay
  constant on screen. Marker coordinates and the coastline share projection
  constants from `src/data/iceland-map.json` — never hardcode projection math
  elsewhere; use `src/lib/geo.ts`.
- `npm run weather` fills missing `weather:` frontmatter from Open-Meteo and
  freezes it. Never overwrite an existing weather block — manual override wins
  by design.
- The Android app publishes via GitHub's Git Data API (no git on device); its
  markdown output must match the site's content schema. Change the schema →
  update `Post.kt` + `Frontmatter.kt` (app) and `scripts/fetch-weather.mjs`
  together.
- Trips: posts carry a `trip:` id pointing into `src/data/trips.json` (name,
  start point, per-trip stats); GPS traces live in `src/data/routes/<tripId>.json`.
  `src/lib/trips.ts` is the site's single accessor — `/` renders the latest
  trip, `/trips/<id>/` the rest. The app mirrors the Trip shape in `Trips.kt`.
- Deploys: every push to `main` rebuilds the site.

## Conventions

- Keep the site lightweight: no new client-side dependencies without a reason;
  prefer build-time work.
- Data files are hand-editable on purpose (`trips.json`, `targets.json`,
  `routes/*.json`) — keep them simple flat structures.
