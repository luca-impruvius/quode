import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ReviewToday } from '../api/review';
import type { QuestionCard, SessionView } from '../api/study';
import { messages } from '../messages';
import { me, mockApi, renderAt } from '../test/renderApp';

const t = messages.today;

const card: QuestionCard = {
  id: 'q1',
  type: 'FLASHCARD',
  promptMd: 'What is IAM?',
  options: [],
  answerMd: 'Access control.',
  explanationMd: null,
};

const today = (overrides: Partial<ReviewToday>): ReviewToday => ({
  due: 0,
  newWaiting: 0,
  moreDue: 0,
  moreNew: 0,
  doneToday: 0,
  newToday: 0,
  nextDueAt: null,
  sessionId: null,
  ...overrides,
});

const review = (overrides: Partial<SessionView> = {}): SessionView => ({
  id: 'r1',
  kind: 'REVIEW',
  total: 3,
  answered: 0,
  correct: 0,
  current: card,
  finishedAt: null,
  ...overrides,
});

const json =
  (body: unknown, status = 200) =>
  () =>
    Response.json(body, { status });

const problem = (status: number, detail: string) => () =>
  Response.json(
    { status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );

function bodyOf(fetchMock: ReturnType<typeof mockApi>, url: string): unknown {
  const call = fetchMock.mock.calls.findLast(([u]) => String(u) === url);
  return JSON.parse(String(call?.[1]?.body));
}

afterEach(() => {
  vi.useRealTimers();
});

describe('Today', () => {
  it('shows the counts and starts a review', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/review/today': json(today({ due: 12, newWaiting: 5 })),
      'POST /api/v1/study/sessions': json(review(), 201),
      '/api/v1/study/sessions/r1': json(review()),
    });
    const user = userEvent.setup();
    const router = renderAt('/');

    expect(await screen.findByText(t.counts(12, 5))).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: t.start }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/quiz/r1'));
    expect(bodyOf(fetchMock, '/api/v1/study/sessions')).toEqual({ kind: 'REVIEW', extra: 'NONE' });
    // The session screen is titled Review and leads back to Today.
    expect(await screen.findByText('What is IAM?')).toBeInTheDocument();
    expect(screen.getByText(messages.review.title)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.review.back })).toHaveAttribute('href', '/');
  });

  it('offers Continue while today’s review is unfinished', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/review/today': json(today({ due: 2, newWaiting: 1, sessionId: 'r1' })),
      '/api/v1/study/sessions/r1': json(review({ answered: 2 })),
    });
    const user = userEvent.setup();
    const router = renderAt('/');

    expect(await screen.findByText(t.counts(2, 1))).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: t.start })).not.toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: new RegExp(t.continue) }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/quiz/r1'));
    expect(await screen.findByText('What is IAM?')).toBeInTheDocument();
  });

  it('says all done, with Keep going, Learn more and the next due day', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-25T10:00:00Z') });
    const fetchMock = mockApi({
      '/api/me': me(),
      // Start of 27 November in Bucharest (UTC+2): two days away.
      '/api/v1/review/today': json(
        today({ moreDue: 8, moreNew: 40, doneToday: 30, nextDueAt: '2026-11-26T22:00:00Z' }),
      ),
      'POST /api/v1/study/sessions': json(review({ id: 'r2' }), 201),
      '/api/v1/study/sessions/r2': json(review({ id: 'r2' })),
    });
    const user = userEvent.setup();
    const router = renderAt('/');

    expect(await screen.findByRole('heading', { name: t.allDone })).toBeInTheDocument();
    expect(screen.getByText(t.nextDue(2))).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: t.start })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: new RegExp(t.learnMore) })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: new RegExp(t.keepGoing) }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/quiz/r2'));
    expect(bodyOf(fetchMock, '/api/v1/study/sessions')).toEqual({
      kind: 'REVIEW',
      extra: 'KEEP_GOING',
    });
  });

  it('starts Learn more and shows only the choices that have questions waiting', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/review/today': json(today({ moreNew: 3, newToday: 5 })),
      'POST /api/v1/study/sessions': json(review({ id: 'r3' }), 201),
      '/api/v1/study/sessions/r3': json(review({ id: 'r3' })),
    });
    const user = userEvent.setup();
    const router = renderAt('/');

    expect(await screen.findByRole('heading', { name: t.allDone })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: new RegExp(t.keepGoing) })).not.toBeInTheDocument();
    // Nothing scheduled after today: no next due line.
    expect(screen.queryByText(/Next ones due/)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: new RegExp(t.learnMore) }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/quiz/r3'));
    expect(bodyOf(fetchMock, '/api/v1/study/sessions')).toEqual({
      kind: 'REVIEW',
      extra: 'LEARN_MORE',
    });
  });

  it('says next ones are due tomorrow', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-25T21:30:00Z') });
    mockApi({
      '/api/me': me(),
      // 23:30 in Bucharest; tomorrow starts at 22:00 UTC.
      '/api/v1/review/today': json(today({ doneToday: 4, nextDueAt: '2026-11-25T22:00:00Z' })),
    });
    renderAt('/');

    expect(await screen.findByText(t.nextDue(1))).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('points to adding questions when there is nothing at all', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/review/today': json(today({})),
    });
    renderAt('/');

    expect(await screen.findByText(t.nothingYet)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: t.allDone })).not.toBeInTheDocument();
  });

  it('refreshes the counts when there turns out to be nothing to review', async () => {
    let calls = 0;
    mockApi({
      '/api/me': me(),
      '/api/v1/review/today': () => {
        calls++;
        return Response.json(calls === 1 ? today({ due: 1 }) : today({ doneToday: 1 }));
      },
      'POST /api/v1/study/sessions': problem(409, 'Nothing to review.'),
    });
    const user = userEvent.setup();
    renderAt('/');

    await user.click(await screen.findByRole('button', { name: t.start }));
    expect(await screen.findByRole('heading', { name: t.allDone })).toBeInTheDocument();
  });

  it('offers only Continue while a Keep going review is unfinished', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/review/today': json(today({ sessionId: 'r2', doneToday: 30, moreDue: 4 })),
    });
    renderAt('/');

    expect(await screen.findByRole('link', { name: new RegExp(t.continue) })).toBeInTheDocument();
    expect(screen.queryByText(t.counts(0, 0))).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: t.allDone })).not.toBeInTheDocument();
  });

  it('shows the error when a review fails to start', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/review/today': json(today({ due: 1 })),
      'POST /api/v1/study/sessions': problem(500, 'Boom'),
    });
    const user = userEvent.setup();
    renderAt('/');

    await user.click(await screen.findByRole('button', { name: t.start }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: t.start })).toBeInTheDocument();
  });

  it('shows an error when today can’t be read', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/review/today': problem(500, 'Boom'),
    });
    renderAt('/');

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});

