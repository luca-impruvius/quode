import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { LibraryItem } from '../api/library';
import { messages } from '../messages';
import { topics } from '../test/questionFixtures';
import { me, renderAt } from '../test/renderApp';

const l = messages.library;

function item(n: number, overrides: Partial<LibraryItem> = {}): LibraryItem {
  return {
    id: `q${n}`,
    type: 'FLASHCARD',
    promptMd: `Question **${n}**`,
    difficulty: 2,
    primaryTopic: { id: 'coll', name: 'Collections', path: 'Languages › Java › Collections' },
    archived: false,
    flagged: false,
    flagNote: null,
    createdAt: '2026-10-31T10:00:00Z',
    updatedAt: '2026-10-31T10:00:00Z',
    ...overrides,
  };
}

// The library endpoint answers by query string; every request URL is recorded.
function backend(pages: (url: URL) => { items: LibraryItem[]; total: number; hasNext?: boolean }) {
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
      const page = pages(url);
      return Response.json({
        items: page.items,
        page: Number(url.searchParams.get('page') ?? 0),
        size: 20,
        totalElements: page.total,
        hasNext: page.hasNext ?? false,
      });
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  });
  return requests;
}

describe('Library', () => {
  it('lists my questions with their topic and type, linking to each', async () => {
    backend(() => ({
      items: [item(1), item(2, { type: 'MULTIPLE_CHOICE', flagged: true })],
      total: 2,
    }));
    renderAt('/library');

    const list = await screen.findByRole('list', { name: l.results });
    const rows = within(list).getAllByRole('link');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveAttribute('href', '/questions/q1');
    expect(rows[0]).toHaveTextContent('Question 1');
    expect(rows[0]).toHaveTextContent(`Collections · ${messages.questionTypeNames.FLASHCARD}`);
    expect(rows[1]).toHaveTextContent(l.flagged);
    expect(screen.getByText(l.count(2))).toBeInTheDocument();
  });

  it('searches as I type', async () => {
    const requests = backend((url) => ({
      items: url.searchParams.get('q') === 'hash' ? [item(1)] : [item(1), item(2)],
      total: url.searchParams.get('q') === 'hash' ? 1 : 2,
    }));
    renderAt('/library');
    await screen.findByText(l.count(2));

    await userEvent.type(screen.getByRole('searchbox', { name: l.search }), 'hash');

    expect(await screen.findByText(l.count(1))).toBeInTheDocument();
    expect(requests.at(-1)?.searchParams.get('q')).toBe('hash');
  });

  it('filters by a topic, including its sub-topics', async () => {
    const requests = backend((url) => ({
      items: [item(1)],
      total: url.searchParams.get('topicId') === 'java' ? 1 : 5,
    }));
    renderAt('/library');
    await screen.findByText(l.count(5));

    await userEvent.click(screen.getByRole('button', { name: l.topicChip(null) }));
    const sheet = screen.getByRole('dialog', { name: l.topic });
    await userEvent.click(within(sheet).getByRole('radio', { name: 'Languages › Java' }));

    expect(await screen.findByText(l.countInTopic(1, 'Java'))).toBeInTheDocument();
    expect(requests.at(-1)?.searchParams.get('topicId')).toBe('java');
    expect(screen.getByRole('button', { name: l.topicChip('Java') })).toBeInTheDocument();
  });

  it('filters by type, difficulty and sort', async () => {
    const requests = backend(() => ({ items: [item(1)], total: 1 }));
    renderAt('/library');
    await screen.findByText(l.count(1));

    await userEvent.click(screen.getByRole('button', { name: l.typeChip(null) }));
    await userEvent.click(
      screen.getByRole('radio', { name: messages.questionTypeNames.OPEN_TEXT }),
    );
    await userEvent.click(screen.getByRole('button', { name: l.difficultyChip(null) }));
    await userEvent.click(screen.getByRole('radio', { name: messages.editor.difficulties[2] }));
    await userEvent.selectOptions(screen.getByRole('combobox', { name: l.sort }), 'difficulty');

    await waitFor(() => {
      const last = requests.at(-1);
      expect(last?.searchParams.get('type')).toBe('OPEN_TEXT');
      expect(last?.searchParams.get('difficulty')).toBe('3');
      expect(last?.searchParams.get('sort')).toBe('difficulty');
    });
  });

  it('shows archived questions from the URL', async () => {
    const requests = backend(() => ({ items: [item(1, { archived: true })], total: 1 }));
    renderAt('/library?status=archived');

    expect(await screen.findByText(l.archived, { selector: 'span' })).toBeInTheDocument();
    expect(requests[0]?.searchParams.get('status')).toBe('archived');
    expect(screen.getByRole('button', { name: l.statusChip('archived') })).toBeInTheDocument();
  });

  it('loads more on request', async () => {
    const requests = backend((url) =>
      url.searchParams.get('page') === '1'
        ? { items: [item(3)], total: 3 }
        : { items: [item(1), item(2)], total: 3, hasNext: true },
    );
    renderAt('/library');

    await userEvent.click(await screen.findByRole('button', { name: l.loadMore }));

    expect(await screen.findByText('Question 3')).toBeInTheDocument();
    expect(screen.getByText('Question 1')).toBeInTheDocument();
    expect(requests.at(-1)?.searchParams.get('page')).toBe('1');
    expect(screen.queryByRole('button', { name: l.loadMore })).not.toBeInTheDocument();
  });

  it("doesn't let a pending search undo a sort chosen meanwhile", async () => {
    const requests = backend(() => ({ items: [item(1)], total: 1 }));
    renderAt('/library');
    await screen.findByText(l.count(1));

    await userEvent.type(screen.getByRole('searchbox', { name: l.search }), 'map');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: l.sort }), 'updated');

    await waitFor(() => expect(requests.at(-1)?.searchParams.get('q')).toBe('map'));
    expect(requests.at(-1)?.searchParams.get('sort')).toBe('updated');
  });

  it('empties the search box when the URL loses its search', async () => {
    backend(() => ({ items: [item(1)], total: 1 }));
    renderAt('/library?q=hash');
    const box = await screen.findByRole('searchbox', { name: l.search });
    expect(box).toHaveValue('hash');

    await userEvent.click(screen.getByRole('link', { name: messages.tabs.library }));

    await waitFor(() => expect(box).toHaveValue(''));
  });

  it('lists flagged questions with their note', async () => {
    const requests = backend(() => ({
      items: [item(1, { flagged: true, flagNote: 'Check the source' })],
      total: 1,
    }));
    renderAt('/library?status=flagged');

    expect(await screen.findByText('“Check the source”')).toBeInTheDocument();
    expect(requests[0]?.searchParams.get('status')).toBe('flagged');
  });

  it('says when nothing matches', async () => {
    backend(() => ({ items: [], total: 0 }));
    renderAt('/library?q=zzz');

    expect(await screen.findByText(l.noMatches)).toBeInTheDocument();
  });

  it('is reachable from Me for archived questions', async () => {
    backend(() => ({ items: [], total: 0 }));
    const router = renderAt('/me');

    await userEvent.click(await screen.findByRole('link', { name: messages.me.archived }));

    expect(await screen.findByText(l.noMatches)).toBeInTheDocument();
    expect(router.state.location.search).toBe('?status=archived');
  });
});
