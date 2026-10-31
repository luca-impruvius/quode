import { ChevronDown, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import {
  useLibrary,
  type LibraryFilters,
  type LibraryItem,
  type LibrarySort,
  type LibraryStatus,
} from '../api/library';
import type { QuestionType } from '../api/questions';
import { topicPaths, useTopics } from '../api/topics';
import { ErrorNotice } from '../components/ErrorNotice';
import { Sheet } from '../components/Sheet';
import { messages } from '../messages';

const l = messages.library;
const types: QuestionType[] = ['FLASHCARD', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'OPEN_TEXT'];
const statuses: LibraryStatus[] = ['active', 'archived', 'flagged'];
const sorts: LibrarySort[] = ['created', 'updated', 'difficulty'];

const pick = <T extends string>(value: string | null, allowed: readonly T[], fallback: T): T =>
  allowed.includes(value as T) ? (value as T) : fallback;

// Filters live in the URL (shareable; Me links to ?status=archived).
function readFilters(params: URLSearchParams): LibraryFilters {
  const difficulty = Number(params.get('difficulty'));
  const type = params.get('type');
  return {
    q: params.get('q') ?? undefined,
    topicId: params.get('topicId') ?? undefined,
    type: types.includes(type as QuestionType) ? (type as QuestionType) : undefined,
    difficulty: [1, 2, 3].includes(difficulty) ? difficulty : undefined,
    status: pick(params.get('status'), statuses, 'active'),
    sort: pick(params.get('sort'), sorts, 'created'),
  } as LibraryFilters;
}

// The list shows text, not rendered Markdown: emphasis marks and link targets go.
function plainText(markdown: string): string {
  return markdown
    .replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[*_`#>~]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

type Open = 'topic' | 'type' | 'difficulty' | 'status' | null;

export function LibraryPage() {
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => readFilters(params), [params]);
  const library = useLibrary(filters);
  const topics = useTopics();
  const paths = useMemo(() => topicPaths(topics.data ?? []), [topics.data]);
  const [text, setText] = useState(filters.q ?? '');
  const [open, setOpen] = useState<Open>(null);

  function update(key: string, value: string | undefined) {
    const next = new URLSearchParams(params);
    if (value === undefined || value === '') {
      next.delete(key);
    } else {
      next.set(key, value);
    }
    setParams(next, { replace: true });
  }

  // Search as you type, once typing pauses.
  useEffect(() => {
    if (text.trim() === (filters.q ?? '')) {
      return;
    }
    const timer = setTimeout(() => update('q', text.trim()), 300);
    return () => clearTimeout(timer);
    // update reads the current params; re-running on every change would reset the timer needlessly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text]);

  const topicName = filters.topicId
    ? (topics.data?.find((t) => t.id === filters.topicId)?.name ?? null)
    : null;
  const items = library.data?.pages.flatMap((page) => page.items) ?? [];
  const total = library.data?.pages[0]?.totalElements;
  const filtered =
    filters.q !== undefined ||
    filters.topicId !== undefined ||
    filters.type !== undefined ||
    filters.difficulty !== undefined ||
    filters.status !== 'active';

  const chip = (active: boolean) =>
    `flex min-h-11 items-center gap-space-1 rounded-pill px-space-3 type-body-sm ${
      active
        ? 'border-bold border-outline bg-accent font-semibold text-on-accent'
        : 'border-2 border-line bg-surface'
    }`;

  return (
    <section className="flex flex-col gap-space-3">
      <div className="flex items-center justify-between">
        <h1 className="type-title">{messages.tabs.library}</h1>
        <Link
          to="/topics"
          className="flex min-h-11 items-center rounded-md border-bold border-outline px-space-3 font-semibold"
        >
          {messages.topics.title}
        </Link>
      </div>

      <label className="relative flex items-center">
        <span className="sr-only">{l.search}</span>
        <Search aria-hidden="true" size={18} strokeWidth={2} className="absolute left-space-3" />
        <input
          type="search"
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder={l.search}
          className="h-12 w-full rounded-md border-2 border-outline bg-surface pr-space-3 pl-10"
        />
      </label>

      <div className="flex flex-wrap gap-space-2">
        <button
          type="button"
          className={chip(filters.topicId !== undefined)}
          onClick={() => setOpen('topic')}
        >
          {l.topicChip(topicName)}
          <ChevronDown aria-hidden="true" size={16} strokeWidth={2} />
        </button>
        <button
          type="button"
          className={chip(filters.type !== undefined)}
          onClick={() => setOpen('type')}
        >
          {l.typeChip(filters.type ? messages.questionTypeNames[filters.type] : null)}
          <ChevronDown aria-hidden="true" size={16} strokeWidth={2} />
        </button>
        <button
          type="button"
          className={chip(filters.difficulty !== undefined)}
          onClick={() => setOpen('difficulty')}
        >
          {l.difficultyChip(
            filters.difficulty
              ? (messages.editor.difficulties[filters.difficulty - 1] ?? null)
              : null,
          )}
          <ChevronDown aria-hidden="true" size={16} strokeWidth={2} />
        </button>
        <button
          type="button"
          className={chip(filters.status !== 'active')}
          onClick={() => setOpen('status')}
        >
          {l.statusChip(filters.status)}
          <ChevronDown aria-hidden="true" size={16} strokeWidth={2} />
        </button>
      </div>

      <div className="flex items-center justify-between gap-space-2 type-body-sm">
        <span className="text-ink-muted" aria-live="polite">
          {total === undefined
            ? ''
            : topicName !== null
              ? l.countInTopic(total, topicName)
              : l.count(total)}
        </span>
        <label className="flex items-center gap-space-1 font-semibold">
          <span className="sr-only">{l.sort}</span>
          <select
            aria-label={l.sort}
            value={filters.sort}
            onChange={(event) =>
              update('sort', event.target.value === 'created' ? undefined : event.target.value)
            }
            className="min-h-11 rounded-md bg-transparent"
          >
            {sorts.map((sort) => (
              <option key={sort} value={sort}>
                {l.sorts[sort]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {library.isError && <ErrorNotice error={library.error} />}
      {library.isPending && (
        <p role="status" className="text-ink-muted">
          {messages.loading}
        </p>
      )}
      {library.isSuccess && items.length === 0 && (
        <p className="py-space-5 text-ink-muted">{filtered ? l.noMatches : l.noQuestions}</p>
      )}
      {items.length > 0 && (
        <ul aria-label={l.results} className="flex flex-col">
          {items.map((question) => (
            <Row key={question.id} question={question} />
          ))}
        </ul>
      )}
      {library.hasNextPage && (
        <button
          type="button"
          disabled={library.isFetchingNextPage}
          onClick={() => void library.fetchNextPage()}
          className="h-12 rounded-md border-bold border-outline font-semibold disabled:opacity-45"
        >
          {l.loadMore}
        </button>
      )}

      {open === 'topic' && (
        <Choices
          title={l.topic}
          options={[
            { value: '', label: l.allTopics },
            ...(topics.data ?? []).map((t) => ({ value: t.id, label: paths.get(t.id) ?? t.name })),
          ]}
          value={filters.topicId ?? ''}
          onChoose={(value) => update('topicId', value)}
          onClose={() => setOpen(null)}
        />
      )}
      {open === 'type' && (
        <Choices
          title={l.type}
          options={[
            { value: '', label: l.anyType },
            ...types.map((t) => ({ value: t, label: messages.questionTypeNames[t] })),
          ]}
          value={filters.type ?? ''}
          onChoose={(value) => update('type', value)}
          onClose={() => setOpen(null)}
        />
      )}
      {open === 'difficulty' && (
        <Choices
          title={l.difficulty}
          options={[
            { value: '', label: l.anyDifficulty },
            ...messages.editor.difficulties.map((label, i) => ({ value: String(i + 1), label })),
          ]}
          value={filters.difficulty ? String(filters.difficulty) : ''}
          onChoose={(value) => update('difficulty', value)}
          onClose={() => setOpen(null)}
        />
      )}
      {open === 'status' && (
        <Choices
          title={l.status}
          options={statuses.map((s) => ({ value: s, label: l.statuses[s] }))}
          value={filters.status}
          onChoose={(value) => update('status', value === 'active' ? undefined : value)}
          onClose={() => setOpen(null)}
        />
      )}
    </section>
  );
}

function Row({ question: q }: { question: LibraryItem }) {
  const meta = [q.primaryTopic?.name, messages.questionTypeNames[q.type]]
    .filter(Boolean)
    .join(' · ');
  return (
    <li className="border-b border-line">
      <Link to={`/questions/${q.id}`} className="flex flex-col gap-space-1 py-space-3">
        <span className="line-clamp-2 font-semibold">{plainText(q.promptMd)}</span>
        <span className="flex flex-wrap items-center gap-space-2 type-body-sm text-ink-muted">
          {meta}
          {q.archived && (
            <span className="rounded-pill bg-surface-sunken px-space-2 text-ink">{l.archived}</span>
          )}
          {q.flagged && (
            <span className="rounded-pill bg-missed-bg px-space-2 text-ink">{l.flagged}</span>
          )}
        </span>
      </Link>
    </li>
  );
}

/** A sheet of mutually exclusive choices; picking one applies it and closes the sheet. */
function Choices({
  title,
  options,
  value,
  onChoose,
  onClose,
}: {
  title: string;
  options: { value: string; label: string }[];
  value: string;
  onChoose: (value: string) => void;
  onClose: () => void;
}) {
  return (
    <Sheet title={title} onClose={onClose}>
      <div
        role="radiogroup"
        aria-label={title}
        className="flex max-h-[60dvh] flex-col overflow-y-auto"
      >
        {options.map((option) => (
          <label
            key={option.value}
            className="flex min-h-11 cursor-pointer items-center gap-space-3 border-b border-line px-space-2"
          >
            <input
              type="radio"
              name={title}
              checked={value === option.value}
              onChange={() => {
                onChoose(option.value);
                onClose();
              }}
              className="size-5 accent-ink"
            />
            {option.label}
          </label>
        ))}
      </div>
    </Sheet>
  );
}
