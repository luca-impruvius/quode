import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { focusManager } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { messages } from '../messages';
import { problem, questionView, topics } from '../test/questionFixtures';
import { me, mockApi, renderAt } from '../test/renderApp';

const e = messages.editor;

type FetchMock = ReturnType<typeof mockApi>;

function bodyOf(fetchMock: FetchMock, method: string, url: string): Record<string, unknown> {
  const call = fetchMock.mock.calls.findLast(
    ([u, init]) => String(u) === url && init?.method === method,
  );
  return JSON.parse(String(call?.[1]?.body)) as Record<string, unknown>;
}

function backend(writes: Record<string, () => Response> = {}) {
  return mockApi({
    '/api/me': me(),
    '/api/v1/topics': Response.json(topics),
    '/api/v1/questions/q1': Response.json(questionView()),
    ...writes,
  });
}

async function chooseTopics(...paths: string[]) {
  await userEvent.click(screen.getByRole('button', { name: e.addTopic }));
  const sheet = screen.getByRole('dialog', { name: e.chooseTopics });
  for (const path of paths) {
    await userEvent.click(await within(sheet).findByRole('button', { name: path }));
  }
  await userEvent.click(within(sheet).getByRole('button', { name: e.done }));
}

async function openMoreOptions() {
  await userEvent.click(screen.getByRole('button', { name: e.moreOptions }));
}

