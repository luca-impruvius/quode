import { Check, ChevronLeft } from 'lucide-react';
import { Link, useParams } from 'react-router';
import {
  questionApi,
  useQuestion,
  useQuestionMutation,
  useRefreshOnConflict,
  type QuestionView,
} from '../api/questions';
import { ErrorNotice } from '../components/ErrorNotice';
import { Markdown } from '../components/Markdown';
import { messages } from '../messages';

const d = messages.question;

function hostOf(url: string): string {
  try {
    return new URL(url).hostname;
  } catch {
    return url;
  }
}

/** One question, read-only, per the QuestionDetail wireframe (no review controls in M1). */
export function QuestionDetailPage() {
  const { id } = useParams();
  const question = useQuestion(id);

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col">
      <header className="flex items-center justify-between border-b border-line px-space-3 py-space-2">
        <Link
          to="/library"
          className="flex min-h-11 items-center gap-space-1 px-space-2 font-semibold"
        >
          <ChevronLeft aria-hidden="true" size={20} strokeWidth={2} />
          {d.back}
        </Link>
        {question.data && (
          <Link
            to={`/questions/${question.data.id}/edit`}
            className="flex min-h-11 items-center rounded-md border-bold border-outline px-space-4 font-semibold"
          >
            {d.edit}
          </Link>
        )}
      </header>
      <main className="flex flex-1 flex-col gap-space-4 px-space-4 py-space-4">
        {question.isPending && (
          <p role="status" className="text-ink-muted">
            {messages.loading}
          </p>
        )}
        {question.isError && <ErrorNotice error={question.error} />}
        {question.data && <Details question={question.data} />}
      </main>
    </div>
  );
}

function Details({ question: q }: { question: QuestionView }) {
  const archive = useQuestionMutation(questionApi.archive);
  const unarchive = useQuestionMutation(questionApi.unarchive);
  const archived = q.archivedAt !== null;
  const change = archived ? unarchive : archive;
  const refreshOnConflict = useRefreshOnConflict();

  return (
    <>
      {archived && (
        <p className="rounded-md bg-surface-sunken px-space-4 py-space-3 type-body-sm">
          {d.archivedBanner}
        </p>
      )}
      <ul className="flex flex-wrap gap-space-2" aria-label={messages.editor.topics}>
        {q.topics.map((topic) => (
          <li
            key={topic.id}
            className={`rounded-pill px-space-3 py-space-1 type-body-sm ${
              topic.primary ? 'border-bold border-outline font-semibold' : 'bg-surface-sunken'
            }`}
          >
            {topic.primary ? `${topic.path} · ${d.primary}` : topic.path}
          </li>
        ))}
      </ul>

      <h1 className="sr-only">{messages.editor.question}</h1>
      <div className="type-question">
        <Markdown text={q.promptMd} />
      </div>

      {q.options.length > 0 && (
        <section className="flex flex-col gap-space-2">
          <h2 className="type-label text-ink-muted uppercase">{d.options}</h2>
          <ul aria-label={d.options} className="flex flex-col gap-space-2">
            {q.options.map((option) => (
              <li
                key={option.id}
                className={`flex items-start gap-space-3 rounded-md border-bold p-space-3 ${
                  option.correct ? 'border-correct bg-correct-bg' : 'border-line'
                }`}
              >
                <div className="flex-1">
                  <Markdown text={option.textMd} />
                </div>
                {option.correct && (
                  <span className="flex items-center gap-space-1 type-body-sm font-bold text-correct">
                    <Check aria-hidden="true" size={16} strokeWidth={2.5} />
                    {d.correct}
                  </span>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {q.answerMd && (
        <section className="flex flex-col gap-space-2">
          <h2 className="type-label text-ink-muted uppercase">{d.answer}</h2>
          <Markdown text={q.answerMd} />
        </section>
      )}

      {q.explanationMd && (
        <section className="flex flex-col gap-space-2">
          <h2 className="type-label text-ink-muted uppercase">{d.explanation}</h2>
          <Markdown text={q.explanationMd} />
        </section>
      )}

      <dl className="grid grid-cols-3 gap-space-2 type-body-sm">
        <Fact label={d.type} value={messages.questionTypeNames[q.type]} />
        <Fact label={d.difficulty} value={d.difficultyValue(q.difficulty)} />
        <Fact label={d.status} value={archived ? d.archived : d.active} />
      </dl>

      {q.sourceUrl && (
        <p className="type-body-sm">
          <span className="text-ink-muted">{d.source}: </span>
          <a href={q.sourceUrl} target="_blank" rel="noopener noreferrer" className="underline">
            {hostOf(q.sourceUrl)}
          </a>
        </p>
      )}

      {change.isError && <ErrorNotice error={change.error} />}
      <button
        type="button"
        disabled={change.isPending}
        onClick={() =>
          change.mutate(
            { id: q.id, version: q.version },
            // Stale: load the latest version, so pressing again works.
            { onError: (error) => refreshOnConflict(q.id, error) },
          )
        }
        className="h-12 rounded-md border-bold border-outline font-semibold disabled:opacity-45"
      >
        {archived ? d.unarchive : d.archive}
      </button>
    </>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md bg-surface-sunken p-space-3">
      <dt className="text-ink-muted">{label}</dt>
      <dd className="font-semibold">{value}</dd>
    </div>
  );
}
