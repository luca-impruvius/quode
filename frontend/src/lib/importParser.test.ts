import { describe, expect, it } from 'vitest';
import { messages } from '../messages';
import { parseImport } from './importParser';

const m = messages.importer;

function questions(text: string, topic = '') {
  const result = parseImport(text, topic);
  if (!result.ok) {
    throw new Error(result.error);
  }
  return result.input;
}

describe('parseImport', () => {
  it('reads the text layout: single and multiple choice, explanation, blank-line blocks', () => {
    const input = questions(
      [
        'Q: Which service runs containers serverless?',
        'A) GKE',
        'B) Cloud Run',
        'Answer: B',
        'Explanation: No cluster to manage.',
        '',
        'Q2: Which are managed databases?',
        'A) Cloud SQL',
        'B) Compute Engine',
        'C) Spanner',
        'Answer: A, C',
      ].join('\n'),
      'Cloud › GCP',
    );
    expect(input.topic).toBe('Cloud › GCP');
    expect(input.questions).toEqual([
      {
        type: 'SINGLE_CHOICE',
        prompt: 'Which service runs containers serverless?',
        explanation: 'No cluster to manage.',
        options: [
          { text: 'GKE', correct: false },
          { text: 'Cloud Run', correct: true },
        ],
      },
      {
        type: 'MULTIPLE_CHOICE',
        prompt: 'Which are managed databases?',
        options: [
          { text: 'Cloud SQL', correct: true },
          { text: 'Compute Engine', correct: false },
          { text: 'Spanner', correct: true },
        ],
      },
    ]);
  });

  it('keeps a question without an answer, and joins continuation lines', () => {
    const input = questions(
      'Q: Pick one\nthat is true.\nA) X\nB) Y\n\nQ: What is IAM?\nAnswer: Access control.',
    );
    expect(input.questions?.[0]).toEqual({
      type: 'SINGLE_CHOICE',
      prompt: 'Pick one\nthat is true.',
      options: [
        { text: 'X', correct: false },
        { text: 'Y', correct: false },
      ],
    });
    expect(input.questions?.[1]).toEqual({
      type: 'FLASHCARD',
      prompt: 'What is IAM?',
      answer: 'Access control.',
    });
  });

  it('names the block that breaks the layout', () => {
    expect(parseImport('Q: Fine\nA) x\nB) y\nAnswer: A\n\nNo prompt here', '')).toEqual({
      ok: false,
      error: m.badBlock(2, m.noPrompt),
    });
    expect(parseImport('Q: One\nA) x\nC) y', '')).toEqual({
      ok: false,
      error: m.badBlock(1, m.optionOrder),
    });
    expect(parseImport('Q: One\nA) x\nB) y\nAnswer: D', '')).toEqual({
      ok: false,
      error: m.badBlock(1, m.badAnswer('D')),
    });
  });

  it('reads JSON as an object or a bare list; the topic field wins over the file', () => {
    const file =
      '{"topic":"Networking","questions":[{"type":"FLASHCARD","prompt":"P","answer":"A"}]}';
    expect(questions(file).topic).toBe('Networking');
    expect(questions(file, 'Cloud').topic).toBe('Cloud');
    expect(questions('[{"type":"FLASHCARD","prompt":"P"}]').questions).toHaveLength(1);
  });

  it('rejects empty input, broken JSON and JSON without questions', () => {
    expect(parseImport('  ', '')).toEqual({ ok: false, error: m.empty });
    expect(parseImport('{"questions": [', '')).toEqual({ ok: false, error: m.badJson });
    expect(parseImport('{"items": []}', '')).toEqual({ ok: false, error: m.noQuestions });
  });
});
