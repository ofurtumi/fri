# Iceland by Camper

Travel blog for a camper-van trip around Iceland. Static site built with [Astro](https://astro.build),
with one interactive SVG map island written in Vue. See `project-plan.md` for the full design.

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

2. `npm run weather` — fetches that day's weather from Open-Meteo by date +
   coordinates and freezes it into the frontmatter. Posts that already have a
   `weather:` block are never touched, so to override a nonsense value just
   write the block yourself (or edit what the script wrote).

3. Update `src/data/stats.json` (the key-value panel) if the numbers moved.

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
