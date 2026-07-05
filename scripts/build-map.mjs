// Generates src/data/iceland-map.json from Natural Earth 10m data.
// Usage: node scripts/build-map.mjs <path-to-countries-10m.json>
// The output stores the projection constants alongside the path so the
// map component projects marker lat/lng with the same math.
import { readFileSync, writeFileSync } from 'node:fs';
import { feature } from 'topojson-client';

const src = process.argv[2];
if (!src) {
  console.error('usage: node scripts/build-map.mjs <countries-10m.json>');
  process.exit(1);
}

const topo = JSON.parse(readFileSync(src, 'utf8'));
const countries = feature(topo, topo.objects.countries);
const iceland = countries.features.find((f) => f.properties.name === 'Iceland');
if (!iceland) throw new Error('Iceland not found in dataset');

const rings =
  iceland.geometry.type === 'Polygon'
    ? iceland.geometry.coordinates
    : iceland.geometry.coordinates.flat();

// Ramer–Douglas–Peucker, tolerance in degrees
function rdp(points, tol) {
  if (points.length < 3) return points;
  const [a, b] = [points[0], points[points.length - 1]];
  let maxDist = 0;
  let maxIdx = 0;
  for (let i = 1; i < points.length - 1; i++) {
    const p = points[i];
    const dx = b[0] - a[0];
    const dy = b[1] - a[1];
    const len = Math.hypot(dx, dy) || 1e-12;
    const dist = Math.abs(dy * p[0] - dx * p[1] + b[0] * a[1] - b[1] * a[0]) / len;
    if (dist > maxDist) {
      maxDist = dist;
      maxIdx = i;
    }
  }
  if (maxDist <= tol) return [a, b];
  return [...rdp(points.slice(0, maxIdx + 1), tol).slice(0, -1), ...rdp(points.slice(maxIdx), tol)];
}

// Rings are closed (first point === last), which degenerates RDP's anchor
// line — simplify as two open halves and rejoin.
function simplifyRing(ring, tol) {
  const mid = Math.floor(ring.length / 2);
  const a = rdp(ring.slice(0, mid + 1), tol);
  const b = rdp(ring.slice(mid), tol);
  return [...a.slice(0, -1), ...b.slice(0, -1)];
}

const TOLERANCE = 0.008;
const MIN_RING_POINTS = 4; // drop islets that simplify to specks
const simplified = rings
  .map((r) => simplifyRing(r, TOLERANCE))
  .filter((r) => r.length >= MIN_RING_POINTS);

// Bounds over the kept rings, padded so coastal markers don't clip
let lngMin = Infinity;
let lngMax = -Infinity;
let latMin = Infinity;
let latMax = -Infinity;
for (const [lng, lat] of simplified.flat()) {
  lngMin = Math.min(lngMin, lng);
  lngMax = Math.max(lngMax, lng);
  latMin = Math.min(latMin, lat);
  latMax = Math.max(latMax, lat);
}
const padLng = (lngMax - lngMin) * 0.03;
const padLat = (latMax - latMin) * 0.03;
lngMin -= padLng;
lngMax += padLng;
latMin -= padLat;
latMax += padLat;

// Equirectangular with cos(midLat) horizontal correction — plenty for one island
const k = Math.cos((((latMin + latMax) / 2) * Math.PI) / 180);
const WIDTH = 1000;
const scale = WIDTH / ((lngMax - lngMin) * k);
const height = Math.round((latMax - latMin) * scale);

const px = (lng) => (lng - lngMin) * k * scale;
const py = (lat) => (latMax - lat) * scale;

const path = simplified
  .map(
    (ring) =>
      'M' + ring.map(([lng, lat]) => `${px(lng).toFixed(1)},${py(lat).toFixed(1)}`).join('L') + 'Z',
  )
  .join('');

const out = {
  width: WIDTH,
  height,
  path,
  proj: { lngMin, latMax, k, scale },
};

writeFileSync(new URL('../src/data/iceland-map.json', import.meta.url), JSON.stringify(out));
console.log(
  `rings: ${simplified.length}, points: ${simplified.flat().length}, viewBox: 0 0 ${WIDTH} ${height}, path: ${(path.length / 1024).toFixed(1)}kB`,
);
