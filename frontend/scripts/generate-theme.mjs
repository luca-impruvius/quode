// Generates src/styles/theme.generated.css from design-system/tokens.json, the single source of
// the design tokens. Runs before dev, build and test; the output is git-ignored, never hand-edited.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const tokensPath = resolve(here, '../../design-system/tokens.json');
const outPath = resolve(here, '../src/styles/theme.generated.css');

// Every top-level key of tokens.json must be handled here; a new category fails loudly.
const KNOWN_KEYS = [
  'name',
  'version',
  'color',
  'type',
  'spacing',
  'radius',
  'shadow',
  'borderWidth',
];
const THEMES = ['light', 'dark'];

export function validateTokens(tokens) {
  const unknown = Object.keys(tokens).filter((key) => !KNOWN_KEYS.includes(key));
  if (unknown.length > 0) {
    throw new Error(`tokens.json has categories the theme generator doesn't map: ${unknown}`);
  }
  for (const token of [...tokens.color.tokens, ...tokens.shadow.tokens]) {
    for (const theme of THEMES) {
      if (typeof token.value?.[theme] !== 'string') {
        throw new Error(`Token ${token.name} has no ${theme} value`);
      }
    }
  }
  for (const group of tokens.type.groups) {
    if (!(group.family in tokens.type.families)) {
      throw new Error(`Type group ${group.name} uses unknown family ${group.family}`);
    }
  }
}

export function generateTheme(tokens) {
  validateTokens(tokens);

  // Theme-dependent values become --qd-* variables that switch with the theme; Tailwind's @theme
  // then points at them. The prefix avoids self-references such as --shadow-pop: var(--shadow-pop).
  const themed = [...tokens.color.tokens, ...tokens.shadow.tokens];
  const vars = (theme, indent = '  ') =>
    [
      `${indent}color-scheme: ${theme};`,
      ...themed.map((t) => `${indent}--qd-${t.name}: ${t.value[theme]};`),
    ].join('\n');

  const theme = [
    // Only design-system values exist: Tailwind's default palette, shadows, radii, type sizes and
    // font families are switched off. (Spacing keeps Tailwind's scale for layout sizes.)
    '  --color-*: initial;',
    '  --shadow-*: initial;',
    '  --inset-shadow-*: initial;',
    '  --drop-shadow-*: initial;',
    '  --text-shadow-*: initial;',
    '  --radius-*: initial;',
    '  --text-*: initial;',
    '  --font-*: initial;',
    ...tokens.color.tokens.map((t) => `  --color-${t.name}: var(--qd-${t.name});`),
    ...tokens.shadow.tokens.map(
      (t) => `  --shadow-${t.name.replace(/^shadow-/, '')}: var(--qd-${t.name});`,
    ),
    ...Object.entries(tokens.type.families).map(([name, value]) => `  --font-${name}: ${value};`),
    ...tokens.spacing.tokens.map((t) => `  --spacing-${t.name}: ${t.value};`),
    ...tokens.radius.tokens.map((t) => `  --radius-${t.name.replace(/^radius-/, '')}: ${t.value};`),
  ];

  // One utility per type style, family included: `type-title` is the whole style.
  const typeUtilities = tokens.type.groups.flatMap((group) =>
    group.styles.map((s) =>
      [
        `@utility type-${s.name} {`,
        `  font-family: var(--font-${group.family});`,
        `  font-size: ${s.fontSize};`,
        `  line-height: ${s.lineHeight};`,
        `  font-weight: ${s.fontWeight};`,
        `  letter-spacing: ${s.letterSpacing ?? 'normal'};`,
        '}',
      ].join('\n'),
    ),
  );
  const borderUtilities = tokens.borderWidth.tokens.map(
    (t) => `@utility ${t.name} {\n  border-style: solid;\n  border-width: ${t.value};\n}`,
  );

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
${vars('dark', '    ')}
  }
}

/* dark: follows the same rule as the tokens, so data-theme wins over the system setting. */
@custom-variant dark {
  &:where([data-theme='dark'], [data-theme='dark'] *) {
    @slot;
  }
  @media (prefers-color-scheme: dark) {
    &:where(:root:not([data-theme='light']), :root:not([data-theme='light']) *) {
      @slot;
    }
  }
}

@theme inline {
${theme.join('\n')}
}

${typeUtilities.join('\n\n')}

${borderUtilities.join('\n\n')}
`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const tokens = JSON.parse(readFileSync(tokensPath, 'utf8'));
  mkdirSync(dirname(outPath), { recursive: true });
  writeFileSync(outPath, generateTheme(tokens));
  console.log(`theme: ${outPath}`);
}
