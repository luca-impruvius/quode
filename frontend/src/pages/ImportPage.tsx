import { ChevronLeft } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ChangeEvent } from 'react';
import { Link } from 'react-router';
import { useImport, type ImportReport } from '../api/imports';
import { ErrorNotice } from '../components/ErrorNotice';
import { parseImport } from '../lib/importParser';
import { messages } from '../messages';

const m = messages.importer;

/**
 * Bulk import (docs/specs/M2-exam-prep-mvp.md): paste or upload, Check (nothing saved), then
 * Import. Import stays off until a Check of exactly this text and topic has passed. The text is
 * parsed only on Check and Import, so a paste of thousands of questions costs nothing while typing.
 */
export function ImportPage() {
  const [text, setText] = useState('');
  const [topic, setTopic] = useState('');
  const [inputError, setInputError] = useState<string | null>(null);
  const [checked, setChecked] = useState<{ text: string; topic: string } | null>(null);
  const [result, setResult] = useState<{ report: ImportReport; dryRun: boolean } | null>(null);
  const run = useImport();
  // The report that already got focus: a check report shown again after an undo must not steal it.
  const [focused, setFocused] = useState<ImportReport | null>(null);
  const ids = { topic: useId(), text: useId(), file: useId(), hint: useId() };

  const busy = run.isPending;
  const upToDate = checked !== null && checked.text === text && checked.topic === topic;
  // A check report only describes the text it checked; an import report stays until the next run.
  const shownResult = result && (!result.dryRun || upToDate) ? result : null;

  function send(dryRun: boolean) {
    if (busy) {
      return;
    }
    setResult(null);
    run.reset();
    const parsed = parseImport(text, topic);
    if (!parsed.ok) {
      setInputError(parsed.error);
      return;
    }
    setInputError(null);
    const sent = { text, topic };
    run.mutate(
      { input: parsed.input, dryRun },
      {
        onSuccess: (report) => {
          setResult({ report, dryRun });
          setChecked(dryRun ? sent : null);
          if (!dryRun) {
            setText('');
          }
        },
      },
    );
  }

  async function onFile(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const file = input.files?.[0];
    // Cleared, so choosing the same file again loads it again.
    input.value = '';
    if (!file) {
      return;
    }
    try {
      setText(await file.text());
      setInputError(null);
    } catch {
      setInputError(m.fileError);
    }
  }

  const field = 'rounded-md border-bold border-outline bg-surface px-space-3 py-space-2';
  const button = 'min-h-11 flex-1 rounded-md border-bold border-outline px-space-4 font-semibold';
  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col">
      <header className="flex items-center border-b border-line px-space-3 py-space-2">
        <Link
          to="/library"
          className="flex min-h-11 items-center gap-space-1 px-space-2 font-semibold"
        >
          <ChevronLeft aria-hidden="true" size={20} strokeWidth={2} />
          {m.back}
        </Link>
      </header>
      <main className="flex flex-1 flex-col gap-space-4 px-space-4 py-space-4">
        <h1 className="type-title">{m.title}</h1>

        <div className="flex flex-col gap-space-1">
          <label htmlFor={ids.topic} className="font-semibold">
            {m.topic}
          </label>
          <input
            id={ids.topic}
            value={topic}
            readOnly={busy}
            onChange={(e) => setTopic(e.target.value)}
            aria-describedby={`${ids.topic}-hint`}
            className={field}
          />
          <p id={`${ids.topic}-hint`} className="text-ink-muted type-body-sm">
            {m.topicHint}
          </p>
        </div>

        <div className="flex flex-col gap-space-1">
          <label htmlFor={ids.text} className="font-semibold">
            {m.text}
          </label>
          <textarea
            id={ids.text}
            value={text}
            readOnly={busy}
            onChange={(e) => {
              setText(e.target.value);
              setInputError(null);
            }}
            rows={10}
            spellCheck={false}
            autoCapitalize="off"
            autoCorrect="off"
            aria-describedby={`${ids.text}-hint`}
            className={`${field} font-mono type-body-sm`}
          />
          <p id={`${ids.text}-hint`} className="text-ink-muted type-body-sm">
            {m.textHint}
          </p>
        </div>

        <div className="flex flex-col gap-space-1">
          <label htmlFor={ids.file} className="font-semibold">
            {m.file}
          </label>
          <input
            id={ids.file}
            type="file"
            disabled={busy}
            accept=".json,.md,.txt,application/json,text/plain,text/markdown"
            onChange={(e) => void onFile(e)}
          />
        </div>

        <div className="flex gap-space-3">
          {/* aria-disabled while busy keeps keyboard focus on the button that was pressed. */}
          <button
            type="button"
            aria-disabled={busy}
            onClick={() => send(true)}
            className={`${button} aria-disabled:opacity-45`}
          >
            {busy && run.variables?.dryRun ? m.checking : m.check}
          </button>
          <button
            type="button"
            disabled={!upToDate}
            aria-disabled={busy}
            aria-describedby={!upToDate && text.trim() !== '' && !busy ? ids.hint : undefined}
            onClick={() => send(false)}
            className={`${button} bg-accent text-on-accent disabled:opacity-45 aria-disabled:opacity-45`}
          >
            {busy && run.variables?.dryRun === false ? m.importing : m.import}
          </button>
        </div>
        {!upToDate && text.trim() !== '' && !busy && (
          <p id={ids.hint} className="text-ink-muted type-body-sm">
            {m.checkFirst}
          </p>
        )}

        {inputError && (
          <p role="alert" className="rounded-md bg-missed-bg px-space-4 py-space-3">
            {inputError}
          </p>
        )}
        {run.isError && <ErrorNotice error={run.error} />}
        {shownResult && (
          <Report
            report={shownResult.report}
            dryRun={shownResult.dryRun}
            focus={focused !== shownResult.report}
            onFocused={() => setFocused(shownResult.report)}
          />
        )}
      </main>
    </div>
  );
}

