import { Check, ChevronLeft, Flag } from 'lucide-react';
import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import {
  questionApi,
  useQuestion,
  useQuestionMutation,
  useRefreshOnConflict,
  type QuestionView,
} from '../api/questions';
import { useQuestionReview, useSaveQuestionReview, type QuestionReview } from '../api/review';
import { useMe } from '../app/useMe';
import { ErrorNotice } from '../components/ErrorNotice';
import { Markdown } from '../components/Markdown';
import { Sheet } from '../components/Sheet';
import { daysFromToday } from '../lib/days';
import { messages } from '../messages';

const d = messages.question;

function hostOf(url: string): string {
  try {
    return new URL(url).hostname;
  } catch {
    return url;
  }
}

/** The Library's right-hand panel on desktop: the selected question, with Edit. */
export function QuestionPanel({ id }: { id: string }) {
  const question = useQuestion(id);
  return (
    <section aria-label={d.panel} className="flex flex-col gap-space-4">
      {/* Focus stays on the list; this tells screen readers what the panel now shows. */}
      <p role="status" className="sr-only">
        {question.isPending
          ? messages.loading
          : question.data
            ? d.showing(question.data.promptMd)
            : ''}
      </p>
      {question.isError && <ErrorNotice error={question.error} />}
      {question.data && (
        <>
          <Link
            to={`/questions/${question.data.id}/edit`}
            className="flex min-h-11 items-center self-end rounded-md border-bold border-outline px-space-4 font-semibold"
          >
            {d.edit}
          </Link>
          <Details question={question.data} />
        </>
      )}
    </section>
  );
}

