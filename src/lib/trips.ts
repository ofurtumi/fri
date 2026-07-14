import tripsData from '../data/trips.json';

export interface Trip {
  id: string;
  name: string;
  start: { name: string; lat: number; lng: number };
  startDate: string; // YYYY-MM-DD
  stats: { label: string; value: string }[];
}

export interface RoutePoint {
  lat: number;
  lng: number;
  t: number; // epoch seconds
}

export const trips: Trip[] = [...(tripsData as Trip[])].sort((a, b) =>
  a.startDate.localeCompare(b.startDate),
);

if (!trips.length) throw new Error('src/data/trips.json has no trips');

export function latestTrip(): Trip {
  return trips[trips.length - 1];
}

export function getTrip(id: string): Trip | undefined {
  return trips.find((t) => t.id === id);
}

// The GPS trace the app publishes; a trip without one falls back to
// straight stop-to-stop lines on the map
const routes = import.meta.glob('/src/data/routes/*.json', {
  import: 'default',
  eager: true,
}) as Record<string, RoutePoint[]>;

export function routeFor(tripId: string): RoutePoint[] | undefined {
  return routes[`/src/data/routes/${tripId}.json`];
}

/** Trip a post belongs to: explicit frontmatter wins, else the last trip started on or before the post date. */
export function tripIdFor(post: { id: string; data: { trip?: string; date: Date } }): string {
  const explicit = post.data.trip;
  if (explicit) {
    if (!getTrip(explicit))
      throw new Error(`Post "${post.id}" references unknown trip "${explicit}" — add it to src/data/trips.json`);
    return explicit;
  }
  const day = post.data.date.toISOString().slice(0, 10);
  const bucket = [...trips].reverse().find((t) => t.startDate <= day);
  return (bucket ?? trips[0]).id;
}
