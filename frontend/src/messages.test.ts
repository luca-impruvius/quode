import { describe, expect, it } from 'vitest';
import { messages } from './messages';

describe('quiz results wording', () => {
  it('names only the counts that happened', () => {
    expect(messages.quiz.breakdown(1, 0)).toBe('1 partly');
    expect(messages.quiz.breakdown(0, 2)).toBe('2 missed');
    expect(messages.quiz.breakdown(1, 2)).toBe('1 partly, 2 missed');
  });
});
