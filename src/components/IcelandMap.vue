<script setup lang="ts">
import { computed, reactive, onBeforeUnmount, ref, watchEffect } from "vue";
import { project, mapSize, mapPath } from "../lib/geo";

export interface MapStop {
  id: string;
  title: string;
  date: string; // ISO
  location: string;
  lat: number;
  lng: number;
  excerpt?: string;
}

export interface MapTarget {
  name: string;
  lat: number;
  lng: number;
}

export interface RoutePoint {
  lat: number;
  lng: number;
  t: number; // epoch seconds
}

export interface TripStart {
  name: string;
  lat: number;
  lng: number;
}

const props = defineProps<{
  stops: MapStop[]; // chronological order
  targets: MapTarget[];
  route?: RoutePoint[]; // logged GPS trace, chronological
  start?: TripStart;
  startDate?: string; // YYYY-MM-DD
}>();

const FULL = { x: 0, y: 0, w: mapSize.width, h: mapSize.height };
const ASPECT = FULL.w / FULL.h;

const vb = reactive({ ...FULL });
const viewBoxAttr = computed(() => `${vb.x} ${vb.y} ${vb.w} ${vb.h}`);

/** Scale factor: sizes multiplied by this stay constant on screen while zooming */
const u = computed(() => vb.w / FULL.w);
const isCountryView = computed(() => vb.w > FULL.w * 0.7);

// --- timeline (day granularity, horizon semantics) ---

const dayKey = (iso: string) => iso.slice(0, 10);
// Iceland is UTC year-round, so UTC day buckets match local days
const routeDayKey = (t: number) => dayKey(new Date(t * 1000).toISOString());

const dayKeys = computed(() => {
  const days = [
    ...props.stops.map((s) => dayKey(s.date)),
    ...(props.route ?? []).map((p) => routeDayKey(p.t)),
    ...(props.startDate ? [props.startDate] : []),
  ].sort();
  if (!days.length) return [] as string[];
  const last = days[days.length - 1];
  const out: string[] = [];
  const d = new Date(`${days[0]}T00:00:00Z`);
  let k = dayKey(d.toISOString());
  while (k <= last) {
    out.push(k);
    d.setUTCDate(d.getUTCDate() + 1);
    k = dayKey(d.toISOString());
  }
  return out;
});

const selectedIdx = ref(Math.max(0, dayKeys.value.length - 1));
const selectedKey = computed(
  () => dayKeys.value[selectedIdx.value] ?? "9999-12-31",
);
const atLatest = computed(() => selectedIdx.value >= dayKeys.value.length - 1);

