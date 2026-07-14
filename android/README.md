# Fri Camper — companion Android app

Publishing tool for the blog: write posts, attach photos, log the GPS route,
tweak stats — everything lands as git commits in this repo, which triggers the
site deploy. No backend, no accounts, just a GitHub token.

## How it publishes

The app never runs git. It uses GitHub's Git Data API to make one atomic
commit per publish (blobs → tree → commit → branch ref), carrying the markdown
post, resized photos, transcoded clips, `src/data/routes/<tripId>.json` and/or
`src/data/trips.json` together. Saving a post only writes a bundle to app
storage; a WorkManager job pushes queued bundles whenever the phone has
connectivity and retries with backoff when it doesn't. Losing signal just
means the queue grows.

## Prerequisites & status

- Android Studio Ladybug (2024.2) or newer — AGP 8.7.3 requires it. The
  bundled JDK is fine (needs 17+). Gradle 8.11.1 downloads via the wrapper;
  SDK 35 installs on first sync.
- Runs on any device/emulator with Android 8.0+ (minSdk 26).
- **Heads up:** the app compiles and its unit tests pass
  (`./gradlew :app:assembleDebug :app:testDebugUnitTest`), but it hasn't been
  exercised on a real device yet — video transcoding and the publish flow in
  particular deserve a test run against a scratch repo first.

## Setup

1. Open the `android/` folder in Android Studio (it will download the Gradle
   distribution and Android SDK bits on first sync) and run on a device.
2. On github.com: Settings → Developer settings → Fine-grained tokens →
   generate one scoped to **only this repo** with **Contents: read and write**.
3. In the app's Settings screen: repo owner, repo name, branch (`main`),
   the token, and the commit author name/email.

## What does what

- **Trips** — list/create trips (`src/data/trips.json`: name, start point,
  per-trip stats) and pick the *active* trip. New posts, GPS points and stats
  edits all belong to the active trip. Creating a trip queues the updated
  trips.json; the local copy is authoritative until it publishes.
- **Record route** toggle — foreground service, one GPS point per ~5 min /
  100 m (balanced-power), logged per trip and published as
  `src/data/routes/<tripId>.json` (with the next post, or via "Publish route
  now"). The site draws it as the driven route on the trip's map. Requires an
  active trip.
- **New post** — title, date, trip, place, coordinates (one tap to use GPS),
  excerpt, markdown body, photos (system picker → downscaled to 1600px JPEG),
  clips (same picker → transcoded on-device to ~270p H.264/AAC, capped at
  30MB per clip because commits are inline base64), and a weather card that
  prefills from Open-Meteo and stays fully editable. Media commits next to
  the post in a `<slug>/` folder under `photos:`/`videos:` frontmatter keys.
- **Posts** — lists published posts fetched from the repo, grouped by trip;
  tap one to edit. Editing keeps the slug (and existing media — new picks are
  appended), regenerates the frontmatter, and republishes to the same path.
  Posts with frontmatter keys the app doesn't know are listed but refuse to
  open, so nothing gets silently dropped.
- **Trip stats** — edits the active trip's stats inside trips.json,
  edit/add/remove rows, queue the update.

## Not built yet

- Markdown preview in the editor
- Deleting already-published posts
- Managing target points (`src/data/targets.json`)
- Draft persistence across process death (don't write novels with the app
  in the background)
