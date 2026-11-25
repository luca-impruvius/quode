import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LibraryItem } from '../api/library';
import type { QuestionCard, SessionView } from '../api/study';
import { messages } from '../messages';
import { questionView, topics } from '../test/questionFixtures';

const question = questionView();
import { me, mockApi, renderAt } from '../test/renderApp';

// A desktop-wide window (docs/specs/M2-exam-prep-mvp.md, Desktop layout: from 1024 px).
function desktopWindow() {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query === '(min-width: 1024px)',
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }));
}

beforeEach(desktopWindow);
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

const item: LibraryItem = {
  id: 'q1',
  type: 'FLASHCARD',
  promptMd: question.promptMd,
  difficulty: 2,
  primaryTopic: { id: 'coll', name: 'Collections', path: 'Languages › Java › Collections' },
  archived: false,
  flagged: false,
  flagNote: null,
  createdAt: '2026-11-24T10:00:00Z',
  updatedAt: '2026-11-24T10:00:00Z',
};

const page = (items: LibraryItem[]) =>
  Response.json({ items, page: 0, size: 20, totalElements: items.length, hasNext: false });

describe('Desktop layout', () => {
  it('has a sidebar with Add and the four places instead of the tab bar', async () => {
    mockApi({ '/api/me': me() });
    renderAt('/');
    const nav = await screen.findByRole('navigation', { name: messages.tabs.label });
    expect(
      within(nav)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual([
      messages.tabs.add,
      messages.tabs.today,
      messages.tabs.library,
      messages.tabs.quizzes,
      messages.tabs.me,
    ]);
  });

  it('shows the Library list and the selected question side by side', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/topics': Response.json(topics),
      '/api/v1/questions?status=active&sort=created&page=0&size=20': page([item]),
      '/api/v1/questions/q1': Response.json(question),
    });
    const user = userEvent.setup();
    const router = renderAt('/library');

    expect(await screen.findByText(messages.question.pick)).toBeInTheDocument();
    await user.click(await screen.findByRole('link', { name: new RegExp('HashMap') }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/library'));
    expect(router.state.location.search).toContain('selected=q1');
    const panel = await screen.findByRole('region', { name: messages.question.panel });
    expect(within(panel).getByRole('link', { name: messages.question.edit })).toHaveAttribute(
      'href',
      '/questions/q1/edit',
    );
  });

  it('opens the editor with every option showing, in two columns', async () => {
    mockApi({ '/api/me': me(), '/api/v1/topics': Response.json(topics) });
    renderAt('/add');
    expect(
      await screen.findByRole('group', { name: messages.editor.difficulty }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.editor.moreOptions })).toBeNull();
  });

  it('answers a quiz from the keyboard: a letter chooses, Enter checks and goes on', async () => {
    const card: QuestionCard = {
      id: 'q1',
      type: 'SINGLE_CHOICE',
      promptMd: 'Serverless containers?',
      options: [
        { id: 'o1', textMd: 'GKE' },
        { id: 'o2', textMd: 'Cloud Run' },
      ],
      answerMd: null,
      explanationMd: null,
    };
    const view = (overrides: Partial<SessionView>): SessionView => ({
      id: 's1',
      kind: 'ADHOC',
      total: 2,
      answered: 0,
      correct: 0,
      current: card,
      finishedAt: null,
      ...overrides,
    });
    const next: QuestionCard = { ...card, id: 'q2', promptMd: 'Next one?' };
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': () => Response.json(view({})),
      'POST /api/v1/study/sessions/s1/answers': () =>
        Response.json({
          outcome: 'CORRECT',
          correctOptionIds: ['o2'],
          answerMd: null,
          explanationMd: null,
          session: view({ answered: 1, correct: 1, current: next }),
        }),
    });
    const user = userEvent.setup();
    renderAt('/quiz/s1');

    expect(await screen.findByText('Serverless containers?')).toBeInTheDocument();
    expect(screen.getByText(messages.quiz.keysChoice)).toBeInTheDocument();
    await user.keyboard('b');
    expect(screen.getByRole('radio', { name: 'Cloud Run' })).toBeChecked();
    await user.keyboard('{Enter}');
    expect(await screen.findByText(messages.quiz.correct, { selector: 'p' })).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/answers'));
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      questionId: 'q1',
      selectedOptionIds: ['o2'],
    });
  });
});
