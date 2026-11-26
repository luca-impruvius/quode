import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LibraryItem } from '../api/library';
import type { QuestionCard, SessionView } from '../api/study';
import { messages } from '../messages';
import { problem, questionView, topics } from '../test/questionFixtures';
import { me, mockApi, renderAt } from '../test/renderApp';
import { DESKTOP } from './useDesktop';

const question = questionView();

// A window whose width can change: desktop (docs/specs/M2-exam-prep-mvp.md, from 1024 px) or phone.
const screenSize = { desktop: true, listeners: new Set<() => void>() };

function resizeTo(desktop: boolean) {
  screenSize.desktop = desktop;
  act(() => screenSize.listeners.forEach((listener) => listener()));
}

beforeEach(() => {
  screenSize.desktop = true;
  screenSize.listeners.clear();
  vi.stubGlobal('matchMedia', (query: string) => ({
    get matches() {
      return query === DESKTOP && screenSize.desktop;
    },
    media: query,
    addEventListener: (_: string, listener: () => void) => screenSize.listeners.add(listener),
    removeEventListener: (_: string, listener: () => void) => screenSize.listeners.delete(listener),
  }));
});

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

// The Library answers every filter with the one item; the detail with the question.
function library() {
  const requests: URL[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = new URL(String(input), 'http://localhost');
    if (url.pathname === '/api/me') {
      return me();
    }
    if (url.pathname === '/api/v1/topics') {
      return Response.json(topics);
    }
    if (url.pathname === '/api/v1/questions') {
      requests.push(url);
      return Response.json({ items: [item], page: 0, size: 20, totalElements: 1, hasNext: false });
    }
    if (url.pathname === '/api/v1/questions/q1/review') {
      return Response.json({ enrolled: true, learnNext: false, dueAt: null, isNew: true });
    }
    if (url.pathname === '/api/v1/questions/q1') {
      return Response.json(question);
    }
    if (url.pathname === '/api/v1/questions/gone') {
      return problem(404, 'Question not found.');
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  });
  return requests;
}

describe('Desktop layout', () => {
  it('has a sidebar instead of the tab bar, and back again on a phone-wide window', async () => {
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

    resizeTo(false);
    const tabs = screen.getByRole('navigation', { name: messages.tabs.label });
    expect(within(tabs).getAllByRole('link')[0]).toHaveTextContent(messages.tabs.today);
  });

  it('shows the Library list and the selected question side by side', async () => {
    library();
    const user = userEvent.setup();
    const router = renderAt('/library');

    expect(await screen.findByText(messages.question.pick)).toBeInTheDocument();
    await user.click(await screen.findByRole('link', { name: /HashMap/ }));

    await waitFor(() => expect(router.state.location.search).toContain('selected=q1'));
    expect(router.state.location.pathname).toBe('/library');
    const panel = await screen.findByRole('region', { name: messages.question.panel });
    expect(within(panel).getByRole('link', { name: messages.question.edit })).toHaveAttribute(
      'href',
      '/questions/q1/edit',
    );
    expect(within(panel).getByRole('status')).toHaveTextContent(
      messages.question.showing(question.promptMd),
    );
    expect(screen.getByRole('link', { name: /HashMap/ })).toHaveAttribute('aria-current', 'true');
  });

  it('keeps the selection when a filter changes, and explains an unknown one', async () => {
    const requests = library();
    const user = userEvent.setup();
    const router = renderAt('/library?selected=q1');

    await screen.findByRole('region', { name: messages.question.panel });
    await user.selectOptions(
      screen.getByRole('combobox', { name: messages.library.sort }),
      'updated',
    );
    await waitFor(() => expect(requests.at(-1)?.searchParams.get('sort')).toBe('updated'));
    expect(router.state.location.search).toContain('selected=q1');

    await act(() => router.navigate('/library?selected=gone'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Question not found.');
  });

  it('links Library rows to their own screen on a phone-wide window', async () => {
    screenSize.desktop = false;
    library();
    renderAt('/library');
    expect(await screen.findByRole('link', { name: /HashMap/ })).toHaveAttribute(
      'href',
      '/questions/q1',
    );
    expect(screen.queryByText(messages.question.pick)).not.toBeInTheDocument();
  });

  it('opens the editor with every option showing, in two columns', async () => {
    mockApi({ '/api/me': me(), '/api/v1/topics': Response.json(topics) });
    renderAt('/add');
    expect(
      await screen.findByRole('group', { name: messages.editor.difficulty }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: messages.editor.moreOptions })).toBeNull();
  });
});

describe('Quiz keys on desktop', () => {
  const single: QuestionCard = {
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
  const card: QuestionCard = {
    id: 'q2',
    type: 'FLASHCARD',
    promptMd: 'What is IAM?',
    options: [],
    answerMd: 'Access control.',
    explanationMd: null,
  };
  const view = (overrides: Partial<SessionView>): SessionView => ({
    id: 's1',
    kind: 'ADHOC',
    total: 3,
    answered: 0,
    correct: 0,
    current: single,
    finishedAt: null,
    ...overrides,
  });
  const answerBody = (fetchMock: ReturnType<typeof mockApi>) =>
    JSON.parse(
      String(fetchMock.mock.calls.findLast(([u]) => String(u).endsWith('/answers'))?.[1]?.body),
    );

  it('a letter chooses, Enter checks, Enter again goes on', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': () => Response.json(view({})),
      'POST /api/v1/study/sessions/s1/answers': () =>
        Response.json({
          outcome: 'CORRECT',
          correctOptionIds: ['o2'],
          answerMd: null,
          explanationMd: null,
          session: view({ answered: 1, correct: 1, current: card }),
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
    expect(answerBody(fetchMock)).toEqual({ questionId: 'q1', selectedOptionIds: ['o2'] });

    await user.keyboard('{Enter}');
    expect(await screen.findByText('What is IAM?')).toBeInTheDocument();
  });

  it('Enter reveals a card and a digit grades it, though focus is on a grade button', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': () => Response.json(view({ answered: 1, current: card })),
      'POST /api/v1/study/sessions/s1/answers': () =>
        Response.json({
          outcome: 'PARTIAL',
          correctOptionIds: [],
          answerMd: 'Access control.',
          explanationMd: null,
          session: view({ answered: 2, current: single }),
        }),
    });
    const user = userEvent.setup();
    renderAt('/quiz/s1');

    expect(await screen.findByText('What is IAM?')).toBeInTheDocument();
    await user.keyboard('{Enter}');
    expect(screen.getByText('Access control.')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: messages.quiz.grades.GOT_IT })).toHaveFocus(),
    );
    await user.keyboard('2');
    await waitFor(() =>
      expect(answerBody(fetchMock)).toEqual({ questionId: 'q2', grade: 'PARTIAL' }),
    );
    expect(await screen.findByText('Serverless containers?')).toBeInTheDocument();
  });

  it('holding Enter acts once: it reveals, and grades nothing', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': () => Response.json(view({ answered: 1, current: card })),
    });
    const user = userEvent.setup();
    renderAt('/quiz/s1');

    expect(await screen.findByText('What is IAM?')).toBeInTheDocument();
    await user.keyboard('{Enter>3}');
    expect(screen.getByText('Access control.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([u]) => String(u).endsWith('/answers'))).toBe(false);
  });
});
