import { screen, within } from '@testing-library/react';
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

  it('explains a stale archive', async () => {
    mockApi({
      '/api/me': me(),
      '/api/v1/questions/q1': Response.json(questionView()),
      'POST /api/v1/questions/q1/archive': problem(
        409,
        'This question changed elsewhere. Reload to see the latest version.',
      ),
    });
    renderAt('/questions/q1');

    await userEvent.click(await screen.findByRole('button', { name: d.archive }));

    expect(await screen.findByRole('alert')).toHaveTextContent('This question changed elsewhere.');
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
