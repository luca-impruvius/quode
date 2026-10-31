// Theme choice from Me: System (follow the device), Light or Dark. Stored per browser only.
// index.html applies the stored value before first paint with the same key; keep both in sync.
export const THEME_KEY = 'quode.theme';

export type Theme = 'system' | 'light' | 'dark';

export function readTheme(): Theme {
  try {
    const stored = localStorage.getItem(THEME_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    // Storage blocked (private mode, settings): fall back to the device setting.
    return 'system';
  }
}

export function saveTheme(theme: Theme): void {
  try {
    if (theme === 'system') {
      localStorage.removeItem(THEME_KEY);
    } else {
      localStorage.setItem(THEME_KEY, theme);
    }
  } catch {
    // Not remembered, but still applied for this visit.
  }
}

/** The generated theme switches on data-theme; without it, it follows prefers-color-scheme. */
export function applyTheme(theme: Theme): void {
  if (theme === 'system') {
    delete document.documentElement.dataset.theme;
  } else {
    document.documentElement.dataset.theme = theme;
  }
}
