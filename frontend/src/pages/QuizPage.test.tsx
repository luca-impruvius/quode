import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import type { QuestionCard, SessionView } from '../api/study';
import { messages } from '../messages';
import { topics } from '../test/questionFixtures';
import { me, mockApi, renderAt } from '../test/renderApp';

const m = messages.quiz;
const q = messages.quizzes;

const single: QuestionCard = {
  id: 'q1',
  type: 'SINGLE_CHOICE',
  promptMd: 'Which runs containers serverless?',
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
  explanationMd: 'Who can do what.',
};

const view = (overrides: Partial<SessionView>): SessionView => ({
  id: 's1',
  kind: 'ADHOC',
  total: 2,
  answered: 0,
  correct: 0,
  current: single,
  finishedAt: null,
  ...overrides,
});

const json =
  (body: unknown, status = 200) =>
  () =>
    Response.json(body, { status });

function bodyOf(fetchMock: ReturnType<typeof mockApi>, url: string): unknown {
  const call = fetchMock.mock.calls.findLast(([u]) => String(u) === url);
  return JSON.parse(String(call?.[1]?.body));
}

afterEach(() => {
  localStorage.clear();
});

describe('Quiz', () => {
  it('grades a choice at once, shows the right answer, then reveals and self-grades a card', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': json(view({})),
      'POST /api/v1/study/sessions/s1/answers': json({
        outcome: 'WRONG',
        correctOptionIds: ['o2'],
        answerMd: null,
        explanationMd: 'No cluster to run.',
        session: view({ answered: 1, current: card }),
      }),
    });
    const user = userEvent.setup();
    renderAt('/quiz/s1');

    expect(await screen.findByText('Which runs containers serverless?')).toBeInTheDocument();
    expect(screen.getByText(m.progress(1, 2))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: m.check })).toBeDisabled();

    await user.click(screen.getByRole('radio', { name: 'GKE' }));
    await user.click(screen.getByRole('button', { name: m.check }));
    expect(await screen.findByText(m.wrong, { selector: 'p.font-semibold' })).toBeInTheDocument();
    expect(screen.getByText('No cluster to run.')).toBeInTheDocument();
    expect(screen.getByText(m.rightAnswer)).toBeInTheDocument();
    expect(screen.getByText(m.yourChoice)).toBeInTheDocument();
    expect(bodyOf(fetchMock, '/api/v1/study/sessions/s1/answers')).toEqual({
      questionId: 'q1',
      selectedOptionIds: ['o1'],
    });
    expect(screen.getByRole('button', { name: m.next })).toHaveFocus();
    expect(screen.getByRole('status')).toHaveTextContent(m.wrong);

    await user.click(screen.getByRole('button', { name: m.next }));
    expect(await screen.findByText('What is IAM?')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText('What is IAM?').closest('[tabindex="-1"]')).toHaveFocus(),
    );
    expect(screen.queryByText('Access control.')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: m.reveal }));
    expect(screen.getByText('Access control.')).toBeInTheDocument();
    expect(screen.getByText('Who can do what.')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: m.grades.GOT_IT })).toHaveFocus(),
    );
  });

  it('moves on after a self-grade that is not the last question', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': json(view({ current: card })),
      'POST /api/v1/study/sessions/s1/answers': json({
        outcome: 'CORRECT',
        correctOptionIds: [],
        answerMd: 'Access control.',
        explanationMd: null,
        session: view({ answered: 1, current: single }),
      }),
    });
    const user = userEvent.setup();
    renderAt('/quiz/s1');

    await user.click(await screen.findByRole('button', { name: m.reveal }));
    await user.click(screen.getByRole('button', { name: m.grades.GOT_IT }));
    expect(await screen.findByText('Which runs containers serverless?')).toBeInTheDocument();
  });

  it('jumps to where the quiz really is after answering in another tab', async () => {
    let calls = 0;
    mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': () => {
        calls++;
        return Response.json(calls === 1 ? view({}) : view({ answered: 1, current: card }));
      },
      'POST /api/v1/study/sessions/s1/answers': () =>
        Response.json(
          { status: 409, detail: 'Not the current question.' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
    });
    const user = userEvent.setup();
    renderAt('/quiz/s1');

    await user.click(await screen.findByRole('radio', { name: 'GKE' }));
    await user.click(screen.getByRole('button', { name: m.check }));
    expect(await screen.findByText('What is IAM?')).toBeInTheDocument();
  });

  it('offers no retry when nothing was missed', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': json(view({ answered: 2, current: null })),
      'POST /api/v1/study/sessions/s1/finish': json({
        total: 2,
        answered: 2,
        correct: 2,
        partial: 0,
        wrong: 0,
        missedQuestionIds: [],
        missed: [],
      }),
    });
    localStorage.setItem('quode.quiz.current', 'other');
    renderAt('/quiz/s1');

    expect(await screen.findByText(m.score(2, 2))).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Retry/ })).not.toBeInTheDocument();
    // Another quiz stays the one to continue.
    expect(localStorage.getItem('quode.quiz.current')).toBe('other');
  });

  it('sends a self-grade and shows the results with Retry missed', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': json(view({ answered: 1, current: card })),
      'POST /api/v1/study/sessions/s1/answers': json({
        outcome: 'PARTIAL',
        correctOptionIds: [],
        answerMd: 'Access control.',
        explanationMd: null,
        session: view({ answered: 2, current: null }),
      }),
      'POST /api/v1/study/sessions/s1/finish': json({
        total: 2,
        answered: 2,
        correct: 1,
        partial: 1,
        wrong: 0,
        missedQuestionIds: ['q2'],
        missed: [{ id: 'q2', promptMd: 'What is IAM?' }],
      }),
      'POST /api/v1/study/sessions': json(view({ id: 's2', total: 1, current: card }), 201),
      '/api/v1/study/sessions/s2': json(view({ id: 's2', total: 1, current: card })),
    });
    const user = userEvent.setup();
    const router = renderAt('/quiz/s1');

    await user.click(await screen.findByRole('button', { name: m.reveal }));
    await user.click(screen.getByRole('button', { name: m.grades.PARTIAL }));
    expect(bodyOf(fetchMock, '/api/v1/study/sessions/s1/answers')).toEqual({
      questionId: 'q2',
      grade: 'PARTIAL',
    });

    expect(await screen.findByRole('heading', { name: m.doneTitle })).toBeInTheDocument();
    expect(screen.getByText(m.score(1, 2))).toBeInTheDocument();
    expect(screen.getByText(m.breakdown(1, 0))).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: m.missedTitle })).toBeInTheDocument();
    expect(screen.getByText('What is IAM?')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: m.retryMissed(1) }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/quiz/s2'));
    expect(bodyOf(fetchMock, '/api/v1/study/sessions')).toEqual({
      kind: 'ADHOC',
      questionIds: ['q2'],
    });
  });

  it('resumes at the question the server says, after a reload', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': json(view({ answered: 1, current: card })),
    });
    renderAt('/quiz/s1');
    expect(await screen.findByText('What is IAM?')).toBeInTheDocument();
    expect(screen.getByText(m.progress(2, 2))).toBeInTheDocument();
  });

  it('marks every right option of a multiple-choice question', async () => {
    const multiple: QuestionCard = {
      ...single,
      id: 'q3',
      type: 'MULTIPLE_CHOICE',
      options: [
        { id: 'a', textMd: 'Cloud SQL' },
        { id: 'b', textMd: 'Compute Engine' },
        { id: 'c', textMd: 'Spanner' },
      ],
    };
    mockApi({
      '/api/me': me(),
      '/api/v1/study/sessions/s1': json(view({ total: 1, current: multiple })),
      'POST /api/v1/study/sessions/s1/answers': json({
        outcome: 'CORRECT',
        correctOptionIds: ['a', 'c'],
        answerMd: null,
        explanationMd: null,
        session: view({ total: 1, answered: 1, current: null }),
      }),
    });
    const user = userEvent.setup();
    renderAt('/quiz/s1');

    expect(await screen.findByText(m.chooseAll)).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Cloud SQL' }));
    await user.click(screen.getByRole('checkbox', { name: 'Spanner' }));
    await user.click(screen.getByRole('button', { name: m.check }));
    expect(await screen.findByText(m.correct, { selector: 'p.font-semibold' })).toBeInTheDocument();
    expect(screen.getAllByText(m.rightAnswer)).toHaveLength(2);
    expect(screen.getByRole('button', { name: m.results })).toBeInTheDocument();
  });
});

