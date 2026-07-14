import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

const posts = defineCollection({
  loader: glob({ pattern: '**/*.md', base: './src/content/posts' }),
  schema: ({ image }) =>
    z.object({
      title: z.string(),
      date: z.coerce.date(),
      // Trip id from src/data/trips.json; older posts without one are
      // date-bucketed into a trip by src/lib/trips.ts
      trip: z.string().optional(),
      location: z.string(),
      lat: z.number(),
      lng: z.number(),
      // Written at publish time (Open-Meteo + manual override), never fetched client-side
      weather: z
        .object({
          temp: z.number(),
          description: z.string(),
          windKmh: z.number().optional(),
        })
        .optional(),
      excerpt: z.string().optional(),
      // Relative paths into the post's sibling `<slug>/` folder, e.g. `./day-1-reykjavik/1.jpg`
      photos: z.array(image()).optional(),
      // Same folder, e.g. `./day-1-reykjavik/clip-1.mp4` — plain strings because
      // image() rejects non-images; resolved to URLs in src/lib/media.ts
      videos: z.array(z.string().regex(/\.mp4$/)).optional(),
    }),
});

export const collections = { posts };
