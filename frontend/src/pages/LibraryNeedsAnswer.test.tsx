import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { LibraryItem } from '../api/library';
import { messages } from '../messages';
import { topics } from '../test/questionFixtures';
import { me, renderAt } from '../test/renderApp';

const l = messages.library;

const draft: LibraryItem = {
  id: 'd1',
  type: 'SINGLE_CHOICE',
  promptMd: 'Which service runs containers serverless?',
  difficulty: 2,
  primaryTopic: { id: 'coll', name: 'Collections', path: 'Languages › Java › Collections' },
  archived: false,
  flagged: false,
  flagNote: null,
  createdAt: '2026-11-24T10:00:00Z',
  updatedAt: '2026-11-24T10:00:00Z',
};

const exported = {
  topic: null,
  remaining: 3,
  questions: [
    {
      id: 'd1',
      type: 'SINGLE_CHOICE',
      prompt: 'Which service runs containers serverless?',
      answer: null,
      options: [
        { text: 'GKE', correct: false },
        { text: 'Cloud Run', correct: false },
      ],
      explanation: null,
      difficulty: 2,
      sourceUrl: null,
      topics: ['Languages › Java › Collections'],
    },
  ],
};

// Records every request; the list answers with one draft, the export with the file above.
function backend() {
  const requests: URL[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = new URL(String(input), 'http://localhost');
    requests.push(url);
    if (url.pathname === '/api/me') {
      return me();
    }
    if (url.pathname === '/api/v1/topics') {
      return Response.json(topics);
    }
    if (url.pathname === '/api/v1/questions/export') {
      return Response.json(exported);
    }
    if (url.pathname === '/api/v1/questions') {
      return Response.json({ items: [draft], page: 0, size: 20, totalElements: 1, hasNext: false });
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  });
  return requests;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Library: Needs answer', () => {
  it('lists drafts under the Needs answer status', async () => {
    const requests = backend();
    const user = userEvent.setup();
    renderAt('/library');

    await user.click(await screen.findByRole('button', { name: l.statusChip('active') }));
    await user.click(await screen.findByRole('radio', { name: l.statuses.needs_answer }));

    expect(await screen.findByText(l.exportHint)).toBeInTheDocument();
    expect(
      requests.some(
        (u) =>
          u.pathname === '/api/v1/questions' && u.searchParams.get('status') === 'needs_answer',
      ),
    ).toBe(true);
  });

  it('copies the drafts in the import format, saying how many wait', async () => {
    const requests = backend();
    const user = userEvent.setup();
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
    renderAt('/library?status=needs_answer&topicId=coll');

    await user.click(await screen.findByRole('button', { name: l.copy }));

    expect(await screen.findByText(`${l.copied(1)} ${l.exportRemaining(3)}`)).toBeInTheDocument();
    expect(JSON.parse(String(writeText.mock.calls[0]?.[0]))).toEqual(exported);
    const exportCall = requests.find((u) => u.pathname === '/api/v1/questions/export');
    expect(exportCall?.searchParams.get('status')).toBe('DRAFT');
    expect(exportCall?.searchParams.get('topicId')).toBe('coll');
  });

  it('downloads the drafts as a JSON file', async () => {
    backend();
    const user = userEvent.setup();
    const createUrl = vi.fn(() => 'blob:quode');
    const revokeUrl = vi.fn();
    vi.stubGlobal(
      'URL',
      Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: revokeUrl }),
    );
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    renderAt('/library?status=needs_answer');

    await user.click(await screen.findByRole('button', { name: l.download }));

    expect(
      await screen.findByText(`${l.downloaded(1)} ${l.exportRemaining(3)}`),
    ).toBeInTheDocument();
    expect(click).toHaveBeenCalledOnce();
    expect(revokeUrl).toHaveBeenCalledWith('blob:quode');
  });

  it('offers no export outside Needs answer', async () => {
    backend();
    renderAt('/library');
    expect(
      await screen.findByText('Which service runs containers serverless?'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: l.download })).not.toBeInTheDocument();
  });
});
