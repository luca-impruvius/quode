import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { QuestionReview } from '../api/review';
import { messages } from '../messages';
import { problem, questionView } from '../test/questionFixtures';
import { me, mockApi, renderAt } from '../test/renderApp';

const d = messages.question;

const reviewOf = (overrides: Partial<QuestionReview> = {}) =>
  Response.json({ enrolled: true, learnNext: false, dueAt: null, isNew: true, ...overrides });

// Today is 25 Nov 2026 in Bucharest (UTC+2); a due day starts at 22:00 UTC the day before.
const NOW = new Date('2026-11-25T10:00:00Z');

describe('Question detail', () => {
  it('shows the question with rendered Markdown, its topics and facts', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': Response.json(
        questionView({ sourceUrl: 'https://docs.oracle.com/java' }),
      ),
    });

    renderAt('/questions/q1');

    // Markdown is lazy-loaded: wait for the rendered emphasis.
    expect(await screen.findByText('HashMap', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByText('chained', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByText(`Languages › Java › Collections · ${d.primary}`)).toBeInTheDocument();
    expect(screen.getByText('Languages › Java')).toBeInTheDocument();
    expect(screen.getByText(messages.questionTypeNames.FLASHCARD)).toBeInTheDocument();
    expect(screen.getByText(d.difficultyValue(2))).toBeInTheDocument();
    expect(screen.getByText(d.active)).toBeInTheDocument();
    const source = screen.getByRole('link', { name: 'docs.oracle.com' });
    expect(source).toHaveAttribute('href', 'https://docs.oracle.com/java');
    expect(source).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.getByRole('link', { name: d.edit })).toHaveAttribute(
      'href',
      '/questions/q1/edit',
    );
  });

  it('marks the correct options with a word, not only a colour', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': Response.json(
        questionView({
          type: 'MULTIPLE_CHOICE',
          answerMd: null,
          options: [
            { id: 'o1', textMd: 'HashMap', correct: false },
            { id: 'o2', textMd: 'ConcurrentHashMap', correct: true },
          ],
        }),
      ),
    });

    renderAt('/questions/q1');

    const options = await screen.findByRole('list', { name: d.options });
    const items = within(options).getAllByRole('listitem');
    expect(items[0]).not.toHaveTextContent(d.correct);
    expect(
      await within(items[1] as HTMLElement).findByText('ConcurrentHashMap'),
    ).toBeInTheDocument();
    expect(items[1]).toHaveTextContent(d.correct);
  });

  it('never renders raw HTML from Markdown', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': Response.json(
        questionView({
          answerMd: 'Safe <img src="x" onerror="alert(1)"> text <script>alert(2)</script>',
        }),
      ),
    });

    const { container } = renderWithContainer();

    expect(await screen.findByText(/Safe/)).toBeInTheDocument();
    expect(container.querySelector('img[src="x"]')).toBeNull();
    expect(container.querySelector('script')).toBeNull();
  });

  it('never links to javascript: and shows images as links', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': Response.json(
        questionView({
          answerMd: '[click](javascript:alert(1)) and ![pixel](https://tracker.example/p.gif)',
        }),
      ),
    });

    renderAt('/questions/q1');

    const click = await screen.findByText('click');
    expect(click.closest('a')?.getAttribute('href') ?? '').not.toMatch(/^javascript:/i);
    expect(document.querySelector('img[src*="tracker"]')).toBeNull();
    expect(screen.getByRole('link', { name: 'pixel' })).toHaveAttribute(
      'href',
      'https://tracker.example/p.gif',
    );
  });

  it('unarchives with the version it read', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': Response.json(
        questionView({ archivedAt: '2026-10-31T11:00:00Z', version: 7 }),
      ),
      'POST /api/v1/questions/q1/unarchive': () => Response.json(questionView({ version: 8 })),
    });
    renderAt('/questions/q1');

    await userEvent.click(await screen.findByRole('button', { name: d.unarchive }));

    expect(await screen.findByRole('button', { name: d.archive })).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/unarchive'));
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ version: 7 });
  });

  it('archives with the version it read, then offers Unarchive', async () => {
    let archived = false;
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': () =>
        Response.json(
          questionView(archived ? { archivedAt: '2026-10-31T11:00:00Z', version: 4 } : {}),
        ),
      'POST /api/v1/questions/q1/archive': () => {
        archived = true;
        return Response.json(questionView({ archivedAt: '2026-10-31T11:00:00Z', version: 4 }));
      },
    });
    renderAt('/questions/q1');

    await userEvent.click(await screen.findByRole('button', { name: d.archive }));

    expect(await screen.findByRole('button', { name: d.unarchive })).toBeInTheDocument();
    expect(screen.getByText(d.archivedBanner)).toBeInTheDocument();
    expect(screen.getByText(d.archived)).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/archive'));
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ version: 3 });
  });

  it('explains a stale archive and loads the latest version for the next try', async () => {
    let gets = 0;
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': () => {
        gets += 1;
        return Response.json(questionView({ version: gets === 1 ? 3 : 4 }));
      },
      'POST /api/v1/questions/q1/archive': problem(
        409,
        'This question changed elsewhere. Reload to see the latest version.',
      ),
    });
    renderAt('/questions/q1');

    await userEvent.click(await screen.findByRole('button', { name: d.archive }));

    expect(await screen.findByRole('alert')).toHaveTextContent('This question changed elsewhere.');
    await waitFor(() => expect(gets).toBe(2));
  });

  it('flags the question with a note, then shows it', async () => {
    let flagged = false;
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': () =>
        Response.json(
          questionView(
            flagged
              ? { flaggedAt: '2026-10-31T12:00:00Z', flagNote: 'Typo in the answer', version: 4 }
              : {},
          ),
        ),
      'POST /api/v1/questions/q1/flag': () => {
        flagged = true;
        return Response.json(
          questionView({
            flaggedAt: '2026-10-31T12:00:00Z',
            flagNote: 'Typo in the answer',
            version: 4,
          }),
        );
      },
    });
    renderAt('/questions/q1');

    await userEvent.click(await screen.findByRole('button', { name: d.flag }));
    const sheet = screen.getByRole('dialog', { name: d.flagTitle });
    await userEvent.type(within(sheet).getByLabelText(d.flagNote), 'Typo in the answer');
    expect(within(sheet).getByText(d.flagNoteHint(200 - 18))).toBeInTheDocument();
    await userEvent.click(within(sheet).getByRole('button', { name: d.flagSave }));

    expect(await screen.findByText(d.flagged)).toBeInTheDocument();
    expect(screen.getByText('Typo in the answer')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: d.flag })).not.toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/flag'));
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ version: 3, note: 'Typo in the answer' });
  });

  it('limits the note to 200 characters, counting an emoji once', async () => {
    mockApi({ '/api/me': me(), '/api/v1/questions/q1': Response.json(questionView()) });
    renderAt('/questions/q1');
    await userEvent.click(await screen.findByRole('button', { name: d.flag }));
    const note = screen.getByLabelText(d.flagNote);

    fireEvent.change(note, { target: { value: '🦆'.repeat(150) } });
    expect(screen.getByText(d.flagNoteHint(50))).toBeInTheDocument();

    fireEvent.change(note, { target: { value: 'x'.repeat(250) } });
    expect(note).toHaveValue('x'.repeat(200));
    expect(screen.getByText(d.flagNoteHint(0))).toBeInTheDocument();
  });

  it('keeps focus on the banner after flagging and on the button after unflagging', async () => {
    let flagged = false;
    const view = () =>
      questionView(
        flagged ? { flaggedAt: '2026-10-31T12:00:00Z', flagNote: null, version: 4 } : {},
      );
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': () => Response.json(view()),
      'POST /api/v1/questions/q1/flag': () => {
        flagged = true;
        return Response.json(view());
      },
      'POST /api/v1/questions/q1/unflag': () => {
        flagged = false;
        return Response.json(view());
      },
    });
    renderAt('/questions/q1');

    await userEvent.click(await screen.findByRole('button', { name: d.flag }));
    await userEvent.click(screen.getByRole('button', { name: d.flagSave }));
    await waitFor(() => expect(screen.getByText(d.flagged).closest('p')).toHaveFocus());

    await userEvent.click(screen.getByRole('button', { name: d.unflag }));
    await waitFor(() => expect(screen.getByRole('button', { name: d.flag })).toHaveFocus());
  });

  it('loads the latest version after a stale flag', async () => {
    let gets = 0;
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': () => {
        gets += 1;
        return Response.json(questionView({ version: gets === 1 ? 3 : 4 }));
      },
      'POST /api/v1/questions/q1/flag': problem(
        409,
        'This question changed elsewhere. Reload to see the latest version.',
      ),
    });
    renderAt('/questions/q1');

    await userEvent.click(await screen.findByRole('button', { name: d.flag }));
    await userEvent.click(screen.getByRole('button', { name: d.flagSave }));

    expect(await screen.findByRole('alert')).toHaveTextContent('This question changed elsewhere.');
    await waitFor(() => expect(gets).toBe(2));
  });

  it('unflags with the version it read', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': Response.json(
        questionView({ flaggedAt: '2026-10-31T12:00:00Z', flagNote: 'Fix me', version: 5 }),
      ),
      'POST /api/v1/questions/q1/unflag': () => Response.json(questionView({ version: 6 })),
    });
    renderAt('/questions/q1');

    await userEvent.click(await screen.findByRole('button', { name: d.unflag }));

    expect(await screen.findByRole('button', { name: d.flag })).toBeInTheDocument();
    expect(screen.queryByText(d.flagged)).not.toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/unflag'));
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ version: 5 });
  });

  it("says when the question doesn't exist", async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/nope': problem(404, 'Question not found.'),
    });

    renderAt('/questions/nope');

    expect(await screen.findByRole('alert')).toHaveTextContent('Question not found.');
    expect(screen.getByRole('link', { name: d.back })).toHaveAttribute('href', '/library');
  });
});

