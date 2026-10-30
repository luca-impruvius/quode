// @vitest-environment node
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { generateTheme } from './generate-theme.mjs';

const tokens = JSON.parse(
  readFileSync(new URL('../../design-system/tokens.json', import.meta.url), 'utf8'),
);
const css = generateTheme(tokens);

describe('generateTheme', () => {
  it('maps every token of every category', () => {
    for (const t of tokens.color.tokens) {
      expect(css).toContain(`--qd-${t.name}: ${t.value.light};`);
      expect(css).toContain(`--qd-${t.name}: ${t.value.dark};`);
      expect(css).toContain(`--color-${t.name}: var(--qd-${t.name});`);
    }
    for (const t of tokens.shadow.tokens) {
      expect(css).toContain(`--qd-${t.name}: ${t.value.light};`);
      expect(css).toContain(`--qd-${t.name}: ${t.value.dark};`);
    }
    for (const t of tokens.spacing.tokens) {
      expect(css).toContain(`--spacing-${t.name}: ${t.value};`);
    }
    for (const t of tokens.radius.tokens) {
      expect(css).toContain(`--radius-${t.name.replace('radius-', '')}: ${t.value};`);
    }
    for (const t of tokens.borderWidth.tokens) {
      expect(css).toContain(
        `@utility ${t.name} {\n  border-style: solid;\n  border-width: ${t.value};`,
      );
    }
    for (const name of Object.keys(tokens.type.families)) {
      expect(css).toContain(`--font-${name}: ${tokens.type.families[name]};`);
    }
    for (const group of tokens.type.groups) {
      for (const s of group.styles) {
        expect(css).toContain(
          `@utility type-${s.name} {\n  font-family: var(--font-${group.family});\n  font-size: ${s.fontSize};`,
        );
      }
    }
  });

  it('puts the family into each type style', () => {
    expect(css).toContain('@utility type-title {\n  font-family: var(--font-display);');
    expect(css).toContain('@utility type-code {\n  font-family: var(--font-mono);');
    expect(css).toContain('letter-spacing: 0.08em;');
  });

  it('switches with data-theme and follows the system setting otherwise', () => {
    expect(css).toContain("[data-theme='dark'] {\n  color-scheme: dark;");
    expect(css).toContain("[data-theme='light'] {\n  color-scheme: light;");
    expect(css).toContain(":root:not([data-theme='light']) {\n    color-scheme: dark;");
    expect(css).toContain('@custom-variant dark {');
  });

  it('allows only design-system values', () => {
    for (const reset of [
      'color',
      'shadow',
      'inset-shadow',
      'drop-shadow',
      'text-shadow',
      'radius',
      'text',
      'font',
    ]) {
      expect(css).toContain(`--${reset}-*: initial;`);
    }
  });

  it('fails on a token without a dark value', () => {
    const broken = structuredClone(tokens);
    broken.color.tokens[0].value = { light: '#ffffff' };

    expect(() => generateTheme(broken)).toThrow(/no dark value/);
  });

  it('fails on a type group with an unknown family', () => {
    const broken = structuredClone(tokens);
    broken.type.groups[0].family = 'nope';

    expect(() => generateTheme(broken)).toThrow(/unknown family/);
  });

  it('fails on a token category it does not map', () => {
    expect(() => generateTheme({ ...tokens, motion: { tokens: [] } })).toThrow(/motion/);
  });
});
