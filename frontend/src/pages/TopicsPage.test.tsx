import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { messages } from '../messages';
import { me, mockApi, renderAt } from '../test/renderApp';

const t = messages.topics;

interface Topic {
  id: string;
  parentId: string | null;
  name: string;
  questionCount: number;
}

const tree: Topic[] = [
  { id: 'lang', parentId: null, name: 'Languages', questionCount: 3 },
  { id: 'java', parentId: 'lang', name: 'Java', questionCount: 3 },
  { id: 'coll', parentId: 'java', name: 'Collections', questionCount: 2 },
  { id: 'web', parentId: null, name: 'Web', questionCount: 0 },
];

const problem = (status: number, detail: string) =>
  Response.json(
    { status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );

// The topics endpoint returns whatever `state.topics` holds at request time, so a test can change it
// in a write handler and see the screen refetch.
function backend(initial: Topic[], writes: Record<string, () => Response> = {}) {
  const state = { topics: initial };
  const fetchMock = mockApi({
    '/api/me': me(),
    '/api/v1/topics': () => Response.json(state.topics),
    ...writes,
  });
  return { state, fetchMock };
}

function bodyOf(fetchMock: ReturnType<typeof mockApi>, method: string, url: string): unknown {
  const call = fetchMock.mock.calls.find(
    ([u, init]) => String(u) === url && init?.method === method,
  );
  return JSON.parse(String(call?.[1]?.body));
}

describe('Topics', () => {
  it('offers starter topics when the list is empty', async () => {
    const { state } = backend([], {
      'POST /api/v1/topics/starter': () => {
        state.topics = tree;
        return Response.json(tree, { status: 201 });
      },
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.addStarter }));

    expect(await screen.findByText('Languages')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: t.addStarter })).not.toBeInTheDocument();
  });

  it('shows the tree with counts, collapsed below the top level', async () => {
    backend(tree);
    renderAt('/topics');

    const languages = await screen.findByRole('listitem', { name: 'Languages' });
    expect(within(languages).getByText('3')).toBeInTheDocument();
    expect(screen.queryByText('Java')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: t.expand('Languages') }));
    await userEvent.click(screen.getByRole('button', { name: t.expand('Java') }));

    expect(screen.getByRole('listitem', { name: 'Collections' })).toHaveTextContent('2');
    expect(screen.getByRole('button', { name: t.collapse('Languages') })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    // Web has no sub-topics, so nothing to expand.
    expect(screen.queryByRole('button', { name: t.expand('Web') })).not.toBeInTheDocument();
  });

  it('creates a topic inside another one', async () => {
    const { state, fetchMock } = backend(tree, {
      'POST /api/v1/topics': () => {
        state.topics = [...tree, { id: 'kt', parentId: 'lang', name: 'Kotlin', questionCount: 0 }];
        return Response.json(state.topics.at(-1), { status: 201 });
      },
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.new }));
    const dialog = screen.getByRole('dialog', { name: t.newTitle });
    await userEvent.type(within(dialog).getByLabelText(t.name), 'Kotlin');
    await userEvent.selectOptions(within(dialog).getByLabelText(t.inside), 'Languages');
    await userEvent.click(within(dialog).getByRole('button', { name: t.save }));

    expect(bodyOf(fetchMock, 'POST', '/api/v1/topics')).toEqual({
      name: 'Kotlin',
      parentId: 'lang',
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    // The parent opens by itself, so the new topic is visible.
    expect(await screen.findByText('Kotlin')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: t.collapse('Languages') })).toBeInTheDocument();
  });

  it('shows a rule error next to the name and keeps the form open', async () => {
    backend(tree, {
      'POST /api/v1/topics': () => problem(409, 'A topic with this name already exists here.'),
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.new }));
    const dialog = screen.getByRole('dialog', { name: t.newTitle });
    const name = within(dialog).getByLabelText(t.name);
    await userEvent.type(name, 'web');
    await userEvent.click(within(dialog).getByRole('button', { name: t.save }));

    expect(
      await within(dialog).findByText('A topic with this name already exists here.'),
    ).toBeVisible();
    expect(name).toHaveAttribute('aria-invalid', 'true');
    expect(name).toHaveValue('web');
  });

  it('renames a topic', async () => {
    const { state, fetchMock } = backend(tree, {
      'PUT /api/v1/topics/web/name': () => {
        state.topics = tree.map((topic) =>
          topic.id === 'web' ? { ...topic, name: 'Web dev' } : topic,
        );
        return Response.json(state.topics[3]);
      },
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.actionsFor('Web') }));
    await userEvent.click(screen.getByRole('button', { name: t.rename }));
    const name = screen.getByLabelText(t.name);
    expect(name).toHaveValue('Web');
    await userEvent.clear(name);
    await userEvent.type(name, 'Web dev');
    await userEvent.click(screen.getByRole('button', { name: t.save }));

    expect(bodyOf(fetchMock, 'PUT', '/api/v1/topics/web/name')).toEqual({ name: 'Web dev' });
    expect(await screen.findByText('Web dev')).toBeInTheDocument();
  });

  it('moves a topic, never offering itself or its sub-topics as the new parent', async () => {
    const { fetchMock } = backend(tree, {
      'PUT /api/v1/topics/lang/parent': () => Response.json(tree[0]),
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.actionsFor('Languages') }));
    await userEvent.click(screen.getByRole('button', { name: t.move }));
    const select = screen.getByLabelText(t.moveUnder);
    const options = within(select)
      .getAllByRole('option')
      .map((option) => option.textContent);
    expect(options).toEqual([t.topLevel, 'Web']);

    await userEvent.selectOptions(select, 'Web');
    await userEvent.click(screen.getByRole('button', { name: t.save }));

    expect(bodyOf(fetchMock, 'PUT', '/api/v1/topics/lang/parent')).toEqual({ parentId: 'web' });
  });

  it('moves a topic to the top level with an explicit null', async () => {
    const { fetchMock } = backend(tree, {
      'PUT /api/v1/topics/java/parent': () => Response.json(tree[1]),
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.expand('Languages') }));
    await userEvent.click(screen.getByRole('button', { name: t.actionsFor('Java') }));
    await userEvent.click(screen.getByRole('button', { name: t.move }));
    await userEvent.selectOptions(screen.getByLabelText(t.moveUnder), t.topLevel);
    await userEvent.click(screen.getByRole('button', { name: t.save }));

    expect(bodyOf(fetchMock, 'PUT', '/api/v1/topics/java/parent')).toEqual({ parentId: null });
  });

  it('deletes a topic after confirming', async () => {
    const { state } = backend(tree, {
      'DELETE /api/v1/topics/web': () => {
        state.topics = tree.filter((topic) => topic.id !== 'web');
        return new Response(null, { status: 204 });
      },
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.actionsFor('Web') }));
    await userEvent.click(screen.getByRole('button', { name: t.delete }));
    const dialog = screen.getByRole('dialog', { name: t.deleteTitle('Web') });
    await userEvent.click(within(dialog).getByRole('button', { name: t.delete }));

    await screen.findByRole('listitem', { name: 'Languages' });
    expect(screen.queryByText('Web')).not.toBeInTheDocument();
  });

  it("explains why a topic can't be deleted", async () => {
    backend(tree, {
      'DELETE /api/v1/topics/lang': () => problem(409, 'Move or delete its sub-topics first.'),
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.actionsFor('Languages') }));
    await userEvent.click(screen.getByRole('button', { name: t.delete }));
    const dialog = screen.getByRole('dialog', { name: t.deleteTitle('Languages') });
    await userEvent.click(within(dialog).getByRole('button', { name: t.delete }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Move or delete its sub-topics first.',
    );
  });

  it('shows a move rule error at the picker and keeps the sheet open', async () => {
    backend(tree, {
      'PUT /api/v1/topics/web/parent': () =>
        problem(409, 'A topic with this name already exists here.'),
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.actionsFor('Web') }));
    await userEvent.click(screen.getByRole('button', { name: t.move }));
    const dialog = screen.getByRole('dialog', { name: t.moveTitle('Web') });
    const select = within(dialog).getByLabelText(t.moveUnder);
    await userEvent.selectOptions(select, 'Languages');
    await userEvent.click(within(dialog).getByRole('button', { name: t.save }));

    expect(
      await within(dialog).findByText('A topic with this name already exists here.'),
    ).toBeVisible();
    expect(select).toHaveAttribute('aria-invalid', 'true');
    expect(select).toHaveAccessibleDescription('A topic with this name already exists here.');
  });

  it('shows the reference, not a server sentence, when saving fails on the server', async () => {
    backend(tree, {
      'POST /api/v1/topics': () =>
        Response.json(
          { status: 500, detail: 'Something went wrong.', traceId: 'abcdef0123456789' },
          { status: 500 },
        ),
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.new }));
    await userEvent.type(screen.getByLabelText(t.name), 'Kotlin');
    await userEvent.click(screen.getByRole('button', { name: t.save }));

    expect(await screen.findByText(messages.errors.reference('abcdef'))).toBeVisible();
  });

  it('announces counts as questions', async () => {
    backend(tree);
    renderAt('/topics');

    expect(await screen.findByRole('listitem', { name: 'Languages' })).toHaveTextContent(
      t.questions(3),
    );
  });

  it('closes a sheet with Escape or a tap on the backdrop, and returns focus', async () => {
    backend(tree);
    renderAt('/topics');
    const newButton = await screen.findByRole('button', { name: t.new });

    await userEvent.click(newButton);
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(newButton).toHaveFocus();

    await userEvent.click(newButton);
    const backdrop = screen.getByRole('dialog').parentElement as HTMLElement;
    await userEvent.click(backdrop);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('keeps Tab inside the sheet', async () => {
    backend(tree);
    renderAt('/topics');
    await userEvent.click(await screen.findByRole('button', { name: t.new }));
    const dialog = screen.getByRole('dialog');

    expect(within(dialog).getByLabelText(t.name)).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(within(dialog).getByRole('button', { name: t.save })).toHaveFocus();
    await userEvent.tab();
    expect(within(dialog).getByLabelText(t.name)).toHaveFocus();
  });

  it('puts focus on the heading after a delete', async () => {
    const { state } = backend(tree, {
      'DELETE /api/v1/topics/web': () => {
        state.topics = tree.filter((topic) => topic.id !== 'web');
        return new Response(null, { status: 204 });
      },
    });
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.actionsFor('Web') }));
    await userEvent.click(screen.getByRole('button', { name: t.delete }));
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: t.delete }),
    );

    await waitFor(() => expect(screen.getByRole('heading', { name: t.title })).toHaveFocus());
  });

  it('closes a sheet with Cancel', async () => {
    backend(tree);
    renderAt('/topics');

    await userEvent.click(await screen.findByRole('button', { name: t.new }));
    await userEvent.click(screen.getByRole('button', { name: t.cancel }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('is reachable from the Library', async () => {
    backend(tree);
    renderAt('/library');

    await userEvent.click(await screen.findByRole('link', { name: t.title }));

    expect(await screen.findByRole('heading', { name: t.title })).toBeInTheDocument();
  });
});
