// Generates src/styles/theme.generated.css from design-system/tokens.json, the single source of
// the design tokens. Runs before dev, build and test; the output is git-ignored, never hand-edited.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const tokensPath = resolve(here, '../../design-system/tokens.json');
const outPath = resolve(here, '../src/styles/theme.generated.css');

export function generateTheme(tokens) {
  // Theme-dependent values become --qd-* variables that switch with the theme; Tailwind's
  // @theme then points at them. The prefix avoids self-references such as --shadow-pop: var(--shadow-pop).
  const themed = [
    ...tokens.color.tokens.map((t) => ({ name: t.name, value: t.value })),
    ...tokens.shadow.tokens.map((t) => ({ name: t.name, value: t.value })),
  ];
  const vars = (theme) => themed.map((t) => `  --qd-${t.name}: ${t.value[theme]};`).join('\n');

  const theme = [
    // Only design-system colours exist: Tailwind's default palette is switched off.
    '  --color-*: initial;',
    ...tokens.color.tokens.map((t) => `  --color-${t.name}: var(--qd-${t.name});`),
    ...tokens.shadow.tokens.map(
      (t) => `  --shadow-${t.name.replace(/^shadow-/, '')}: var(--qd-${t.name});`,
    ),
    ...Object.entries(tokens.type.families).map(([name, value]) => `  --font-${name}: ${value};`),
    ...tokens.type.groups.flatMap((group) =>
      group.styles.flatMap((s) => [
        `  --text-${s.name}: ${s.fontSize};`,
        `  --text-${s.name}--line-height: ${s.lineHeight};`,
        `  --text-${s.name}--font-weight: ${s.fontWeight};`,
        ...(s.letterSpacing ? [`  --text-${s.name}--letter-spacing: ${s.letterSpacing};`] : []),
      ]),
    ),
    ...tokens.spacing.tokens.map((t) => `  --spacing-${t.name}: ${t.value};`),
    ...tokens.radius.tokens.map((t) => `  --radius-${t.name.replace(/^radius-/, '')}: ${t.value};`),
  ];
  const borders = tokens.borderWidth.tokens.map((t) => `  --${t.name}: ${t.value};`).join('\n');

  return `/* Generated from design-system/tokens.json by scripts/generate-theme.mjs. Do not edit. */
:root,
[data-theme='light'] {
${vars('light')}
}

[data-theme='dark'] {
${vars('dark')}
}

/* No explicit choice: follow the system setting. */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme='light']) {
${vars('dark').replace(/^/gm, '  ')}
  }
}

:root {
${borders}
}

@theme inline {
${theme.join('\n')}
}
`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const tokens = JSON.parse(readFileSync(tokensPath, 'utf8'));
  mkdirSync(dirname(outPath), { recursive: true });
  writeFileSync(outPath, generateTheme(tokens));
  console.log(`theme: ${outPath}`);
}
