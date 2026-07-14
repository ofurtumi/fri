import type { ImageMetadata } from 'astro';
import type { CollectionEntry } from 'astro:content';

export type MediaItem =
  | { kind: 'image'; src: ImageMetadata }
  | { kind: 'video'; src: string };

// Videos sit in the same per-post folder as photos but can't go through
// astro:assets image(), so they're resolved to emitted asset URLs here
const videoUrls = import.meta.glob('/src/content/posts/**/*.mp4', {
  query: '?url',
  import: 'default',
  eager: true,
}) as Record<string, string>;

function resolveVideo(post: CollectionEntry<'posts'>, rel: string): string {
  const filePath = post.filePath;
  if (!filePath) throw new Error(`Post "${post.id}" has no filePath`);
  // filePath is project-root-relative (src/content/posts/<slug>.md)
  const dir = filePath.replace(/^\/?/, '/').replace(/\/[^/]+$/, '');
  const key = `${dir}/${rel.replace(/^\.\//, '')}`;
  const url = videoUrls[key];
  if (!url)
    throw new Error(`Post "${post.id}" lists video "${rel}" but ${key} does not exist`);
  return url;
}

/** All of a post's media in display order: photos first, then videos. */
export function mediaFor(post: CollectionEntry<'posts'>): MediaItem[] {
  const photos = (post.data.photos ?? []).map(
    (src): MediaItem => ({ kind: 'image', src }),
  );
  const videos = (post.data.videos ?? []).map(
    (rel): MediaItem => ({ kind: 'video', src: resolveVideo(post, rel) }),
  );
  return [...photos, ...videos];
}
