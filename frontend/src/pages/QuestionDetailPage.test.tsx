import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { messages } from '../messages';
import { problem, questionView } from '../test/questionFixtures';
import { me, mockApi, renderAt } from '../test/renderApp';

const d = messages.question;

describe('Question detail', () => {
  it('shows the question with rendered Markdown, its topics and facts', async () => {
    mockApi({
      '/api/me': me(),
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

  it('limits the note to 200 characters', async () => {
    mockApi({ '/api/me': me(), '/api/v1/questions/q1': Response.json(questionView()) });
    renderAt('/questions/q1');

    await userEvent.click(await screen.findByRole('button', { name: d.flag }));

    expect(screen.getByLabelText(d.flagNote)).toHaveAttribute('maxLength', '200');
  });

  it('unflags with the version it read', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
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

function renderWithContainer() {
  renderAt('/questions/q1');
  return { container: document.body };
}