const dayLabel = computed(() => {
  const k = dayKeys.value[selectedIdx.value];
  if (!k) return "";
  return new Date(`${k}T12:00:00Z`).toLocaleDateString("is-IS", {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
});

// Posts are static Astro HTML; the scrubber hides the ones past the horizon
// via their data-post-date attribute.
watchEffect(() => {
  const key = selectedKey.value;
  if (typeof document === "undefined") return; // SSR
  document.querySelectorAll<HTMLElement>("[data-post-date]").forEach((el) => {
    el.toggleAttribute("hidden", (el.dataset.postDate ?? "") > key);
  });
});

// --- projection ---

const projectedStops = computed(() =>
  props.stops.map((s) => ({ ...s, ...project(s.lat, s.lng) })),
);
const projectedTargets = computed(() =>
  props.targets.map((t) => ({ ...t, ...project(t.lat, t.lng) })),
);
const projectedRoute = computed(() =>
  (props.route ?? []).map((p) => ({ t: p.t, ...project(p.lat, p.lng) })),
);
const projectedStart = computed(() =>
  props.start
    ? { ...props.start, ...project(props.start.lat, props.start.lng) }
    : null,
);

const pastStops = computed(() =>
  projectedStops.value.filter((s) => dayKey(s.date) <= selectedKey.value),
);
const futureStops = computed(() =>
  projectedStops.value.filter((s) => dayKey(s.date) > selectedKey.value),
);

const pastRoute = computed(() =>
  pastStops.value.map((s) => `${s.x},${s.y}`).join(" "),
);
// The future leg continues from the last reached stop so the line stays connected
const futureRoute = computed(() => {
  if (!futureStops.value.length) return "";
  const last = pastStops.value[pastStops.value.length - 1];
  const pts = last ? [last, ...futureStops.value] : futureStops.value;
  return pts.length > 1 ? pts.map((s) => `${s.x},${s.y}`).join(" ") : "";
});

// --- the real GPS trace (replaces the straight lines when present) ---

const hasGps = computed(() => projectedRoute.value.length > 1);
// End of the selected day, as epoch seconds — same horizon semantics as stops
const cutoffT = computed(
  () => Date.parse(`${selectedKey.value}T23:59:59Z`) / 1000,
);

const gpsPast = computed(() => {
  const pts = projectedRoute.value.filter((p) => p.t <= cutoffT.value);
  return pts.length > 1 ? pts.map((p) => `${p.x},${p.y}`).join(" ") : "";
});
const gpsFuture = computed(() => {
  const ahead = projectedRoute.value.filter((p) => p.t > cutoffT.value);
  if (!ahead.length) return "";
  const reached = projectedRoute.value.filter((p) => p.t <= cutoffT.value);
  const last = reached[reached.length - 1];
  const pts = last ? [last, ...ahead] : ahead;
  return pts.length > 1 ? pts.map((p) => `${p.x},${p.y}`).join(" ") : "";
});

// --- clustering (screen-space: threshold shrinks as we zoom in) ---

interface Cluster {
  key: string;
  x: number;
  y: number;
  stops: (MapStop & { x: number; y: number })[];
}

function clusterize(stops: (MapStop & { x: number; y: number })[]): Cluster[] {
  const threshold = vb.w * 0.06;
  const out: Cluster[] = [];
  for (const s of stops) {
    const hit = out.find((c) => Math.hypot(c.x - s.x, c.y - s.y) < threshold);
    if (hit) {
      hit.stops.push(s);
      hit.x = hit.stops.reduce((a, p) => a + p.x, 0) / hit.stops.length;
      hit.y = hit.stops.reduce((a, p) => a + p.y, 0) / hit.stops.length;
    } else {
      out.push({ key: s.id, x: s.x, y: s.y, stops: [s] });
    }
  }
  return out;
}

// Past and future cluster separately so a cluster is never half-grayed
const pastClusters = computed(() => clusterize(pastStops.value));
const futureClusters = computed(() => clusterize(futureStops.value));

// --- viewBox tweening ---

let raf = 0;

function tweenTo(target: { x: number; y: number; w: number; h: number }) {
  cancelAnimationFrame(raf);
  const from = { x: vb.x, y: vb.y, w: vb.w, h: vb.h };
  const start = performance.now();
  const dur = 650;
  const ease = (t: number) =>
    t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  const step = (now: number) => {
    const t = Math.min(1, (now - start) / dur);
    const e = ease(t);
    vb.x = from.x + (target.x - from.x) * e;
    vb.y = from.y + (target.y - from.y) * e;
    vb.w = from.w + (target.w - from.w) * e;
    vb.h = from.h + (target.h - from.h) * e;
    if (t < 1) raf = requestAnimationFrame(step);
  };
  raf = requestAnimationFrame(step);
}

onBeforeUnmount(() => cancelAnimationFrame(raf));

/** Expand a box to the map's aspect ratio (keeps tooltip math + framing sane) */
function withAspect(box: { x: number; y: number; w: number; h: number }) {
  let { x, y, w, h } = box;
  if (w / h < ASPECT) {
    const nw = h * ASPECT;
    x -= (nw - w) / 2;
    w = nw;
  } else {
    const nh = w / ASPECT;
    y -= (nh - h) / 2;
    h = nh;
  }
  return { x, y, w, h };
}

const MIN_ZOOM_W = 240;

function zoomToStops(stops: { x: number; y: number }[]) {
  const xs = stops.map((s) => s.x);
  const ys = stops.map((s) => s.y);
  let x = Math.min(...xs);
  let y = Math.min(...ys);
  let w = Math.max(...xs) - x;
  let h = Math.max(...ys) - y;
  const pad = Math.max(w, h) * 0.35 + 40;
  x -= pad;
  y -= pad;
  w += pad * 2;
  h += pad * 2;
  if (w < MIN_ZOOM_W) {
    x -= (MIN_ZOOM_W - w) / 2;
    w = MIN_ZOOM_W;
  }
  tweenTo(withAspect({ x, y, w, h }));
}

function showAll() {
  tweenTo(FULL);
}

// --- interaction ---

function goToPost(id: string) {
  const el = document.getElementById(`post-${id}`);
  if (el && !el.hidden)
    el.scrollIntoView({ behavior: "smooth", block: "start" });
  else window.location.assign(`${window.location.pathname}#post-${id}`);
}

function onClusterClick(c: Cluster) {
  if (c.stops.length > 1) {
    zoomToStops(c.stops);
  } else if (isCountryView.value) {
    // first tap zooms to the region, second tap (below) opens the post
    zoomToStops(c.stops);
  } else {
    goToPost(c.stops[0].id);
  }
}

// Tapping a grayed (future) stop pulls the timeline forward to its day first,
// so its post becomes visible, then behaves like a normal stop tap.
function onFutureClick(c: Cluster) {
  const maxKey = c.stops.map((s) => dayKey(s.date)).sort()[c.stops.length - 1];
  const idx = dayKeys.value.indexOf(maxKey);
  if (idx > selectedIdx.value) selectedIdx.value = idx;
  onClusterClick(c);
}

function onBackgroundClick() {
  if (!isCountryView.value) showAll();
}

// --- hover previews (desktop only, hidden via CSS on touch) ---

const hovered = ref<{
  title: string;
  sub: string;
  x: number;
  y: number;
} | null>(null);

function hoverCluster(c: Cluster, future = false) {
  const first = c.stops[0];
  const when = `${formatDate(first.date)} · ${first.location}`;
  const sub =
    c.stops.length > 1
      ? `${c.stops.length} stops — tap to zoom in`
      : future
        ? `up ahead · ${when}`
        : when;
  hovered.value = {
    title: c.stops.length > 1 ? "Several stops" : first.title,
    sub,
    x: c.x,
    y: c.y,
  };
}

function hoverTarget(t: MapTarget & { x: number; y: number }) {
  hovered.value = {
    title: t.name,
    sub: "planned — not there yet",
    x: t.x,
    y: t.y,
  };
}

function hoverStart() {
  const s = projectedStart.value;
  if (!s) return;
  hovered.value = {
    title: s.name,
    sub: props.startDate ? `upphaf · ${formatDate(props.startDate)}` : "upphaf",
    x: s.x,
    y: s.y,
  };
}

function unhover() {
  hovered.value = null;
}

const tooltipStyle = computed(() => {
  if (!hovered.value) return {};
  return {
    left: `${((hovered.value.x - vb.x) / vb.w) * 100}%`,
    top: `${((hovered.value.y - vb.y) / vb.h) * 100}%`,
  };
});

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
  });
}
</script>