describe('Quizzes tab', () => {
  it('starts a quiz on a topic with count, mode and shuffle', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/topics': Response.json(topics),
      'POST /api/v1/study/sessions': json(view({ id: 's9' }), 201),
      '/api/v1/study/sessions/s9': json(view({ id: 's9' })),
    });
    const user = userEvent.setup();
    const router = renderAt('/quizzes');

    const start = await screen.findByRole('button', { name: q.start });
    expect(start).toBeDisabled();
    await screen.findByRole('option', { name: 'Languages › Java' });
    await user.selectOptions(screen.getByLabelText(q.topic), 'java');
    await user.click(screen.getByRole('radio', { name: q.counts['10'] }));
    await user.click(screen.getByRole('radio', { name: q.modes.MISSED }));
    await user.click(screen.getByRole('checkbox', { name: q.shuffle }));
    await user.click(start);

    await waitFor(() => expect(router.state.location.pathname).toBe('/quiz/s9'));
    expect(bodyOf(fetchMock, '/api/v1/study/sessions')).toEqual({
      kind: 'ADHOC',
      topicIds: ['java'],
      mode: 'MISSED',
      shuffle: false,
      count: 10,
    });
    expect(localStorage.getItem('quode.quiz.current')).toBe('s9');
  });

  it('leaves out the count for All', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/topics': Response.json(topics),
      'POST /api/v1/study/sessions': json(view({ id: 's9' }), 201),
      '/api/v1/study/sessions/s9': json(view({ id: 's9' })),
    });
    const user = userEvent.setup();
    renderAt('/quizzes');

    await screen.findByRole('option', { name: 'Languages › Java' });
    await user.selectOptions(screen.getByLabelText(q.topic), 'java');
    await user.click(
      within(screen.getByRole('group', { name: q.count })).getByRole('radio', {
        name: q.counts.all,
      }),
    );
    await user.click(screen.getByRole('button', { name: q.start }));
    await waitFor(() =>
      expect(bodyOf(fetchMock, '/api/v1/study/sessions')).toEqual({
        kind: 'ADHOC',
        topicIds: ['java'],
        mode: 'ALL',
        shuffle: true,
      }),
    );
  });

  it('hides Continue when the remembered quiz is finished', async () => {
    localStorage.setItem('quode.quiz.current', 's1');
    mockApi({
      '/api/me': me(),
      '/api/v1/topics': Response.json(topics),
      '/api/v1/study/sessions/s1': json(view({ answered: 2, current: null })),
    });
    renderAt('/quizzes');
    expect(await screen.findByRole('option', { name: 'Languages › Java' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: new RegExp(q.continue) })).not.toBeInTheDocument();
  });

  it('asks for topics first when there are none', async () => {
    mockApi({ '/api/me': me(), '/api/v1/topics': Response.json([]) });
    renderAt('/quizzes');
    expect(await screen.findByText(q.noTopics)).toBeInTheDocument();
  });

  it('offers to continue an unfinished quiz and preselects a topic from the Library', async () => {
    localStorage.setItem('quode.quiz.current', 's1');
    mockApi({
      '/api/me': me(),
      '/api/v1/topics': Response.json(topics),
      '/api/v1/study/sessions/s1': json(view({ answered: 1, current: card })),
    });
    renderAt('/quizzes?topicId=coll');

    expect(await screen.findByRole('link', { name: new RegExp(q.continue) })).toHaveAttribute(
      'href',
      '/quiz/s1',
    );
    expect(screen.getByText(q.continueHint(1, 2))).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText(q.topic)).toHaveValue('coll'));
  });
});
