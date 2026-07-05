// @ts-check
import { defineConfig } from 'astro/config';
import vue from '@astrojs/vue';

export default defineConfig({
  // TODO: set the real domain before going live — used for canonical URLs and OG tags
  site: 'https://fri.sjomli.is',
  integrations: [vue()],
});
