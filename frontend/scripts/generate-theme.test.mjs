// @vitest-environment node
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { generateTheme } from './generate-theme.mjs';

const tokens = JSON.parse(
  readFileSync(new URL('../../design-system/tokens.json', import.meta.url), 'utf8'),
);
const css = generateTheme(tokens);

describe('generateTheme', () => {
  it('defines every colour token for light and dark', () => {
    for (const token of tokens.color.tokens) {
      expect(css).toContain(`--qd-${token.name}: ${token.value.light};`);
      expect(css).toContain(`--qd-${token.name}: ${token.value.dark};`);
      expect(css).toContain(`--color-${token.name}: var(--qd-${token.name});`);
    }
  });

  it('switches with data-theme and follows the system setting otherwise', () => {
    expect(css).toContain("[data-theme='dark'] {");
    expect(css).toContain('@media (prefers-color-scheme: dark)');
    expect(css).toContain(":root:not([data-theme='light'])");
  });

  it('allows only design-system colours', () => {
    expect(css).toContain('--color-*: initial;');
  });

  it('maps type, spacing, radius and shadow tokens into the Tailwind theme', () => {
    expect(css).toContain('--text-question: 21px;');
    expect(css).toContain('--text-question--line-height: 28px;');
    expect(css).toContain('--text-label--letter-spacing: 0.08em;');
    expect(css).toContain('--font-display: "Bricolage Grotesque"');
    expect(css).toContain('--spacing-space-4: 16px;');
    expect(css).toContain('--radius-pill: 999px;');
    expect(css).toContain('--shadow-pop: var(--qd-shadow-pop);');
  });
});
