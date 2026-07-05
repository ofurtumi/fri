import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

const posts = defineCollection({
  loader: glob({ pattern: '**/*.md', base: './src/content/posts' }),
  schema: z.object({
    title: z.string(),
    date: z.coerce.date(),
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
  }),
});

export const collections = { posts };
