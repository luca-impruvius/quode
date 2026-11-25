import { useSyncExternalStore } from 'react';

/**
 * The desktop layout starts at 1024 px wide (docs/specs/M2-exam-prep-mvp.md). Written in rem like
 * Tailwind's `lg` (64rem), so code and CSS switch at the same width even with a larger font.
 */
export const DESKTOP = '(min-width: 64rem)';

function subscribe(onChange: () => void) {
  if (typeof window.matchMedia !== 'function') {
    return () => undefined;
  }
  const query = window.matchMedia(DESKTOP);
  query.addEventListener('change', onChange);
  return () => query.removeEventListener('change', onChange);
}

function isDesktop() {
  return typeof window.matchMedia === 'function' && window.matchMedia(DESKTOP).matches;
}

/** Whether the window is desktop-wide; follows resizes. Without matchMedia (tests) it's the phone. */
export function useDesktop(): boolean {
  return useSyncExternalStore(subscribe, isDesktop, () => false);
}
