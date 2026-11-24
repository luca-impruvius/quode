import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import {
  rememberQuiz,
  sessionKey,
  studyApi,
  useSession,
  useStartQuiz,
  type AnswerResult,
  type QuestionCard,
  type SelfGrade,
} from '../api/study';
import { ErrorNotice } from '../components/ErrorNotice';
import { Markdown } from '../components/Markdown';
import { messages } from '../messages';

const m = messages.quiz;
const grades: SelfGrade[] = ['GOT_IT', 'PARTIAL', 'MISSED'];

/**
 * One quick quiz (docs/specs/M2-exam-prep-mvp.md). Progress lives on the server, so reopening this
 * URL resumes at the same question. Choice questions are graded at once; flashcard and open ones
 * reveal their answer, then you grade yourself. After the last answer come the results.
 */
export function QuizPage() {
  const { id = '' } = useParams();
  const session = useSession(id);
  const queryClient = useQueryClient();
  // The answered card stays on screen with its feedback until Next.
  const [feedback, setFeedback] = useState<{
    card: QuestionCard;
    chosen: string[];
    result: AnswerResult;
  } | null>(null);

  const answer = useMutation({
    mutationFn: studyApi.answer,
    onSuccess: (result) => queryClient.setQueryData(sessionKey(id), result.session),
  });

  const data = session.data;
  const card = feedback?.card ?? data?.current ?? null;
  const done = data !== undefined && feedback === null && data.current === null;

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col">
      <header className="flex items-center justify-between border-b border-line px-space-3 py-space-2">
        <Link
          to="/quizzes"
          className="flex min-h-11 items-center gap-space-1 px-space-2 font-semibold"
        >
          <ChevronLeft aria-hidden="true" size={20} strokeWidth={2} />
          {m.quit}
        </Link>
        {data && !done && (
          <span className="px-space-2 text-ink-muted" aria-live="polite">
            {m.progress(Math.min(data.answered + (feedback ? 0 : 1), data.total), data.total)}
          </span>
        )}
      </header>
      <main className="flex flex-1 flex-col gap-space-4 px-space-4 py-space-4">
        {session.isPending && (
          <p role="status" className="text-ink-muted">
            {messages.loading}
          </p>
        )}
        {session.isError && <ErrorNotice error={session.error} />}
        {done && <Results id={id} />}
        {!done && card && (
          <Card
            key={card.id}
            card={card}
            feedback={feedback}
            busy={answer.isPending}
            error={answer.error}
            onChoose={(chosen) =>
              answer.mutate(
                { id, questionId: card.id, selectedOptionIds: chosen },
                { onSuccess: (result) => setFeedback({ card, chosen, result }) },
              )
            }
            onGrade={(grade) => answer.mutate({ id, questionId: card.id, grade })}
            onNext={() => {
              answer.reset();
              setFeedback(null);
            }}
            last={feedback?.result.session.current === null}
          />
        )}
      </main>
    </div>
  );
}

interface CardProps {
  card: QuestionCard;
  feedback: { chosen: string[]; result: AnswerResult } | null;
  busy: boolean;
  error: unknown;
  last: boolean;
  onChoose: (chosen: string[]) => void;
  onGrade: (grade: SelfGrade) => void;
  onNext: () => void;
}

