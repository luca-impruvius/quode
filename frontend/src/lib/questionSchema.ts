import { z } from 'zod';
import type { QuestionInput, QuestionType } from '../api/questions';

// The backend's rules and messages (QuestionRules.java), for instant feedback. The backend checks
// again and has the last word.
export const MAX_OPTIONS = 6;
export const MIN_OPTIONS = 2;
export const MAX_TOPICS = 5;

const isChoice = (type: QuestionType) => type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE';

// Characters as PostgreSQL counts them (code points), like the backend.
const length = (text: string) => [...text].length;
const blank = (text: string) => text.trim() === '';

export interface QuestionFormValues {
  type: QuestionType;
  promptMd: string;
  answerMd: string;
  explanationMd: string;
  difficulty: number;
  sourceUrl: string;
  topicIds: string[];
  primaryTopicId: string;
  options: { textMd: string; correct: boolean }[];
}

export const questionSchema = z
  .object({
    type: z.enum(['FLASHCARD', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'OPEN_TEXT']),
    promptMd: z.string(),
    answerMd: z.string(),
    explanationMd: z.string(),
    difficulty: z.number(),
    sourceUrl: z.string(),
    topicIds: z.array(z.string()),
    primaryTopicId: z.string(),
    options: z.array(z.object({ textMd: z.string(), correct: z.boolean() })),
  })
  .superRefine((q, ctx) => {
    const fail = (path: (string | number)[], message: string) =>
      ctx.addIssue({ code: 'custom', path, message });

    if (blank(q.promptMd) || length(q.promptMd) > 2000) {
      fail(['promptMd'], 'The question needs 1 to 2,000 characters.');
    }
    if (!isChoice(q.type) && blank(q.answerMd)) {
      fail(['answerMd'], 'This question type needs an answer.');
    } else if (length(q.answerMd) > 10_000) {
      fail(['answerMd'], 'The answer can have up to 10,000 characters.');
    }
    if (length(q.explanationMd) > 10_000) {
      fail(['explanationMd'], 'The explanation can have up to 10,000 characters.');
    }
    if (
      !blank(q.sourceUrl) &&
      (!/^https?:\/\/\S+$/.test(q.sourceUrl) || length(q.sourceUrl) > 2000)
    ) {
      fail(
        ['sourceUrl'],
        'The source link must start with http:// or https:// (up to 2,000 characters).',
      );
    }
    if (q.topicIds.length < 1 || q.topicIds.length > MAX_TOPICS) {
      fail(['topicIds'], 'Choose 1 to 5 different topics.');
    } else if (!q.topicIds.includes(q.primaryTopicId)) {
      fail(['topicIds'], "Choose one of the question's topics as the primary topic.");
    }
    if (isChoice(q.type)) {
      q.options.forEach((option, i) => {
        if (blank(option.textMd) || length(option.textMd) > 2000) {
          fail(['options', i, 'textMd'], 'Every option needs 1 to 2,000 characters.');
        }
      });
      const correct = q.options.filter((option) => option.correct).length;
      if (q.type === 'SINGLE_CHOICE' && correct !== 1) {
        fail(['options'], 'Mark exactly one option as correct.');
      }
      if (q.type === 'MULTIPLE_CHOICE' && correct < 1) {
        fail(['options'], 'Mark at least one option as correct.');
      }
    }
  });

/** What the API gets: options only for choice types, blank optional texts as null. */
export function toQuestionInput(values: QuestionFormValues): QuestionInput {
  const optional = (text: string) => (blank(text) ? null : text);
  return {
    type: values.type,
    promptMd: values.promptMd,
    answerMd: optional(values.answerMd),
    explanationMd: optional(values.explanationMd),
    difficulty: values.difficulty,
    sourceUrl: optional(values.sourceUrl),
    topicIds: values.topicIds,
    primaryTopicId: values.primaryTopicId,
    options: isChoice(values.type) ? values.options : [],
  };
}

export const emptyOption = () => ({ textMd: '', correct: false });

export { isChoice };