interface ReportProps {
  report: ImportReport;
  dryRun: boolean;
  focus: boolean;
  onFocused: () => void;
}

function Report({ report, dryRun, focus, onFocused }: ReportProps) {
  const heading = useRef<HTMLHeadingElement>(null);
  const titleId = useId();
  // A new report takes focus once, so screen readers read it and the keyboard continues from it.
  useEffect(() => {
    if (focus) {
      heading.current?.focus();
      onFocused();
    }
  }, [focus, onFocused]);
  const lines = [
    m.created(report.created),
    report.drafts > 0 && m.drafts(report.drafts),
    report.filled > 0 && m.filled(report.filled),
    report.skippedDuplicates > 0 && m.skipped(report.skippedDuplicates),
    report.topicsCreated > 0 && m.topicsCreated(report.topicsCreated),
  ].filter((line): line is string => typeof line === 'string');
  return (
    <section
      aria-labelledby={titleId}
      className="flex flex-col gap-space-2 rounded-md bg-surface-sunken p-space-4"
    >
      <h2 id={titleId} ref={heading} tabIndex={-1} className="font-semibold">
        {dryRun ? m.checkedTitle : m.doneTitle}
      </h2>
      <ul className="flex flex-col gap-space-1">
        {lines.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      {report.problems.length > 0 && (
        <>
          <h3 className="font-semibold">{m.problemsTitle(report.problems.length)}</h3>
          <ul className="flex flex-col gap-space-1 type-body-sm">
            {report.problems.map((p) => (
              <li key={`${p.index}-${p.reason}`}>{m.problem(p.index, p.reason)}</li>
            ))}
          </ul>
        </>
      )}
      {!dryRun && (
        <Link to="/library" className="font-semibold underline">
          {m.toLibrary}
        </Link>
      )}
    </section>
  );
}
