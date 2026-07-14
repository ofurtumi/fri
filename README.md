# Iceland by Camper

Travel blog for a camper-van trip around Iceland, live at
[fri.sjomli.is](https://fri.sjomli.is). Static site built with
[Astro](https://astro.build), with one interactive SVG map island written in
Vue. See `project-plan.md` for the design and current status.

## Repo layout

| Path       | What it is                                                        |
| ---------- | ----------------------------------------------------------------- |
| `src/`     | The Astro site (content, map component, styles)                   |
| `scripts/` | `build-map.mjs` (map geometry) and `fetch-weather.mjs` (weather)  |
| `android/` | Companion Android app for publishing from the road — own README   |

## Prerequisites

- Node 20+ and npm (site + scripts)
- `npm install` after cloning
- The Android app needs Android Studio — see `android/README.md`

## Commands

| Command           | What it does                                            |
| ----------------- | ------------------------------------------------------- |
| `npm run dev`     | Dev server at `localhost:4321`, live-reloads            |
| `npm run build`   | Static production build into `dist/`                    |
| `npm run preview` | Serve the production build locally                      |
| `npm run weather` | Fill in missing `weather` frontmatter (see below)       |

## Posting from the road

1. Add a markdown file to `src/content/posts/`, e.g. `day-5-egilsstadir.md`:

   ```markdown
   ---
   title: 'Day 5: Over the fjord heaths'
   date: 2026-07-05
   location: Egilsstaðir
   lat: 65.2669
   lng: -14.3948
   excerpt: One line used for link previews and map tooltips.
   ---

   The post body, plain markdown.
   ```

   `lat`/`lng` — copy from a long-press in Google Maps. The frontmatter is
   schema-checked at build time (`src/content.config.ts`), so mistakes fail
   loudly instead of rendering wrong.

   To attach photos, drop them in a sibling folder named after the post's
   slug and list them under a `photos:` key:

   ```markdown
   ---
   ...
   photos:
     - ./day-5-egilsstadir/1.jpg
     - ./day-5-egilsstadir/2.jpg
   ---
   ```

   (`src/content/posts/day-5-egilsstadir/1.jpg`, etc.) They're resolved
   through `astro:assets`, so they're optimized at build time — no manual
   resizing needed beyond what the Android app already does. One photo
   renders as a single image; more than one renders as a clickable,
   looping carousel on the home feed and `/gallery`, and as a full grid on
   the post's own page.

   Short videos work the same way under a `videos:` key (`./day-5/clip-1.mp4`
   in the same folder). Keep them tiny — the app transcodes to ~270p; from a
   laptop do the equivalent with
   `ffmpeg -i in.mp4 -vf scale=-2:270 -c:v libx264 -c:a aac clip-1.mp4`.
   A `trip:` key ties the post to a trip in `src/data/trips.json` (posts
   without one are date-bucketed into the nearest trip).

2. `npm run weather` — fetches that day's weather from Open-Meteo by date +
   coordinates and freezes it into the frontmatter. Posts that already have a
   `weather:` block are never touched, so to override a nonsense value just
   write the block yourself (or edit what the script wrote).

3. Update the trip's `stats` in `src/data/trips.json` (the key-value panel)
   if the numbers moved.

4. Commit and push — the host rebuilds and deploys.

Planned-but-not-yet-visited places live in `src/data/targets.json` (hollow
dashed circles on the map). Delete them as they become real stops.

## The map

`src/components/IcelandMap.vue` renders `src/data/iceland-map.json`: a
simplified Natural Earth coastline plus the projection constants used to place
markers. Regenerate it (e.g. to change detail level via `TOLERANCE`):

```sh
curl -L -o /tmp/countries-10m.json https://cdn.jsdelivr.net/npm/world-atlas@2/countries-10m.json
node scripts/build-map.mjs /tmp/countries-10m.json
```

Map behaviour: tap a stop to zoom to its region, tap again to open its post;
tap the sea (or "← whole country") to zoom out. Nearby stops cluster into a
numbered badge — zooming in splits them. The scrubber under the map moves a
day-by-day horizon: everything after the selected day grays out on the map and
hides in the feed.

## Deployment

The site is fully static: `npm run build` → `dist/`. Hosting builds and
deploys on every push to `main` — which means publishing a post is just a
push, whether from a laptop or from the Android app. Note that pushes only
touching `android/` also trigger a (harmless) site rebuild.

`/gallery` shows every post's photos as a chronological (newest-first)
timeline with date and location — it's just another static page, generated
from the same `photos:` frontmatter.

Data files the site reads: posts in `src/content/posts/`, trips (name, start
point, per-trip stats) in `src/data/trips.json`, GPS traces in
`src/data/routes/<tripId>.json` (committed by the app, drawn as the driven
route on the map), planned destinations in `src/data/targets.json`, map
geometry in `src/data/iceland-map.json` (generated, committed).

Trips: `/` shows the latest trip — its posts, stats and route; older trips
live at `/trips/<id>/` via the sidebar list (a slide-out menu on mobile).
`/gallery` stays trip-agnostic.
