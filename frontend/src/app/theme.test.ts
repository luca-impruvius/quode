import { afterEach, describe, expect, it, vi } from 'vitest';
import { applyTheme, readTheme, saveTheme, THEME_KEY } from './theme';

afterEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
  vi.restoreAllMocks();
});

describe('theme', () => {
  it('defaults to System', () => {
    expect(readTheme()).toBe('system');
  });

  it('ignores unknown stored values', () => {
    localStorage.setItem(THEME_KEY, 'purple');

    expect(readTheme()).toBe('system');
  });

  it('sets data-theme for Light and Dark and removes it for System', () => {
    applyTheme('light');
    expect(document.documentElement.dataset.theme).toBe('light');

    applyTheme('system');
    expect(document.documentElement.dataset.theme).toBeUndefined();
  });

  it('still works when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });

    expect(readTheme()).toBe('system');
    expect(() => saveTheme('dark')).not.toThrow();
  });
});
