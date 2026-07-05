# Iceland Camper Van Blog — Project Plan

A blog tracking a summer camper-van trip around Iceland (~2 weeks, one author), built as a **static site with one rich interactive map**, plus a companion Android app for publishing from the road. The site/app combo is meant to be reusable for future trips.

## Architecture

**Static site, no backend.** All content lives as files in a GitHub repo; publishing = git push.

- **Framework: Astro + Vue integration.** Markdown content collections, build-time image optimization, near-zero JS shipped — except the map, which is an interactive island written in Vue.
- **Hosting:** Cloudflare Pages or Netlify (free tier), auto-deploy on push.
- **Posts:** markdown files with frontmatter (`date`, `title`, `lat`, `lng`, `weather`, ...) shown in chronological order. Each post gets a real URL with OG tags so social shares render proper previews (main audience is on phones via social media — mobile first).
- **Stats:** a simple CSV/JSON file in the repo (fish caught, van breakdowns, general mood, ...), rendered as a key-value panel near the map.
- **Route data:** a JSON file of stops + logged GPS points.

## The map (flagship feature)

Stylized SVG of Iceland — Indiana Jones map vibe, not a tile map. Iceland is roughly 4:3, so the map occupies about the top half of a phone screen.

- **Zoom = animating the SVG viewBox.** Default view is always the whole country. Zooming tweens the viewBox to a padded bounding box around a group of points. The same mechanism is reused to frame day pages.
- **Clustering:** points closer together than a screen-space threshold collapse into a single cluster marker at country view; zoomed in, they all render individually.
- **Interaction (touch-first):** first tap on a cluster/point zooms to that region; a tap on an individual point navigates/scrolls to its post. Desktop additionally gets hover previews.
- **Route:** straight lines from stop to stop for v1 — if it looks bad we improve it later. The app logs GPS points regularly, so the data to upgrade to real driven routes will exist.
- **Target points:** purely visual markers of planned destinations. Location only — no dates, no computed "plan adherence."

## Time scrubber

- Day granularity; ~14-day trip means a discrete 14-tick control, not a continuous slider.
- **Horizon semantics:** selecting a day shows all posts and map points **on or before** that day. The route is drawn up to that day; the latest/current position beyond it still renders, grayed out.
- The scrubber filters the chronological post list the same way.

## Weather metadata

- Pulled from Open-Meteo's historical API (free, keyless) using post date + lat/lng.
- Fetched once at publish time and frozen into the post's frontmatter — never fetched client-side.
- Manual override before storing, for when the daily summary doesn't match reality (it's Iceland).

## Companion Android app (phase 2)

A small native Android app as the publishing tool from the road, handling the "complicated" parts:

- Create/edit posts: markdown text area with preview.
- Image handling: resize/compress photos before committing (nobody on mobile data gets a 4MB hero image).
- GPS logging: record position at regular intervals into the route data file.
- Weather: fetch the API value at post time, allow manual override, write into frontmatter.
- Stats: view and update the stats file.
- Publish: commit + push to GitHub, which triggers the site deploy.

Until the app exists, the manual fallback works fine: edit markdown and upload photos via GitHub's web UI or a laptop, push whenever there's signal.

## Build order

1. Astro site skeleton: post collection, chronological feed, per-post pages with OG tags.
2. SVG map: stops, straight-line route, viewBox zoom, clustering, target points.
3. Time scrubber wired to both the map and the post list.
4. Stats panel + weather-at-build pipeline.
5. Android companion app.