describe('Question detail review controls', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  function bodyOfPut(fetchMock: ReturnType<typeof mockApi>) {
    const put = fetchMock.mock.calls.findLast(
      ([url, init]) => String(url) === '/api/v1/questions/q1/review' && init?.method === 'PUT',
    );
    return JSON.parse(String(put?.[1]?.body)) as unknown;
  }

  function nextReview() {
    return screen.findByText(d.nextReview);
  }

  it.each([
    ['2026-11-25T22:00:00Z', d.nextReviewIn(1)],
    ['2026-11-27T22:00:00Z', d.nextReviewIn(3)],
    ['2026-11-24T22:00:00Z', d.nextReviewIn(0)],
    ['2026-11-20T22:00:00Z', d.nextReviewIn(0)],
  ])('shows a scheduled question due at %s as "%s"', async (dueAt, label) => {
    vi.useFakeTimers({ now: NOW, toFake: ['Date'] });
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf({ isNew: false, dueAt }),
      '/api/v1/questions/q1': Response.json(questionView()),
    });

    renderAt('/questions/q1');

    const term = await nextReview();
    expect(await within(term.parentElement as HTMLElement).findByText(label)).toBeInTheDocument();
    // Learn next only orders new questions.
    expect(screen.queryByRole('switch', { name: d.learnNext })).not.toBeInTheDocument();
  });

  it('words the days relative to today', () => {
    expect(d.nextReviewIn(1)).toBe('tomorrow');
    expect(d.nextReviewIn(3)).toBe('in 3 days');
    expect(d.nextReviewIn(0)).toBe('today');
  });

  it('shows New and turns Learn next on and off', async () => {
    let learnNext = false;
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': () => reviewOf({ learnNext }),
      'PUT /api/v1/questions/q1/review': () => {
        learnNext = !learnNext;
        return reviewOf({ learnNext });
      },
      '/api/v1/questions/q1': Response.json(questionView()),
      '/api/v1/review/today': Response.json({}),
    });
    const user = userEvent.setup();
    renderAt('/questions/q1');

    const term = await nextReview();
    expect(await within(term.parentElement as HTMLElement).findByText(d.isNew)).toBeInTheDocument();
    const toggle = await screen.findByRole('switch', { name: d.learnNext });
    expect(toggle).toHaveAttribute('aria-checked', 'false');

    await user.click(toggle);
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'));
    expect(bodyOfPut(fetchMock)).toEqual({ enrolled: true, learnNext: true });

    await user.click(toggle);
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'false'));
    expect(bodyOfPut(fetchMock)).toEqual({ enrolled: true, learnNext: false });
  });

  it('removes the question from review, then adds it back as new', async () => {
    let enrolled = true;
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': () =>
        enrolled ? reviewOf({ isNew: false, dueAt: '2026-11-27T22:00:00Z' }) : reviewOf(),
      'PUT /api/v1/questions/q1/review': () => {
        enrolled = !enrolled;
        return enrolled
          ? reviewOf()
          : reviewOf({ enrolled: false, isNew: false, learnNext: false });
      },
      '/api/v1/questions/q1': Response.json(questionView()),
      '/api/v1/review/today': Response.json({}),
    });
    const user = userEvent.setup();
    renderAt('/questions/q1');

    await user.click(await screen.findByRole('button', { name: d.removeFromReview }));
    // A reviewed question loses its history: ask first.
    const dialog = await screen.findByRole('dialog', { name: d.removeTitle });
    expect(dialog).toHaveTextContent(d.removeBody);
    await user.click(within(dialog).getByRole('button', { name: d.removeConfirm }));

    expect(await screen.findByText(d.notInReview)).toBeInTheDocument();
    expect(bodyOfPut(fetchMock)).toEqual({ enrolled: false, learnNext: false });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('switch', { name: d.learnNext })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: d.addToReview }));

    expect(await screen.findByText(d.isNew)).toBeInTheDocument();
    expect(bodyOfPut(fetchMock)).toEqual({ enrolled: true, learnNext: false });
    expect(screen.getByRole('button', { name: d.removeFromReview })).toBeInTheDocument();
  });

  it('keeps a reviewed question in review when the removal is cancelled', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf({ isNew: false, dueAt: '2026-11-27T22:00:00Z' }),
      '/api/v1/questions/q1': Response.json(questionView()),
    });
    const user = userEvent.setup();
    renderAt('/questions/q1');

    await user.click(await screen.findByRole('button', { name: d.removeFromReview }));
    const dialog = await screen.findByRole('dialog', { name: d.removeTitle });
    await user.click(within(dialog).getByRole('button', { name: d.cancel }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
  });

  it('removes a new question at once: it has no history to lose', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      'PUT /api/v1/questions/q1/review': () =>
        reviewOf({ enrolled: false, isNew: false, learnNext: false }),
      '/api/v1/questions/q1': Response.json(questionView()),
      '/api/v1/review/today': Response.json({}),
    });
    const user = userEvent.setup();
    renderAt('/questions/q1');

    await user.click(await screen.findByRole('button', { name: d.removeFromReview }));

    expect(await screen.findByText(d.notInReview)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(bodyOfPut(fetchMock)).toEqual({ enrolled: false, learnNext: false });
  });

  it('has no review controls on an archived question', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      '/api/v1/questions/q1': Response.json(questionView({ archivedAt: '2026-11-20T10:00:00Z' })),
    });

    renderAt('/questions/q1');

    expect(await screen.findByRole('button', { name: d.unarchive })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: d.removeFromReview })).not.toBeInTheDocument();
    expect(screen.queryByRole('switch', { name: d.learnNext })).not.toBeInTheDocument();
  });

  it('shows the reference when saving fails, and stays usable', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1/review': reviewOf(),
      'PUT /api/v1/questions/q1/review': () =>
        problem(500, 'boom', 'abcdef0123456789abcdef0123456789'),
      '/api/v1/questions/q1': Response.json(questionView()),
    });
    const user = userEvent.setup();
    renderAt('/questions/q1');

    await user.click(await screen.findByRole('button', { name: d.removeFromReview }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.errors.reference('abcdef'));
    expect(screen.getByRole('button', { name: d.removeFromReview })).toBeEnabled();
  });
});

function renderWithContainer() {
  renderAt('/questions/q1');
  return { container: document.body };
}
