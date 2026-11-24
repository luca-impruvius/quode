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

const exported = (remaining: number) => ({
  topic: null,
  remaining,
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
});

// Records every request; the list answers with `items`, the export with `exportAnswer`.
function backend({
  items = [draft],
  exportAnswer = () => Response.json(exported(3)),
}: { items?: LibraryItem[]; exportAnswer?: () => Response } = {}) {
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
      return exportAnswer();
    }
    if (url.pathname === '/api/v1/questions') {
      return Response.json({
        items,
        page: 0,
        size: 20,
        totalElements: items.length,
        hasNext: false,
      });
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  });
  return requests;
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe('Library: Needs answer', () => {
  it('lists drafts under the Needs answer status', async () => {
    const requests = backend();
    const user = userEvent.setup();
    renderAt('/library');

    await user.click(await screen.findByRole('button', { name: l.statusChip('active') }));
    await user.click(await screen.findByRole('radio', { name: l.statuses.needs_answer }));

    expect(await screen.findByText(l.exportHint(false))).toBeInTheDocument();
    expect(
      requests.some(
        (u) =>
          u.pathname === '/api/v1/questions' && u.searchParams.get('status') === 'needs_answer',
      ),
    ).toBe(true);
  });

  it('copies the drafts of the topic in the import format, saying how many wait', async () => {
    const requests = backend();
    const user = userEvent.setup();
    const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
    renderAt('/library?status=needs_answer&topicId=coll');

    expect(await screen.findByText(l.exportHint(true))).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: l.copy }));

    expect(await screen.findByText(`${l.copied(1)} ${l.exportRemaining(3)}`)).toBeInTheDocument();
    expect(JSON.parse(String(writeText.mock.calls[0]?.[0]))).toEqual(exported(3));
    const exportCall = requests.find((u) => u.pathname === '/api/v1/questions/export');
    expect(exportCall?.searchParams.get('status')).toBe('DRAFT');
    expect(exportCall?.searchParams.get('topicId')).toBe('coll');
  });

  it('downloads the drafts as a named JSON file and frees it afterwards', async () => {
    backend({ exportAnswer: () => Response.json(exported(0)) });
    const user = userEvent.setup();
    const blobs: Blob[] = [];
    const realCreate = URL.createObjectURL;
    const realRevoke = URL.revokeObjectURL;
    const revokeUrl = vi.fn();
    URL.createObjectURL = (blob: Blob) => {
      blobs.push(blob);
      return 'blob:quode';
    };
    URL.revokeObjectURL = revokeUrl;
    const downloads: string[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      downloads.push(this.download);
    });
    const timers = vi.spyOn(globalThis, 'setTimeout');
    try {
      renderAt('/library?status=needs_answer');
      await user.click(await screen.findByRole('button', { name: l.download }));

      expect(await screen.findByText(l.downloaded(1))).toBeInTheDocument();
      expect(screen.queryByText(/more wait/)).not.toBeInTheDocument();
      expect(downloads).toEqual([l.exportFileName]);
      expect(JSON.parse(await (blobs[0] ?? new Blob()).text())).toEqual(exported(0));
      // Freed a second later, not at once (Safari may cancel the download otherwise).
      expect(revokeUrl).not.toHaveBeenCalled();
      const delayed = timers.mock.calls.find(
        ([fn, ms]) => ms === 1000 && String(fn).includes('revokeObjectURL'),
      );
      expect(delayed).toBeDefined();
      (delayed?.[0] as () => void)();
      expect(revokeUrl).toHaveBeenCalledWith('blob:quode');
    } finally {
      URL.createObjectURL = realCreate;
      URL.revokeObjectURL = realRevoke;
    }
  });

  it("shows the server's message when the export fails", async () => {
    backend({
      exportAnswer: () =>
        Response.json(
          { status: 404, detail: 'Topic not found.' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
    });
    const user = userEvent.setup();
    vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
    renderAt('/library?status=needs_answer&topicId=coll');

    await user.click(await screen.findByRole('button', { name: l.copy }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Topic not found.');
    expect(screen.queryByText(l.copied(1))).not.toBeInTheDocument();
  });

  it('suggests Download when the clipboard refuses', async () => {
    backend();
    const user = userEvent.setup();
    vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValue(new Error('NotAllowedError'));
    renderAt('/library?status=needs_answer');

    await user.click(await screen.findByRole('button', { name: l.copy }));
    expect(await screen.findByRole('alert')).toHaveTextContent(l.copyFailed);
  });

  it('says every question has its answer when the list is empty', async () => {
    backend({ items: [] });
    renderAt('/library?status=needs_answer');
    expect(await screen.findByText(l.allAnswered)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: l.download })).not.toBeInTheDocument();
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