<template>
  <div class="map-card">
    <div class="map">
      <svg
        :viewBox="viewBoxAttr"
        xmlns="http://www.w3.org/2000/svg"
        role="img"
        aria-label="Map of Iceland showing the route and stops"
        @click.self="onBackgroundClick"
      >
        <path class="land" :d="mapPath" @click="onBackgroundClick" />

        <!-- Straight stop-to-stop lines: fallback for trips without a GPS trace -->
        <polyline
          v-if="!hasGps && futureRoute"
          class="route future"
          :points="futureRoute"
          :stroke-width="3 * u"
          :stroke-dasharray="`${9 * u} ${7 * u}`"
        />
        <polyline
          v-if="!hasGps && pastStops.length > 1"
          class="route"
          :points="pastRoute"
          :stroke-width="3 * u"
          :stroke-dasharray="`${9 * u} ${7 * u}`"
        />

        <!-- The actually driven route; solid so it reads as recorded, not inferred -->
        <polyline
          v-if="gpsFuture"
          class="route future"
          :points="gpsFuture"
          :stroke-width="2.5 * u"
        />
        <polyline
          v-if="gpsPast"
          class="route"
          :points="gpsPast"
          :stroke-width="2.5 * u"
        />

        <g
          v-if="projectedStart"
          class="start"
          @mouseenter="hoverStart"
          @mouseleave="unhover"
        >
          <circle
            class="ring"
            :cx="projectedStart.x"
            :cy="projectedStart.y"
            :r="7 * u"
            :stroke-width="2.5 * u"
          />
          <circle
            class="core"
            :cx="projectedStart.x"
            :cy="projectedStart.y"
            :r="2.5 * u"
          />
        </g>

        <g
          v-for="t in projectedTargets"
          :key="t.name"
          class="target"
          @mouseenter="hoverTarget(t)"
          @mouseleave="unhover"
        >
          <circle
            :cx="t.x"
            :cy="t.y"
            :r="10 * u"
            :stroke-width="2.5 * u"
            :stroke-dasharray="`${4 * u} ${3.5 * u}`"
          />
        </g>

        <g
          v-for="c in futureClusters"
          :key="`f-${c.key}`"
          class="stop future"
          role="button"
          tabindex="0"
          :aria-label="`Up ahead: ${c.stops.length > 1 ? `${c.stops.length} stops` : c.stops[0].title}`"
          @click="onFutureClick(c)"
          @keydown.enter="onFutureClick(c)"
          @mouseenter="hoverCluster(c, true)"
          @mouseleave="unhover"
        >
          <circle class="hit" :cx="c.x" :cy="c.y" :r="30 * u" />
          <circle
            class="dot"
            :cx="c.x"
            :cy="c.y"
            :r="(c.stops.length > 1 ? 15 : 10) * u"
            :stroke-width="3 * u"
          />
          <text
            v-if="c.stops.length > 1"
            :x="c.x"
            :y="c.y"
            :font-size="17 * u"
            text-anchor="middle"
            dominant-baseline="central"
          >
            {{ c.stops.length }}
          </text>
        </g>

        <g
          v-for="c in pastClusters"
          :key="c.key"
          class="stop"
          role="button"
          tabindex="0"
          :aria-label="
            c.stops.length > 1
              ? `${c.stops.length} stops, zoom in`
              : c.stops[0].title
          "
          @click="onClusterClick(c)"
          @keydown.enter="onClusterClick(c)"
          @mouseenter="hoverCluster(c)"
          @mouseleave="unhover"
        >
          <circle class="hit" :cx="c.x" :cy="c.y" :r="30 * u" />
          <circle
            class="dot"
            :cx="c.x"
            :cy="c.y"
            :r="(c.stops.length > 1 ? 15 : 10) * u"
            :stroke-width="3 * u"
          />
          <text
            v-if="c.stops.length > 1"
            :x="c.x"
            :y="c.y"
            :font-size="17 * u"
            text-anchor="middle"
            dominant-baseline="central"
          >
            {{ c.stops.length }}
          </text>
        </g>
      </svg>

      <div v-if="hovered" class="tooltip" :style="tooltipStyle">
        <strong>{{ hovered.title }}</strong>
        <span>{{ hovered.sub }}</span>
      </div>

      <button
        v-if="!isCountryView"
        class="show-all"
        type="button"
        @click="showAll"
      >
        ← Aftur í yfirlit
      </button>
    </div>

    <div v-if="dayKeys.length > 1" class="scrubber">
      <input
        v-model.number="selectedIdx"
        type="range"
        min="0"
        :max="dayKeys.length - 1"
        step="1"
        :aria-label="`Timeline, day ${selectedIdx + 1}: ${dayLabel}`"
      />
      <div class="scrub-info">
        <span>Dagur {{ selectedIdx + 1 }} · {{ dayLabel }}</span>
        <button
          v-if="!atLatest"
          type="button"
          @click="selectedIdx = dayKeys.length - 1"
        >
          skip to latest →
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.map-card {
  border: 1px solid var(--line);
  border-radius: 0.75rem;
  background: #f5efe0;
  overflow: hidden;
  margin: 1rem 0;
}

