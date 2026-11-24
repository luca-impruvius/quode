import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { messages } from '../messages';
import { me, mockApi, renderAt } from '../test/renderApp';

const m = messages.importer;

const report =
  (overrides: Record<string, unknown> = {}) =>
  () =>
    Response.json({
      created: 2,
      filled: 0,
      drafts: 1,
      skippedDuplicates: 0,
      topicsCreated: 1,
      problems: [{ index: 3, reason: 'Mark exactly one option as correct.' }],
      ...overrides,
    });

const text = 'Q: First?\nA) x\nB) y\nAnswer: A';

function bodyOf(fetchMock: ReturnType<typeof mockApi>, url: string): unknown {
  const call = fetchMock.mock.calls.find(([u]) => String(u) === url);
  return JSON.parse(String(call?.[1]?.body));
}

describe('Import', () => {
  it('checks without saving, then imports the same questions', async () => {
    const fetchMock = mockApi({
      '/api/me': me(),
      'POST /api/v1/imports?dryRun=true': report(),
      'POST /api/v1/imports?dryRun=false': report({ problems: [] }),
    });
    const user = userEvent.setup();
    renderAt('/import');

    await user.type(await screen.findByLabelText(m.topic), 'Cloud › GCP');
    await user.type(screen.getByLabelText(m.text), text);
    expect(screen.getByRole('button', { name: m.import })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: m.check }));
    expect(await screen.findByText(m.checkedTitle)).toBeInTheDocument();
    expect(screen.getByText(m.created(2))).toBeInTheDocument();
    expect(screen.getByText(m.drafts(1))).toBeInTheDocument();
    expect(screen.getByText(m.problemsTitle(1))).toBeInTheDocument();
    expect(
      screen.getByText(m.problem(3, 'Mark exactly one option as correct.')),
    ).toBeInTheDocument();
    expect(bodyOf(fetchMock, '/api/v1/imports?dryRun=true')).toEqual({
      topic: 'Cloud › GCP',
      questions: [
        {
          type: 'SINGLE_CHOICE',
          prompt: 'First?',
          options: [
            { text: 'x', correct: true },
            { text: 'y', correct: false },
          ],
        },
      ],
    });

    await user.click(screen.getByRole('button', { name: m.import }));
    expect(await screen.findByText(m.doneTitle)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: m.toLibrary })).toBeInTheDocument();
    expect(screen.getByLabelText(m.text)).toHaveValue('');
  });

  it('needs a new check after the text changes', async () => {
    mockApi({ '/api/me': me(), 'POST /api/v1/imports?dryRun=true': report() });
    const user = userEvent.setup();
    renderAt('/import');

    await user.type(await screen.findByLabelText(m.text), text);
    await user.click(screen.getByRole('button', { name: m.check }));
    await waitFor(() => expect(screen.getByRole('button', { name: m.import })).toBeEnabled());

    await user.type(screen.getByLabelText(m.text), ' more');
    expect(screen.getByRole('button', { name: m.import })).toBeDisabled();
    expect(screen.getByText(m.checkFirst)).toBeInTheDocument();
  });

  it('shows a layout problem without calling the server', async () => {
    const fetchMock = mockApi({ '/api/me': me() });
    const user = userEvent.setup();
    renderAt('/import');

    await user.type(await screen.findByLabelText(m.text), 'Just some text');
    await user.click(screen.getByRole('button', { name: m.check }));
    expect(await screen.findByRole('alert')).toHaveTextContent(m.badBlock(1, m.noPrompt));
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/imports'))).toBe(false);
  });

  it('shows the server refusal instead of an old report, and keeps Import off', async () => {
    let refuse = false;
    mockApi({
      '/api/me': me(),
      'POST /api/v1/imports?dryRun=true': () =>
        refuse
          ? Response.json(
              { status: 400, detail: 'An import needs 1 to 2,000 questions.' },
              { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
            )
          : report()(),
    });
    const user = userEvent.setup();
    renderAt('/import');

    await user.type(await screen.findByLabelText(m.text), text);
    await user.click(screen.getByRole('button', { name: m.check }));
    expect(await screen.findByText(m.checkedTitle)).toBeInTheDocument();

    refuse = true;
    await user.type(screen.getByLabelText(m.text), '\n\nQ: Second?');
    await user.click(screen.getByRole('button', { name: m.check }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'An import needs 1 to 2,000 questions.',
    );
    expect(screen.queryByText(m.checkedTitle)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: m.import })).toBeDisabled();
  });

  it('focuses a new report once, never while the text is being edited', async () => {
    mockApi({ '/api/me': me(), 'POST /api/v1/imports?dryRun=true': report() });
    const user = userEvent.setup();
    renderAt('/import');

    const box = await screen.findByLabelText(m.text);
    await user.type(box, text);
    await user.click(screen.getByRole('button', { name: m.check }));
    expect(await screen.findByRole('heading', { name: m.checkedTitle })).toHaveFocus();

    await user.click(box);
    await user.type(box, 'x{Backspace}');
    expect(screen.getByText(m.checkedTitle)).toBeInTheDocument();
    expect(box).toHaveFocus();
  });

  it('loads a chosen file into the text box', async () => {
    mockApi({ '/api/me': me() });
    const user = userEvent.setup();
    renderAt('/import');

    const file = new File(['{"questions":[]}'], 'questions.json', { type: 'application/json' });
    await user.upload(await screen.findByLabelText(m.file), file);
    await waitFor(() => expect(screen.getByLabelText(m.text)).toHaveValue('{"questions":[]}'));
  });
});
