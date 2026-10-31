import type { QuestionView } from '../api/questions';
import type { Topic } from '../api/topics';

export const topics: Topic[] = [
  { id: 'lang', parentId: null, name: 'Languages', questionCount: 0 },
  { id: 'java', parentId: 'lang', name: 'Java', questionCount: 0 },
  { id: 'coll', parentId: 'java', name: 'Collections', questionCount: 0 },
];

export function questionView(overrides: Partial<QuestionView> = {}): QuestionView {
  return {
    id: 'q1',
    type: 'FLASHCARD',
    promptMd: 'How does **HashMap** handle collisions?',
    answerMd: 'Entries are **chained**.',
    explanationMd: null,
    difficulty: 2,
    status: 'ACTIVE',
    sourceUrl: null,
    topics: [
      { id: 'coll', name: 'Collections', path: 'Languages › Java › Collections', primary: true },
      { id: 'java', name: 'Java', path: 'Languages › Java', primary: false },
    ],
    options: [],
    flaggedAt: null,
    flagNote: null,
    archivedAt: null,
    version: 3,
    createdAt: '2026-10-31T10:00:00Z',
    updatedAt: '2026-10-31T10:00:00Z',
    ...overrides,
  };
}

export const problem = (status: number, detail: string, traceId?: string) =>
  Response.json(
    { status, detail, ...(traceId === undefined ? {} : { traceId }) },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
