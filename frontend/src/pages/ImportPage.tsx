import { ChevronLeft } from 'lucide-react';
import { useId, useState, type ChangeEvent } from 'react';
import { Link } from 'react-router';
import { useImport, type ImportReport } from '../api/imports';
import { ErrorNotice } from '../components/ErrorNotice';
import { parseImport } from '../lib/importParser';
import { messages } from '../messages';

const m = messages.importer;

/**
 * Bulk import (docs/specs/M2-exam-prep-mvp.md): paste or upload, Check (nothing saved), then
 * Import. Import stays off until a Check of exactly this text and topic has passed.
 */
export function ImportPage() {
  const [text, setText] = useState('');
  const [topic, setTopic] = useState('');
  const [parseError, setParseError] = useState<string | null>(null);
  const [checked, setChecked] = useState<{ text: string; topic: string } | null>(null);
  const [result, setResult] = useState<{ report: ImportReport; dryRun: boolean } | null>(null);
  const run = useImport();
  const ids = { topic: useId(), text: useId(), file: useId() };

  const upToDate = checked !== null && checked.text === text && checked.topic === topic;

  function send(dryRun: boolean) {
    const parsed = parseImport(text, topic);
    if (!parsed.ok) {
      setParseError(parsed.error);
      setResult(null);
      return;
    }
    setParseError(null);
    run.mutate(
      { input: parsed.input, dryRun },
      {
        onSuccess: (report) => {
          setResult({ report, dryRun });
          setChecked(dryRun ? { text, topic } : null);
          if (!dryRun) {
            setText('');
          }
        },
      },
    );
  }

  async function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) {
      setText(await file.text());
    }
  }

  const busy = run.isPending;
  const field = 'rounded-md border-bold border-outline bg-surface px-space-3 py-space-2';
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
            onChange={(e) => setText(e.target.value)}
            rows={10}
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
            accept=".json,.md,.txt,application/json,text/plain,text/markdown"
            onChange={(e) => void onFile(e)}
          />
        </div>

        <div className="flex gap-space-3">
          <button
            type="button"
            disabled={busy}
            onClick={() => send(true)}
            className="min-h-11 flex-1 rounded-md border-bold border-outline px-space-4 font-semibold"
          >
            {busy && run.variables?.dryRun ? m.checking : m.check}
          </button>
          <button
            type="button"
            disabled={busy || !upToDate}
            onClick={() => send(false)}
            className="min-h-11 flex-1 rounded-md border-bold border-outline bg-accent px-space-4 font-semibold text-on-accent disabled:opacity-50"
          >
            {busy && run.variables?.dryRun === false ? m.importing : m.import}
          </button>
        </div>
        {!upToDate && text.trim() !== '' && !busy && (
          <p className="text-ink-muted type-body-sm">{m.checkFirst}</p>
        )}

        {parseError && (
          <p role="alert" className="rounded-md bg-missed-bg px-space-4 py-space-3">
            {parseError}
          </p>
        )}
        {run.isError && <ErrorNotice error={run.error} />}
        {result && <Report report={result.report} dryRun={result.dryRun} />}
      </main>
    </div>
  );
}

function Report({ report, dryRun }: { report: ImportReport; dryRun: boolean }) {
  const titleId = useId();
  const lines = [
    m.created(report.created),
    report.drafts > 0 && m.drafts(report.drafts),
    report.filled > 0 && m.filled(report.filled),
    report.skippedDuplicates > 0 && m.skipped(report.skippedDuplicates),
    report.topicsCreated > 0 && m.topicsCreated(report.topicsCreated),
  ].filter((line): line is string => typeof line === 'string');
  return (
    <section
      role="status"
      aria-labelledby={titleId}
      className="flex flex-col gap-space-2 rounded-md bg-surface-sunken p-space-4"
    >
      <h2 id={titleId} className="font-semibold">
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
              <li key={p.index}>{m.problem(p.index, p.reason)}</li>
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
