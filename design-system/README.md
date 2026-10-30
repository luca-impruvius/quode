# Quode

Quode is a mobile-first study app: store what you learn as questions, review a few every day. The design has one job, which is to make a tired five-minute session on a phone feel easy and a little fun. It is calm and high-contrast, with one bright beak-yellow action per screen and bold "sticker" outlines that echo the duck mascot.

## Principles

1. **One obvious action per screen.** Only that action gets the `accent` fill and `shadow-pop`. Everything else is outlined or quiet.
2. **Thumb zone first.** Primary actions and grade buttons sit at the bottom of the screen; touch targets are at least 44px, primary buttons 56px.
3. **Readable when tired.** Body text is 16px, questions 21px, generous line height. No light grey body text.
4. **State is never colour alone.** Correct, partial and missed always carry a word ("Correct · your pick") and use blue / purple / orange, which differ in lightness as well as hue.
5. **Dark mode is a first-class theme.** Most sessions happen in the evening; every token has a dark value.

## Voice

Short, plain and a little warm. The duck may be playful; the interface copy is not.

- Buttons say what happens: "Start review", "Save & add another", "Add 6".
- Feedback is honest and kind: "Not quite · counted as Missed", never "Wrong!".
- Numbers carry their unit: "30 due reviews", "about 9 min".
- The duck speaks one short line on Today and Summary, nowhere else.

## Colour

- `bg` behind everything, `surface` for cards and controls, `surface-sunken` for quiet fills.
- `ink` for text, `ink-muted` for meta lines. `outline` (2px, `border-bold`) frames interactive things.
- `accent` (beak yellow) is a fill only, with `on-accent` text. Never use it as text or a thin line: it fails contrast on light grounds.
- Outcomes: `correct` / `correct-bg`, `partial` / `partial-bg`, `missed` / `missed-bg`. Text on the `-bg` fills stays `ink`.
- `focus`: 3px ring, 2px offset, on every interactive element.

## Type

- **Bricolage Grotesque** (display): wordmark, screen titles, the big number on Today. Used sparingly.
- **IBM Plex Sans** (text): everything else.
- **IBM Plex Mono** (code): inline code and code blocks; code blocks sit on `ink` with `surface-sunken`-light text, radius `radius-sm`.
- All three are SIL OFL 1.1 fonts, self-hosted from the Fontsource npm packages (Bricolage Grotesque 600/700, IBM Plex Sans 400/600/700, IBM Plex Mono 400): browsers never contact Google, and the app works offline.

## Shape and depth

- Corners: `radius-md` for buttons, inputs and options; `radius-lg` for cards; `radius-pill` for chips, progress and the add button.
- Depth comes from outlines, not blur. `shadow-pop` is the only shadow, reserved for the primary action; pressing removes it and shifts the button 2px down-right.

## Spacing

A 4px base: `space-1` 4 … `space-6` 32. Screens use a `space-4` (16px) side gutter; sections are `space-5` apart.

## Mascot

The Quode duck is a white, slightly puzzled duck seen from the front: thick black hand-drawn outline, orange beak (#FF751F), and three orange question marks above its head. Quode is about questions, so the duck is always wondering.

- **Duck.svg** (full, with question marks): Login, empty states, the session summary.
- **duck-mark.svg** (head only): small places such as the Today greeting and avatars, from 48px.
- **app-icon.svg** (head on a beak-yellow tile): the installable-app icon and favicon.
- The duck stays white in both themes; its black outline and white body keep it readable on light and dark grounds.
- Don't recolour it, add gradients or shadows, or place it on busy backgrounds. The beak orange belongs to the duck only; it is not a UI colour (Missed uses the `missed` token).
- More expressions (proud on a streak, confused on Missed) are an M4 illustration task, drawn in the same style. The duck speaks one short line on Today and the summary, nowhere else.

## Iconography

2px stroke icons with round caps and joins, 20–24px, coloured `ink` (Lucide in code). Icon-only buttons always have an accessible label.

## Components

Button, GradeButtons, Chip, Card, TextField, AnswerOption, ProgressBar and TabBar. Classes are prefixed `qd-` and live in `components/bundle.css`; the React components are built in the app's frontend and map one-to-one to these previews.
