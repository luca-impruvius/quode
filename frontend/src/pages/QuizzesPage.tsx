import { useId, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { rememberedQuiz, useSession, useStartQuiz, type QuizMode } from '../api/study';
import { topicPaths, useTopics } from '../api/topics';
import { ErrorNotice } from '../components/ErrorNotice';
import { messages } from '../messages';

const m = messages.quizzes;
const counts = ['10', '20', '50', 'all'] as const;
const modes: QuizMode[] = ['ALL', 'UNSEEN', 'MISSED'];

/**
 * The Quizzes tab (docs/specs/M2-exam-prep-mvp.md): start a quick quiz on a topic (its sub-topics
 * included), or continue the unfinished one. `?topicId=` preselects a topic (from the Library).
 */
export function QuizzesPage() {
  const [params] = useSearchParams();
  const topics = useTopics();
  const paths = useMemo(() => topicPaths(topics.data ?? []), [topics.data]);
  const [topicId, setTopicId] = useState(params.get('topicId') ?? '');
  const [count, setCount] = useState<(typeof counts)[number]>('20');
  const [mode, setMode] = useState<QuizMode>('ALL');
  const [shuffle, setShuffle] = useState(true);
  const start = useStartQuiz();
  const navigate = useNavigate();
  const ids = { topic: useId(), count: useId(), mode: useId(), shuffle: useId() };

  function onStart() {
    if (chosenTopic === '' || start.isPending) {
      return;
    }
    start.mutate(
      {
        kind: 'ADHOC',
        topicIds: [chosenTopic],
        mode,
        shuffle,
        ...(count === 'all' ? {} : { count: Number(count) }),
      },
      { onSuccess: (session) => void navigate(`/quiz/${session.id}`) },
    );
  }

  const options = [...paths.entries()].sort(([, a], [, b]) => a.localeCompare(b));
  // A topic from the URL that isn't (or is no longer) the user's counts as none chosen.
  const chosenTopic = topics.isSuccess && !paths.has(topicId) ? '' : topicId;
  return (
    <section className="flex flex-col gap-space-4">
      <h1 className="type-title">{m.title}</h1>
      <ContinueQuiz />

      <h2 className="font-semibold">{m.start}</h2>
      {topics.isError && <ErrorNotice error={topics.error} />}
      {topics.isSuccess && options.length === 0 ? (
        <p className="text-ink-muted">{m.noTopics}</p>
      ) : (
        <div className="flex flex-col gap-space-4">
          <div className="flex flex-col gap-space-1">
            <label htmlFor={ids.topic} className="font-semibold">
              {m.topic}
            </label>
            <select
              id={ids.topic}
              value={chosenTopic}
              onChange={(e) => setTopicId(e.target.value)}
              className="min-h-11 rounded-md border-bold border-outline bg-surface px-space-3"
            >
              <option value="">{m.chooseTopic}</option>
              {options.map(([id, path]) => (
                <option key={id} value={id}>
                  {path}
                </option>
              ))}
            </select>
          </div>

          <Choice
            legend={m.count}
            name={ids.count}
            values={counts}
            labels={m.counts}
            value={count}
            onChange={setCount}
          />
          <Choice
            legend={m.mode}
            name={ids.mode}
            values={modes}
            labels={m.modes}
            value={mode}
            onChange={setMode}
          />

          <label htmlFor={ids.shuffle} className="flex min-h-11 items-center gap-space-2">
            <input
              id={ids.shuffle}
              type="checkbox"
              checked={shuffle}
              onChange={(e) => setShuffle(e.target.checked)}
              className="size-5"
            />
            {m.shuffle}
          </label>

          <button
            type="button"
            disabled={chosenTopic === ''}
            aria-disabled={start.isPending}
            onClick={onStart}
            className="min-h-12 rounded-md border-bold border-outline bg-accent font-semibold text-on-accent disabled:primary-disabled"
          >
            {start.isPending ? m.starting : m.start}
          </button>
          {start.isError && <ErrorNotice error={start.error} />}
        </div>
      )}
    </section>
  );
}

function Choice<T extends string>(props: {
  legend: string;
  name: string;
  values: readonly T[];
  labels: Record<T, string>;
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <fieldset className="flex flex-col gap-space-1">
      <legend className="font-semibold">{props.legend}</legend>
      <div className="flex flex-wrap gap-space-2">
        {props.values.map((v) => (
          <label
            key={v}
            className={`flex min-h-11 items-center rounded-pill border-bold px-space-4 has-focus-visible:outline-3 has-focus-visible:outline-focus ${
              v === props.value ? 'border-outline bg-accent text-on-accent' : 'border-line'
            }`}
          >
            <input
              type="radio"
              name={props.name}
              value={v}
              checked={v === props.value}
              onChange={() => props.onChange(v)}
              className="sr-only"
            />
            {props.labels[v]}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

/** The quiz this browser started last, while it still has questions left. */
function ContinueQuiz() {
  const id = rememberedQuiz();
  return id === null ? null : <ContinueLink id={id} />;
}

function ContinueLink({ id }: { id: string }) {
  const session = useSession(id);
  const data = session.data;
  if (!data || data.finishedAt !== null || data.current === null) {
    return null;
  }
  return (
    <Link
      to={`/quiz/${id}`}
      className="flex flex-col rounded-md border-bold border-outline bg-surface-sunken p-space-4"
    >
      <span className="font-semibold">{m.continue}</span>
      <span className="text-ink-muted type-body-sm">
        {m.continueHint(data.answered, data.total)}
      </span>
    </Link>
  );
}
