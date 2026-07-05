// Fills in missing `weather` frontmatter on posts from Open-Meteo (free, keyless),
// using each post's date + lat/lng. Posts that already have a weather block are
// left alone — a manual override always wins. Run via: npm run weather
//
// Values are frozen into the markdown so the site never fetches weather at
// build or view time. Commit the result.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const POSTS_DIR = fileURLToPath(new URL('../src/content/posts/', import.meta.url));

// WMO weather codes → short human description
function describe(code) {
  if (code == null) return 'unknown';
  if (code === 0) return 'clear';
  if (code <= 2) return 'partly cloudy';
  if (code === 3) return 'overcast';
  if (code === 45 || code === 48) return 'fog';
  if (code <= 57) return 'drizzle';
  if (code <= 67) return 'rain';
  if (code <= 77) return 'snow';
  if (code <= 82) return 'rain showers';
  if (code <= 86) return 'snow showers';
  return 'thunderstorm';
}

async function fetchWeather(date, lat, lng) {
  const daily = 'temperature_2m_mean,weather_code,wind_speed_10m_max';
  const params = `latitude=${lat}&longitude=${lng}&start_date=${date}&end_date=${date}&daily=${daily}&timezone=auto`;
  // The archive lags ~5 days behind; the forecast API covers the recent past.
  const urls = [
    `https://archive-api.open-meteo.com/v1/archive?${params}`,
    `https://api.open-meteo.com/v1/forecast?${params}`,
  ];
  for (const url of urls) {
    try {
      const res = await fetch(url);
      if (!res.ok) continue;
      const { daily: d } = await res.json();
      if (d?.temperature_2m_mean?.[0] != null) {
        return {
          temp: Math.round(d.temperature_2m_mean[0]),
          description: describe(d.weather_code?.[0]),
          windKmh: Math.round(d.wind_speed_10m_max?.[0] ?? 0),
        };
      }
    } catch {
      // try the next source
    }
  }
  return null;
}

const files = readdirSync(POSTS_DIR).filter((f) => f.endsWith('.md'));
let updated = 0;

for (const file of files) {
  const path = POSTS_DIR + file;
  const raw = readFileSync(path, 'utf8');
  const fm = raw.match(/^---\n([\s\S]*?)\n---/);
  if (!fm) {
    console.warn(`${file}: no frontmatter, skipped`);
    continue;
  }
  if (/^weather:/m.test(fm[1])) {
    console.log(`${file}: weather already set, kept`);
    continue;
  }
  const date = fm[1].match(/^date:\s*['"]?(\d{4}-\d{2}-\d{2})/m)?.[1];
  const lat = fm[1].match(/^lat:\s*(-?[\d.]+)/m)?.[1];
  const lng = fm[1].match(/^lng:\s*(-?[\d.]+)/m)?.[1];
  if (!date || !lat || !lng) {
    console.warn(`${file}: missing date/lat/lng, skipped`);
    continue;
  }

  const w = await fetchWeather(date, lat, lng);
  if (!w) {
    console.warn(`${file}: no weather data available for ${date}, skipped`);
    continue;
  }

  const block = `weather:\n  temp: ${w.temp}\n  description: ${w.description}\n  windKmh: ${w.windKmh}`;
  writeFileSync(path, raw.replace(fm[0], `---\n${fm[1]}\n${block}\n---`));
  console.log(`${file}: ${w.temp}°C, ${w.description}, wind ${w.windKmh} km/h`);
  updated++;
}

console.log(`done — ${updated} of ${files.length} post(s) updated`);