function Card({ card, feedback, busy, error, last, onChoose, onGrade, onNext }: CardProps) {
  const choice = card.type === 'SINGLE_CHOICE' || card.type === 'MULTIPLE_CHOICE';
  const multiple = card.type === 'MULTIPLE_CHOICE';
  const [chosen, setChosen] = useState<string[]>([]);
  const [revealed, setRevealed] = useState(false);
  const promptId = useId();
  const next = useRef<HTMLButtonElement>(null);
  const answered = feedback !== null;

  // After feedback, Next takes focus so Enter moves on.
  useEffect(() => {
    if (answered) {
      next.current?.focus();
    }
  }, [answered]);

  function toggle(optionId: string) {
    if (answered) {
      return;
    }
    setChosen((current) =>
      multiple
        ? current.includes(optionId)
          ? current.filter((o) => o !== optionId)
          : [...current, optionId]
        : [optionId],
    );
  }

  const correct = new Set(feedback?.result.correctOptionIds ?? []);
  const button = 'min-h-12 rounded-md border-bold border-outline px-space-4 font-semibold';
  return (
    <article aria-labelledby={promptId} className="flex flex-col gap-space-4">
      <div id={promptId} className="type-question">
        <Markdown text={card.promptMd} />
      </div>

      {choice && (
        <fieldset className="flex flex-col gap-space-2">
          <legend className="text-ink-muted type-body-sm">
            {multiple ? m.chooseAll : m.chooseOne}
          </legend>
          {card.options.map((option) => {
            const picked = chosen.includes(option.id);
            const state = !answered
              ? picked
                ? 'border-outline bg-surface-sunken'
                : 'border-line'
              : correct.has(option.id)
                ? 'border-correct bg-correct-bg'
                : picked
                  ? 'border-missed bg-missed-bg'
                  : 'border-line opacity-70';
            return (
              <label
                key={option.id}
                className={`flex min-h-12 items-start gap-space-3 rounded-md border-bold p-space-3 ${state}`}
              >
                <input
                  type={multiple ? 'checkbox' : 'radio'}
                  name={card.id}
                  checked={picked}
                  disabled={answered}
                  onChange={() => toggle(option.id)}
                  className="mt-1 size-5"
                />
                <span className="flex-1">
                  <Markdown text={option.textMd} />
                  {answered && correct.has(option.id) && (
                    <span className="type-label text-ink-muted">{m.rightAnswer}</span>
                  )}
                  {answered && picked && !correct.has(option.id) && (
                    <span className="type-label text-ink-muted">{m.yourChoice}</span>
                  )}
                </span>
              </label>
            );
          })}
        </fieldset>
      )}

      {choice && !answered && (
        <button
          type="button"
          disabled={chosen.length === 0}
          aria-disabled={busy}
          onClick={() => !busy && onChoose(chosen)}
          className={`${button} bg-accent text-on-accent disabled:opacity-45`}
        >
          {m.check}
        </button>
      )}

      {choice && feedback && (
        <section role="status" className="flex flex-col gap-space-2">
          <p className="font-semibold">
            {feedback.result.outcome === 'CORRECT' ? m.correct : m.wrong}
          </p>
          {feedback.result.explanationMd && (
            <div className="rounded-md bg-surface-sunken p-space-3">
              <p className="type-label text-ink-muted">{m.explanation}</p>
              <Markdown text={feedback.result.explanationMd} />
            </div>
          )}
        </section>
      )}

      {!choice && !revealed && (
        <button type="button" onClick={() => setRevealed(true)} className={button}>
          {m.reveal}
        </button>
      )}

      {!choice && revealed && (
        <>
          <div
            role="status"
            className="flex flex-col gap-space-2 rounded-md bg-surface-sunken p-space-3"
          >
            <p className="type-label text-ink-muted">{m.answer}</p>
            <Markdown text={card.answerMd ?? ''} />
            {card.explanationMd && (
              <>
                <p className="type-label text-ink-muted">{m.explanation}</p>
                <Markdown text={card.explanationMd} />
              </>
            )}
          </div>
          <fieldset className="flex flex-col gap-space-2">
            <legend className="font-semibold">{m.gradePrompt}</legend>
            <div className="flex gap-space-2">
              {grades.map((grade) => (
                <button
                  key={grade}
                  type="button"
                  aria-disabled={busy}
                  onClick={() => !busy && onGrade(grade)}
                  className={`${button} flex-1`}
                >
                  {m.grades[grade]}
                </button>
              ))}
            </div>
          </fieldset>
        </>
      )}

      {answered && (
        <button
          ref={next}
          type="button"
          onClick={onNext}
          className={`${button} bg-accent text-on-accent`}
        >
          {last ? m.results : m.next}
        </button>
      )}
      {error !== null && <ErrorNotice error={error} />}
    </article>
  );
}

/** The score and missed questions; Retry missed starts a new quiz from them. */
function Results({ id }: { id: string }) {
  const finish = useMutation({ mutationFn: studyApi.finish });
  const retry = useStartQuiz();
  const navigate = useNavigate();
  const heading = useRef<HTMLHeadingElement>(null);
  const { mutate } = finish;

  useEffect(() => {
    rememberQuiz(null);
    mutate(id);
  }, [id, mutate]);
  useEffect(() => {
    if (finish.data) {
      heading.current?.focus();
    }
  }, [finish.data]);

  if (finish.isError) {
    return <ErrorNotice error={finish.error} />;
  }
  if (!finish.data) {
    return (
      <p role="status" className="text-ink-muted">
        {messages.loading}
      </p>
    );
  }
  const r = finish.data;
  const missed = r.missedQuestionIds;
  return (
    <section className="flex flex-col gap-space-4">
      <h1 ref={heading} tabIndex={-1} className="type-title">
        {m.doneTitle}
      </h1>
      <p className="type-display">{m.score(r.correct, r.total)}</p>
      {(r.partial > 0 || r.wrong > 0) && (
        <p className="text-ink-muted">{m.breakdown(r.partial, r.wrong)}</p>
      )}
      {missed.length > 0 && (
        <button
          type="button"
          aria-disabled={retry.isPending}
          onClick={() =>
            !retry.isPending &&
            retry.mutate(
              { kind: 'ADHOC', questionIds: missed },
              { onSuccess: (session) => void navigate(`/quiz/${session.id}`) },
            )
          }
          className="min-h-12 rounded-md border-bold border-outline bg-accent font-semibold text-on-accent"
        >
          {m.retryMissed(missed.length)}
        </button>
      )}
      {retry.isError && <ErrorNotice error={retry.error} />}
      <Link to="/quizzes" className="font-semibold underline">
        {m.backToQuizzes}
      </Link>
    </section>
  );
}
