import map from '../data/iceland-map.json';

/** Project lat/lng into the SVG coordinate space of iceland-map.json */
export function project(lat: number, lng: number): { x: number; y: number } {
  const { lngMin, latMax, k, scale } = map.proj;
  return { x: (lng - lngMin) * k * scale, y: (latMax - lat) * scale };
}

export const mapSize = { width: map.width, height: map.height };
export const mapPath = map.path;