describe('Review session', () => {
  it('ends with the reviewed count and when the next ones are due', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-25T10:00:00Z') });
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/r1': json(review({ answered: 2 })),
      'POST /api/v1/study/sessions/r1/answers': json({
        outcome: 'CORRECT',
        correctOptionIds: [],
        answerMd: 'Access control.',
        explanationMd: null,
        session: review({ answered: 3, current: null }),
      }),
      'POST /api/v1/study/sessions/r1/finish': json({
        total: 3,
        answered: 3,
        correct: 2,
        partial: 0,
        wrong: 1,
        missedQuestionIds: ['q9'],
        missed: [{ id: 'q9', promptMd: 'Missed one' }],
      }),
      '/api/v1/review/today': json(today({ doneToday: 3, nextDueAt: '2026-11-25T22:00:00Z' })),
    });
    localStorage.setItem('quode.quiz.current', 'quiz1');
    const user = userEvent.setup();
    renderAt('/quiz/r1');

    await user.click(await screen.findByRole('button', { name: messages.quiz.reveal }));
    await user.click(screen.getByRole('button', { name: messages.quiz.grades.GOT_IT }));

    expect(
      await screen.findByRole('heading', { name: messages.review.doneTitle }),
    ).toBeInTheDocument();
    expect(screen.getByText(messages.review.reviewed(3))).toBeInTheDocument();
    expect(await screen.findByText(t.nextDue(1))).toBeInTheDocument();
    // A review offers no quiz retry, and leaves the quiz to continue alone.
    expect(screen.queryByRole('button', { name: /Retry/ })).not.toBeInTheDocument();
    expect(localStorage.getItem('quode.quiz.current')).toBe('quiz1');
    expect(fetchMock.mock.calls.some(([u]) => String(u).endsWith('/r1/finish'))).toBe(true);
    expect(screen.getByRole('link', { name: messages.review.backToToday })).toHaveAttribute(
      'href',
      '/',
    );
    localStorage.clear();
  });

  it('points to Keep going when more reviews are due after the review', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/r1': json(review({ answered: 3, current: null })),
      'POST /api/v1/study/sessions/r1/finish': json({
        total: 3,
        answered: 3,
        correct: 3,
        partial: 0,
        wrong: 0,
        missedQuestionIds: [],
        missed: [],
      }),
      '/api/v1/review/today': json(
        today({ doneToday: 30, moreDue: 5, nextDueAt: '2026-11-25T22:00:00Z' }),
      ),
    });
    renderAt('/quiz/r1');

    expect(await screen.findByText(messages.review.moreToday)).toBeInTheDocument();
    // When the next ones are due always shows too.
    expect(screen.getByText(/Next ones due/)).toBeInTheDocument();
  });

  it('still says when the next ones are due while only new questions wait', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/r1': json(review({ answered: 3, current: null })),
      'POST /api/v1/study/sessions/r1/finish': json({
        total: 3,
        answered: 3,
        correct: 3,
        partial: 0,
        wrong: 0,
        missedQuestionIds: [],
        missed: [],
      }),
      '/api/v1/review/today': json(
        today({ newToday: 5, moreNew: 40, nextDueAt: '2026-11-25T22:00:00Z' }),
      ),
    });
    renderAt('/quiz/r1');

    expect(await screen.findByText(/Next ones due/)).toBeInTheDocument();
    expect(screen.queryByText(messages.review.moreToday)).not.toBeInTheDocument();
  });

  it('shows the error and the way back when finishing fails', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/r1': json(review({ answered: 3, current: null })),
      'POST /api/v1/study/sessions/r1/finish': problem(500, 'Boom'),
      '/api/v1/review/today': json(today({})),
    });
    renderAt('/quiz/r1');

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: messages.review.backToToday })).toHaveAttribute(
      'href',
      '/',
    );
  });
});