describe('Editor', () => {
  it('creates a flashcard and opens it', async () => {
    const fetchMock = backend({
      'POST /api/v1/questions': () => Response.json(questionView(), { status: 201 }),
    });
    const router = renderAt('/add');

    await userEvent.type(await screen.findByLabelText(e.question), 'What is a HashMap?');
    await userEvent.type(screen.getByLabelText(e.answer), 'A hash table.');
    await chooseTopics('Languages › Java');
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    expect(await screen.findByText('HashMap', { selector: 'strong' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/questions/q1');
    expect(bodyOf(fetchMock, 'POST', '/api/v1/questions')).toEqual({
      type: 'FLASHCARD',
      promptMd: 'What is a HashMap?',
      answerMd: 'A hash table.',
      explanationMd: null,
      difficulty: 2,
      sourceUrl: null,
      topicIds: ['java'],
      primaryTopicId: 'java',
      options: [],
    });
  });

  it('shows rule messages at the fields and sends nothing', async () => {
    const fetchMock = backend();
    renderAt('/add');

    await userEvent.click(await screen.findByRole('button', { name: e.save }));

    expect(await screen.findByLabelText(e.question)).toHaveAccessibleDescription(
      'The question needs 1 to 2,000 characters.',
    );
    expect(screen.getByLabelText(e.answer)).toHaveAccessibleDescription(
      'This question type needs an answer.',
    );
    expect(screen.getByText('Choose 1 to 5 different topics.')).toBeVisible();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
  });

  it('creates a single-choice question with its options', async () => {
    const fetchMock = backend({
      'POST /api/v1/questions': () => Response.json(questionView(), { status: 201 }),
    });
    renderAt('/add');

    await userEvent.type(await screen.findByLabelText(e.question), 'Pick one');
    await chooseTopics('Languages › Java');
    await openMoreOptions();
    await userEvent.click(
      screen.getByRole('radio', { name: messages.questionTypes.SINGLE_CHOICE }),
    );
    await userEvent.type(screen.getByLabelText(e.option(1)), 'HashMap');
    await userEvent.type(screen.getByLabelText(e.option(2)), 'TreeMap');
    await userEvent.click(screen.getByRole('radio', { name: e.correctOption(2) }));
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    await screen.findByText('HashMap', { selector: 'strong' });
    expect(bodyOf(fetchMock, 'POST', '/api/v1/questions')).toMatchObject({
      type: 'SINGLE_CHOICE',
      answerMd: null,
      options: [
        { textMd: 'HashMap', correct: false },
        { textMd: 'TreeMap', correct: true },
      ],
    });
  });

  it('needs a correct option on a multiple-choice question', async () => {
    backend();
    renderAt('/add');

    await userEvent.type(await screen.findByLabelText(e.question), 'Pick some');
    await chooseTopics('Languages › Java');
    await openMoreOptions();
    await userEvent.click(
      screen.getByRole('radio', { name: messages.questionTypes.MULTIPLE_CHOICE }),
    );
    await userEvent.type(screen.getByLabelText(e.option(1)), 'A');
    await userEvent.type(screen.getByLabelText(e.option(2)), 'B');
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    expect(await screen.findByText('Mark at least one option as correct.')).toBeVisible();
  });

  it('keeps 2 to 6 options', async () => {
    backend();
    renderAt('/add');
    await screen.findByLabelText(e.question);
    await openMoreOptions();
    await userEvent.click(
      await screen.findByRole('radio', { name: messages.questionTypes.MULTIPLE_CHOICE }),
    );

    expect(screen.getByRole('button', { name: e.removeOption(1) })).toBeDisabled();
    const add = screen.getByRole('button', { name: e.addOption });
    for (let i = 0; i < 4; i++) {
      await userEvent.click(add);
    }
    expect(screen.getByLabelText(e.option(6))).toBeInTheDocument();
    expect(add).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: e.removeOption(6) }));
    expect(screen.queryByLabelText(e.option(6))).not.toBeInTheDocument();
  });

  it('lets me pick the primary topic', async () => {
    const fetchMock = backend({
      'POST /api/v1/questions': () => Response.json(questionView(), { status: 201 }),
    });
    renderAt('/add');

    await userEvent.type(await screen.findByLabelText(e.question), 'Q');
    await userEvent.type(screen.getByLabelText(e.answer), 'A');
    await chooseTopics('Languages › Java', 'Languages › Java › Collections');
    await userEvent.click(screen.getByRole('button', { name: e.makePrimary('Collections') }));
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    await screen.findByText('HashMap', { selector: 'strong' });
    expect(bodyOf(fetchMock, 'POST', '/api/v1/questions')).toMatchObject({
      topicIds: ['java', 'coll'],
      primaryTopicId: 'coll',
    });
  });

  it('previews Markdown', async () => {
    backend();
    renderAt('/add');

    await userEvent.type(await screen.findByLabelText(e.question), 'Use **volatile**');
    const view = screen.getByRole('radiogroup', { name: e.viewOf(e.question) });
    await userEvent.click(within(view).getByRole('radio', { name: e.preview }));

    expect(await screen.findByText('volatile', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.queryByLabelText(e.question)).not.toBeInTheDocument();
  });

  it('saves and starts the next question, keeping topics and type', async () => {
    const fetchMock = backend({
      'POST /api/v1/questions': () => Response.json(questionView(), { status: 201 }),
    });
    const router = renderAt('/add');

    await userEvent.type(await screen.findByLabelText(e.question), 'First');
    await userEvent.type(screen.getByLabelText(e.answer), 'One');
    await chooseTopics('Languages › Java');
    await userEvent.click(screen.getByRole('button', { name: e.saveAndAdd }));

    expect(await screen.findByRole('status')).toHaveTextContent(e.saved);
    expect(router.state.location.pathname).toBe('/add');
    expect(screen.getByLabelText(e.question)).toHaveValue('');
    expect(screen.getByLabelText(e.answer)).toHaveValue('');
    expect(screen.getByText('Languages › Java', { exact: false })).toBeInTheDocument();
    expect(bodyOf(fetchMock, 'POST', '/api/v1/questions')).toMatchObject({ promptMd: 'First' });
  });

  it('edits a question with the version it read', async () => {
    const fetchMock = backend({
      'PUT /api/v1/questions/q1': () => Response.json(questionView({ version: 4 })),
    });
    const router = renderAt('/questions/q1/edit');

    const prompt = await screen.findByLabelText(e.question);
    expect(prompt).toHaveValue('How does **HashMap** handle collisions?');
    await userEvent.clear(prompt);
    await userEvent.type(prompt, 'Changed');
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    await screen.findByRole('link', { name: messages.question.edit });
    expect(router.state.location.pathname).toBe('/questions/q1');
    expect(bodyOf(fetchMock, 'PUT', '/api/v1/questions/q1')).toMatchObject({
      promptMd: 'Changed',
      version: 3,
      topicIds: ['coll', 'java'],
      primaryTopicId: 'coll',
    });
  });

  it('keeps my text on a stale save and reloads the latest on request', async () => {
    let latest = questionView();
    mockApi({
      '/api/me': me(),
      '/api/v1/topics': Response.json(topics),
      '/api/v1/questions/q1': () => Response.json(latest),
      'PUT /api/v1/questions/q1': () =>
        problem(409, 'This question changed elsewhere. Reload to see the latest version.'),
    });
    renderAt('/questions/q1/edit');

    const prompt = await screen.findByLabelText(e.question);
    await userEvent.clear(prompt);
    await userEvent.type(prompt, 'My edit');
    latest = questionView({ promptMd: 'Their edit', version: 4 });
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    expect(await screen.findByText(e.stale)).toBeVisible();
    expect(prompt).toHaveValue('My edit');

    await userEvent.click(screen.getByRole('button', { name: e.reload }));

    expect(await screen.findByDisplayValue('Their edit')).toBeInTheDocument();
    expect(screen.queryByText(e.stale)).not.toBeInTheDocument();
  });

  it("shows the server's rule message", async () => {
    backend({
      'POST /api/v1/questions': () => problem(404, 'Topic not found.'),
    });
    renderAt('/add');

    await userEvent.type(await screen.findByLabelText(e.question), 'Q');
    await userEvent.type(screen.getByLabelText(e.answer), 'A');
    await chooseTopics('Languages › Java');
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Topic not found.');
  });

  it('never swaps the form for a newer server version on its own', async () => {
    let latest = questionView();
    const fetchMock = mockApi({
      '/api/me': me(),
      '/api/v1/topics': Response.json(topics),
      '/api/v1/questions/q1': () => Response.json(latest),
    });
    renderAt('/questions/q1/edit');
    const prompt = await screen.findByLabelText(e.question);
    await userEvent.clear(prompt);
    await userEvent.type(prompt, 'Typing away');

    latest = questionView({ promptMd: 'Changed elsewhere', version: 4 });
    focusManager.setFocused(false);
    focusManager.setFocused(true);
    window.dispatchEvent(new Event('online'));

    expect(prompt).toHaveValue('Typing away');
    const gets = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/v1/questions/q1');
    expect(gets).toHaveLength(1);
  });

  it('opens More options when a field in it is wrong', async () => {
    backend();
    renderAt('/add');

    await userEvent.type(await screen.findByLabelText(e.question), 'Q');
    await userEvent.type(screen.getByLabelText(e.answer), 'A');
    await chooseTopics('Languages › Java');
    await openMoreOptions();
    await userEvent.type(screen.getByLabelText(e.source), 'not a link');
    await openMoreOptions();
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    expect(await screen.findByLabelText(e.source)).toHaveAccessibleDescription(
      'The source link must start with http:// or https:// (up to 2,000 characters).',
    );
    expect(screen.getByRole('button', { name: e.moreOptions })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
  });

  it('asks for exactly one correct option after switching to single choice', async () => {
    backend();
    renderAt('/add');
    await userEvent.type(await screen.findByLabelText(e.question), 'Q');
    await chooseTopics('Languages › Java');
    await openMoreOptions();
    await userEvent.click(
      screen.getByRole('radio', { name: messages.questionTypes.MULTIPLE_CHOICE }),
    );
    await userEvent.type(screen.getByLabelText(e.option(1)), 'A');
    await userEvent.type(screen.getByLabelText(e.option(2)), 'B');
    await userEvent.click(screen.getByRole('checkbox', { name: e.correctOption(1) }));
    await userEvent.click(screen.getByRole('checkbox', { name: e.correctOption(2) }));

    await userEvent.click(
      screen.getByRole('radio', { name: messages.questionTypes.SINGLE_CHOICE }),
    );
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    expect(await screen.findByText('Mark exactly one option as correct.')).toBeVisible();
  });

  it('sends no options after switching back to a flashcard', async () => {
    const fetchMock = backend({
      'POST /api/v1/questions': () => Response.json(questionView(), { status: 201 }),
    });
    renderAt('/add');
    await userEvent.type(await screen.findByLabelText(e.question), 'Q');
    await chooseTopics('Languages › Java');
    await openMoreOptions();
    await userEvent.click(
      screen.getByRole('radio', { name: messages.questionTypes.SINGLE_CHOICE }),
    );
    await userEvent.type(screen.getByLabelText(e.option(1)), 'A');
    await userEvent.click(screen.getByRole('radio', { name: messages.questionTypes.FLASHCARD }));
    await userEvent.type(screen.getByLabelText(e.answer), 'Answer');
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    await screen.findByText('HashMap', { selector: 'strong' });
    expect(bodyOf(fetchMock, 'POST', '/api/v1/questions')).toMatchObject({
      type: 'FLASHCARD',
      options: [],
    });
  });

  it('moves primary to the next topic when the primary is removed', async () => {
    const fetchMock = backend({
      'POST /api/v1/questions': () => Response.json(questionView(), { status: 201 }),
    });
    renderAt('/add');
    await userEvent.type(await screen.findByLabelText(e.question), 'Q');
    await userEvent.type(screen.getByLabelText(e.answer), 'A');
    await chooseTopics('Languages › Java', 'Languages › Java › Collections');

    await userEvent.click(screen.getByRole('button', { name: e.removeTopic('Java') }));
    expect(screen.getByText(`Languages › Java › Collections · ${e.primary}`)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: e.save }));

    await screen.findByText('HashMap', { selector: 'strong' });
    expect(bodyOf(fetchMock, 'POST', '/api/v1/questions')).toMatchObject({
      topicIds: ['coll'],
      primaryTopicId: 'coll',
    });
  });

  it('puts focus on the question after Save & add another', async () => {
    backend({
      'POST /api/v1/questions': () => Response.json(questionView(), { status: 201 }),
    });
    renderAt('/add');
    await userEvent.type(await screen.findByLabelText(e.question), 'First');
    await userEvent.type(screen.getByLabelText(e.answer), 'One');
    await chooseTopics('Languages › Java');

    await userEvent.click(screen.getByRole('button', { name: e.saveAndAdd }));

    await screen.findByRole('status');
    expect(screen.getByLabelText(e.question)).toHaveFocus();
  });

  it('opens from the tab bar', async () => {
    backend();
    renderAt('/');

    await userEvent.click(await screen.findByRole('link', { name: messages.tabs.add }));

    expect(await screen.findByRole('heading', { name: e.newTitle })).toBeInTheDocument();
  });
});
