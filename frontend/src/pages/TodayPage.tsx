import { Link, useNavigate } from 'react-router';
import duckMarkUrl from '../../../design-system/logos/duck-mark.svg';
import { ApiError } from '../api/client';
import { useReviewToday, useStartReview, type ReviewExtra, type ReviewToday } from '../api/review';
import { firstName, useMe } from '../app/useMe';
import { ErrorNotice } from '../components/ErrorNotice';
import { daysFromToday } from '../lib/days';
import { messages } from '../messages';

const m = messages.today;
const primary =
  'flex min-h-12 items-center justify-center rounded-md border-bold border-outline bg-accent px-space-4 font-semibold text-on-accent';

/**
 * Today (docs/specs/M3-daily-review.md, Screens): the greeting, "N to review · M new" with Start
 * review (or Continue while today's review is unfinished), and "All done for today" once the
 * queue is empty, with Keep going / Learn more when more questions wait.
 */
export function TodayPage() {
  const me = useMe();
  const today = useReviewToday();

  return (
    <section className="flex flex-col gap-space-5">
      <p className="type-label text-ink-muted uppercase">{m.title}</p>
      <div className="flex items-center gap-space-3">
        <img src={duckMarkUrl} alt={m.duckAlt} className="size-12" />
        <h1 className="type-title">{m.greeting(firstName(me.data?.name))}</h1>
      </div>
      {today.isPending && <p className="text-ink-muted">{messages.loading}</p>}
      {today.isError && <ErrorNotice error={today.error} />}
      {today.data && <Queue today={today.data} timeZone={me.data?.timeZone} />}
    </section>
  );
}

function Queue({ today, timeZone }: { today: ReviewToday; timeZone: string | undefined }) {
  const start = useStartReview();
  const navigate = useNavigate();

  function onStart(extra: ReviewExtra) {
    if (start.isPending) {
      return;
    }
    start.mutate(extra, { onSuccess: (session) => void navigate(`/quiz/${session.id}`) });
  }

  // A 409 (nothing left after all) just refreshes the counts; they say why.
  const error =
    start.isError && !(start.error instanceof ApiError && start.error.status === 409) ? (
      <ErrorNotice error={start.error} />
    ) : null;
  const waiting = today.due + today.newWaiting > 0;

  if (today.sessionId !== null || waiting) {
    return (
      <div className="flex flex-col gap-space-4">
        {/* During Keep going / Learn more both counts are 0 (past today's limits): no "0 · 0". */}
        {waiting && <p className="type-display">{m.counts(today.due, today.newWaiting)}</p>}
        {today.sessionId !== null ? (
          <Link to={`/quiz/${today.sessionId}`} className={`${primary} flex-col`}>
            <span>{m.continue}</span>
            <span className="font-normal type-body-sm">{m.continueHint}</span>
          </Link>
        ) : (
          <button
            type="button"
            aria-disabled={start.isPending}
            onClick={() => onStart('NONE')}
            className={primary}
          >
            {start.isPending ? m.starting : m.start}
          </button>
        )}
        {error}
      </div>
    );
  }

  const nothingAtAll =
    today.doneToday + today.newToday + today.moreDue + today.moreNew === 0 &&
    today.nextDueAt === null;
  if (nothingAtAll) {
    return <p className="text-ink-muted">{m.nothingYet}</p>;
  }

  const secondary =
    'flex min-h-12 flex-col items-start rounded-md border-bold border-outline p-space-3';
  return (
    <div className="flex flex-col gap-space-4">
      <h2 className="type-title">{m.allDone}</h2>
      {today.nextDueAt !== null && (
        <p className="text-ink-muted">{m.nextDue(daysFromToday(today.nextDueAt, timeZone))}</p>
      )}
      {today.moreDue > 0 && (
        <button
          type="button"
          aria-disabled={start.isPending}
          onClick={() => onStart('KEEP_GOING')}
          className={secondary}
        >
          <span className="font-semibold">{m.keepGoing}</span>
          <span className="text-ink-muted type-body-sm">{m.keepGoingHint(today.moreDue)}</span>
        </button>
      )}
      {today.moreNew > 0 && (
        <button
          type="button"
          aria-disabled={start.isPending}
          onClick={() => onStart('LEARN_MORE')}
          className={secondary}
        >
          <span className="font-semibold">{m.learnMore}</span>
          <span className="text-ink-muted type-body-sm">{m.learnMoreHint(today.moreNew)}</span>
        </button>
      )}
      {error}
    </div>
  );
}