/** One question, read-only, per the QuestionDetail wireframe, with its review controls (M3). */
export function QuestionDetailPage() {
  const { id } = useParams();
  const question = useQuestion(id);

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col lg:max-w-3xl">
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
  const unflag = useQuestionMutation(questionApi.unflag);
  const [flagging, setFlagging] = useState(false);
  const flagged = q.flaggedAt !== null;
  const bannerHeading = useRef<HTMLParagraphElement>(null);
  const flagButton = useRef<HTMLButtonElement>(null);
  const bannerId = useId();

  // Flagging swaps the button for the banner and back: keep focus on whichever now stands there.
  const wasFlagged = useRef(flagged);
  useEffect(() => {
    if (flagged !== wasFlagged.current) {
      (flagged ? bannerHeading.current : flagButton.current)?.focus();
      wasFlagged.current = flagged;
    }
  }, [flagged]);

  return (
    <>
      {archived && (
        <p className="rounded-md bg-surface-sunken px-space-4 py-space-3 type-body-sm">
          {d.archivedBanner}
        </p>
      )}
      {flagged && (
        <section
          aria-labelledby={bannerId}
          className="flex flex-col gap-space-2 rounded-md bg-missed-bg p-space-4"
        >
          <p
            id={bannerId}
            ref={bannerHeading}
            tabIndex={-1}
            className="flex items-center gap-space-2 font-semibold"
          >
            <Flag aria-hidden="true" size={16} strokeWidth={2} />
            {d.flagged}
          </p>
          {q.flagNote && <p className="type-body-sm whitespace-pre-line">{q.flagNote}</p>}
          {unflag.isError && <ErrorNotice error={unflag.error} />}
          <button
            type="button"
            disabled={unflag.isPending}
            onClick={() =>
              unflag.mutate(
                { id: q.id, version: q.version },
                { onError: (error) => refreshOnConflict(q.id, error) },
              )
            }
            className="h-11 self-start rounded-md border-bold border-outline bg-surface px-space-4 font-semibold disabled:opacity-45"
          >
            {d.unflag}
          </button>
        </section>
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
        <NextReview id={q.id} />
      </dl>

      {/* Archived questions never enter the queue: no controls that would look like they do. */}
      {!archived && <ReviewControls id={q.id} />}

      {q.sourceUrl && (
        <p className="type-body-sm">
          <span className="text-ink-muted">{d.source}: </span>
          <a href={q.sourceUrl} target="_blank" rel="noopener noreferrer" className="underline">
            {hostOf(q.sourceUrl)}
          </a>
        </p>
      )}

      {!flagged && (
        <button
          ref={flagButton}
          type="button"
          onClick={() => setFlagging(true)}
          className="flex h-12 items-center justify-center gap-space-2 rounded-md border-bold border-outline font-semibold"
        >
          <Flag aria-hidden="true" size={18} strokeWidth={2} />
          {d.flag}
        </button>
      )}
      {flagging && <FlagSheet question={q} onClose={() => setFlagging(false)} />}
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

const MAX_NOTE = 200;

function FlagSheet({ question: q, onClose }: { question: QuestionView; onClose: () => void }) {
  const [note, setNote] = useState('');
  const flag = useQuestionMutation(questionApi.flag);
  const refreshOnConflict = useRefreshOnConflict();
  // Characters as the server counts them (code points), so an emoji counts once.
  const left = MAX_NOTE - [...note].length;
  const hintId = useId();

  function submit(event: FormEvent) {
    event.preventDefault();
    flag.mutate(
      { id: q.id, version: q.version, note },
      { onSuccess: onClose, onError: (error) => refreshOnConflict(q.id, error) },
    );
  }

  return (
    <Sheet title={d.flagTitle} onClose={onClose}>
      <form onSubmit={submit} className="flex flex-col gap-space-4">
        <label className="flex flex-col gap-space-1">
          <span className="font-semibold">{d.flagNote}</span>
          <textarea
            value={note}
            rows={3}
            onChange={(event) => setNote([...event.target.value].slice(0, MAX_NOTE).join(''))}
            aria-describedby={hintId}
            className="rounded-md border-2 border-outline bg-surface p-space-3"
          />
        </label>
        <span id={hintId} className="type-body-sm text-ink-muted">
          {d.flagNoteHint(left)}
        </span>
        {flag.isError && <ErrorNotice error={flag.error} />}
        <div className="grid grid-cols-2 gap-space-3">
          <button
            type="button"
            onClick={onClose}
            className="h-12 rounded-md border-bold border-outline font-semibold"
          >
            {d.cancel}
          </button>
          <button
            type="submit"
            disabled={flag.isPending}
            className="h-12 rounded-md border-bold border-outline bg-accent font-bold text-on-accent shadow-pop disabled:opacity-45 disabled:shadow-none"
          >
            {d.flagSave}
          </button>
        </div>
      </form>
    </Sheet>
  );
}

/** "Next review: in 3 days", "New", or "Not in review", under Status. */
function NextReview({ id }: { id: string }) {
  const review = useQuestionReview(id);
  const me = useMe();
  return (
    <Fact
      label={d.nextReview}
      value={review.data ? nextReviewText(review.data, me.data?.timeZone) : messages.loading}
      wide
    />
  );
}

function nextReviewText(review: QuestionReview, timeZone: string | undefined): string {
  if (!review.enrolled) {
    return d.notInReview;
  }
  if (review.isNew || review.dueAt === null) {
    return d.isNew;
  }
  return d.nextReviewIn(daysFromToday(review.dueAt, timeZone));
}

/** Learn next (new questions only) and Remove from review / Add to review. */
function ReviewControls({ id }: { id: string }) {
  const review = useQuestionReview(id);
  const save = useSaveQuestionReview(id);
  const [confirming, setConfirming] = useState(false);
  const r = review.data;

  function removeOrAdd(current: QuestionReview) {
    if (!current.enrolled) {
      save.mutate({ enrolled: true, learnNext: false });
    } else if (current.isNew) {
      // Nothing to lose yet: no schedule or history.
      save.mutate({ enrolled: false, learnNext: false });
    } else {
      setConfirming(true);
    }
  }

  return (
    <section aria-label={d.review} className="flex flex-col gap-space-3">
      {review.isError && <ErrorNotice error={review.error} />}
      {r?.enrolled && r.isNew && (
        <div className="flex min-h-13 items-center justify-between gap-space-3">
          <div className="flex flex-col">
            <span id={`${id}-learn-next`} className="font-semibold">
              {d.learnNext}
            </span>
            <span id={`${id}-learn-next-hint`} className="type-body-sm text-ink-muted">
              {d.learnNextHint}
            </span>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={r.learnNext}
            aria-labelledby={`${id}-learn-next`}
            aria-describedby={`${id}-learn-next-hint`}
            disabled={save.isPending}
            onClick={() => save.mutate({ enrolled: true, learnNext: !r.learnNext })}
            className="relative h-8 w-14 shrink-0 rounded-pill border-bold border-outline bg-surface-sunken transition-colors aria-checked:bg-accent disabled:opacity-45"
          >
            <span
              aria-hidden="true"
              className={`absolute top-1/2 size-5 -translate-y-1/2 rounded-pill border-bold border-outline bg-surface transition-[left] ${
                r.learnNext ? 'left-7' : 'left-1'
              }`}
            />
          </button>
        </div>
      )}
      {save.isError && <ErrorNotice error={save.error} />}
      {r && (
        <button
          type="button"
          disabled={save.isPending}
          onClick={() => removeOrAdd(r)}
          className="h-12 rounded-md border-bold border-outline font-semibold disabled:opacity-45"
        >
          {r.enrolled ? d.removeFromReview : d.addToReview}
        </button>
      )}
      {confirming && (
        <Sheet title={d.removeTitle} onClose={() => setConfirming(false)}>
          <div className="flex flex-col gap-space-4">
            <p>{d.removeBody}</p>
            <div className="grid grid-cols-2 gap-space-3">
              <button
                type="button"
                onClick={() => setConfirming(false)}
                className="h-12 rounded-md border-bold border-outline font-semibold"
              >
                {d.cancel}
              </button>
              <button
                type="button"
                disabled={save.isPending}
                onClick={() =>
                  save.mutate(
                    { enrolled: false, learnNext: false },
                    { onSettled: () => setConfirming(false) },
                  )
                }
                className="h-12 rounded-md border-bold border-outline bg-accent font-bold text-on-accent shadow-pop disabled:opacity-45 disabled:shadow-none"
              >
                {d.removeConfirm}
              </button>
            </div>
          </div>
        </Sheet>
      )}
    </section>
  );
}

function Fact({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={`rounded-md bg-surface-sunken p-space-3 ${wide ? 'col-span-3' : ''}`}>
      <dt className="text-ink-muted">{label}</dt>
      <dd className="font-semibold">{value}</dd>
    </div>
  );
}