.map {
  position: relative;
}

svg {
  display: block;
  width: 100%;
  height: auto;
}

.land {
  fill: #ebdfc3;
  stroke: #a5947a;
  stroke-width: 1.5;
  vector-effect: non-scaling-stroke;
}

.route {
  fill: none;
  stroke: red;
  stroke-linecap: round;
  stroke-linejoin: round;
  pointer-events: none;
}

.route.future {
  stroke: #b9ab92;
}

.stop {
  cursor: pointer;
}

.stop:focus-visible .dot {
  stroke: var(--ink);
}

.stop .hit {
  fill: transparent;
}

.stop .dot {
  fill: red;
  stroke: #faf6ef;
}

.stop.future .dot {
  fill: #b9ab92;
}

.stop text {
  fill: #faf6ef;
  font-weight: 700;
  pointer-events: none;
  user-select: none;
}

.target circle {
  fill: none;
  stroke: #a5947a;
}

.start .ring {
  fill: none;
  stroke: var(--ink, #3b3428);
}

.start .core {
  fill: var(--ink, #3b3428);
}

.tooltip {
  position: absolute;
  transform: translate(-50%, -130%);
  background: var(--ink);
  color: var(--paper);
  border-radius: 0.4rem;
  padding: 0.35rem 0.6rem;
  font-size: 0.8rem;
  line-height: 1.35;
  pointer-events: none;
  white-space: nowrap;
  display: none;
}

.tooltip strong {
  display: block;
}

@media (hover: hover) {
  .tooltip {
    display: block;
  }
}

.show-all {
  position: absolute;
  top: 0.6rem;
  right: 0.6rem;
  border: 1px solid var(--line);
  background: var(--card);
  color: var(--ink);
  border-radius: 999px;
  padding: 0.3rem 0.8rem;
  font-size: 0.8rem;
  cursor: pointer;
}

.scrubber {
  border-top: 1px solid var(--line);
  background: var(--card);
  padding: 0.6rem 0.9rem 0.7rem;
}

.scrubber input[type="range"] {
  width: 100%;
  margin: 0;
}

.scrub-info {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 0.85rem;
  color: var(--ink-soft);
}

.scrub-info button {
  border: none;
  background: none;
  font-size: 0.85rem;
  cursor: pointer;
  padding: 0;
}
</style>
