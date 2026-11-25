import type { components } from '../api/schema';
import { messages } from '../messages';

export type ImportInput = components['schemas']['ImportInput'];
export type ImportItem = components['schemas']['ImportItem'];

export type ParseResult = { ok: true; input: ImportInput } | { ok: false; error: string };

const m = messages.importer;

/**
 * Turns what was pasted or uploaded into the import JSON (docs/specs/M2-exam-prep-mvp.md): strict
 * JSON (an object with `questions`, or just the array), or the simple text layout. A non-empty
 * `topic` replaces the file's own. The app does not guess: anything else is an error to fix.
 */
export function parseImport(text: string, topic: string): ParseResult {
  const trimmed = text.trim();
  if (trimmed === '') {
    return { ok: false, error: m.empty };
  }
  const result =
    trimmed.startsWith('{') || trimmed.startsWith('[') ? parseJson(trimmed) : parseText(trimmed);
  if (result.ok && topic.trim() !== '') {
    return { ok: true, input: { ...result.input, topic: topic.trim() } };
  }
  return result;
}

function parseJson(text: string): ParseResult {
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    return { ok: false, error: m.badJson };
  }
  if (Array.isArray(data)) {
    return { ok: true, input: { questions: data as ImportItem[] } };
  }
  if (data !== null && typeof data === 'object' && Array.isArray((data as ImportInput).questions)) {
    return { ok: true, input: data as ImportInput };
  }
  return { ok: false, error: m.noQuestions };
}

const QUESTION = /^Q\d*[:.]\s*(.*)$/i;
const OPTION = /^([A-Za-z])[).]\s+(.*)$/;
const ANSWER = /^Answer:\s*(.*)$/i;
const EXPLANATION = /^Explanation:\s*(.*)$/i;

/**
 * Blocks separated by a blank line: `Q:` prompt, options `A)`, `B)`, …, `Answer: B` (or `B, D`),
 * optional `Explanation:`. A line that starts none of these continues the part above it. Without
 * options a block is a flashcard whose answer is the `Answer:` text.
 */
function parseText(text: string): ParseResult {
  const questions: ImportItem[] = [];
  const blocks = text.split(/\r?\n\s*\r?\n/);
  for (let b = 0; b < blocks.length; b++) {
    const parsed = parseBlock(blocks[b] ?? '');
    if (typeof parsed === 'string') {
      return { ok: false, error: m.badBlock(b + 1, parsed) };
    }
    questions.push(parsed);
  }
  return { ok: true, input: { questions } };
}

function parseBlock(block: string): ImportItem | string {
  let prompt: string | null = null;
  const options: string[] = [];
  let answer: string | null = null;
  let explanation: string | null = null;
  let part: 'prompt' | 'option' | 'answer' | 'explanation' | null = null;

  for (const raw of block.split(/\r?\n/)) {
    const line = raw.trim();
    let match: RegExpMatchArray | null;
    if ((match = QUESTION.exec(line))) {
      // A second Q: means a missing blank line: never merge two questions into one.
      if (prompt !== null) {
        return m.twoQuestions;
      }
      prompt = match[1] ?? '';
      part = 'prompt';
    } else if ((match = OPTION.exec(line)) && part !== 'answer' && part !== 'explanation') {
      if ((match[1] ?? '').toUpperCase() !== String.fromCharCode(65 + options.length)) {
        return m.optionOrder;
      }
      options.push(match[2] ?? '');
      part = 'option';
    } else if ((match = ANSWER.exec(line))) {
      if (answer !== null) {
        return m.twoQuestions;
      }
      answer = match[1] ?? '';
      part = 'answer';
    } else if ((match = EXPLANATION.exec(line))) {
      if (explanation !== null) {
        return m.twoQuestions;
      }
      explanation = match[1] ?? '';
      part = 'explanation';
    } else if (part === null) {
      return m.noPrompt;
    } else if (part === 'prompt') {
      prompt = `${prompt ?? ''}\n${line}`;
    } else if (part === 'option') {
      options[options.length - 1] += `\n${line}`;
    } else if (part === 'answer') {
      answer = `${answer ?? ''}\n${line}`;
    } else {
      explanation = `${explanation ?? ''}\n${line}`;
    }
  }
  if (prompt === null) {
    return m.noPrompt;
  }
  const item: ImportItem = { prompt: prompt.trim() };
  if (explanation !== null && explanation.trim() !== '') {
    item.explanation = explanation.trim();
  }
  if (options.length === 0) {
    return { ...item, type: 'FLASHCARD', answer: answer?.trim() || null };
  }
  const letters = (answer ?? '')
    .split(/[\s,]+/)
    .filter((l) => l !== '')
    .map((l) => l.toUpperCase());
  const correct = new Set<number>();
  for (const letter of letters) {
    if (!/^[A-Z]$/.test(letter)) {
      return m.answerFormat;
    }
    const index = letter.charCodeAt(0) - 65;
    if (index >= options.length) {
      return m.badAnswer(letter);
    }
    correct.add(index);
  }
  return {
    ...item,
    type: correct.size > 1 ? 'MULTIPLE_CHOICE' : 'SINGLE_CHOICE',
    options: options.map((text, i) => ({ text: text.trim(), correct: correct.has(i) })),
  };
}
