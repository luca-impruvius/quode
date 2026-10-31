import { ChevronDown, ChevronLeft, ChevronRight, Ellipsis, Plus } from 'lucide-react';
import { useId, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../api/client';
import { topicApi, useTopicMutation, useTopics, type Topic } from '../api/topics';
import { ErrorNotice } from '../components/ErrorNotice';
import { Sheet } from '../components/Sheet';
import { messages } from '../messages';

const t = messages.topics;

interface Row extends Topic {
  depth: number;
  hasChildren: boolean;
  /** "Languages › Java", for pickers. */
  path: string;
}

type Open =
  | { kind: 'new' }
  | { kind: 'actions'; topic: Row }
  | { kind: 'rename'; topic: Row }
  | { kind: 'move'; topic: Row }
  | { kind: 'delete'; topic: Row };

// The API already sends parents before children; this adds depth, paths and the parent links.
function toRows(topics: Topic[]): Row[] {
  const byId = new Map<string, Row>();
  const rows = topics.map((topic) => {
    const parent = topic.parentId === null ? undefined : byId.get(topic.parentId);
    const row: Row = {
      ...topic,
      depth: parent ? parent.depth + 1 : 0,
      hasChildren: false,
      path: parent ? `${parent.path} › ${topic.name}` : topic.name,
    };
    if (parent) {
      parent.hasChildren = true;
    }
    byId.set(topic.id, row);
    return row;
  });
  return rows;
}

// A topic and everything under it: never a valid new parent for that topic.
function subtreeIds(rows: Row[], rootId: string): Set<string> {
  const ids = new Set([rootId]);
  for (const row of rows) {
    if (row.parentId !== null && ids.has(row.parentId)) {
      ids.add(row.id);
    }
  }
  return ids;
}

export function TopicsPage() {
  const topics = useTopics();
  const rows = useMemo(() => toRows(topics.data ?? []), [topics.data]);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [open, setOpen] = useState<Open | null>(null);
  const addStarter = useTopicMutation(topicApi.addStarter);

  const visible = rows.filter((row) => {
    for (let id = row.parentId; id !== null;) {
      if (!expanded.has(id)) {
        return false;
      }
      id = rows.find((r) => r.id === id)?.parentId ?? null;
    }
    return true;
  });

  function toggle(id: string) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  const close = () => setOpen(null);

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col">
      <header className="flex items-center justify-between border-b border-line px-space-3 py-space-2">
        <Link
          to="/library"
          className="flex min-h-11 items-center gap-space-1 px-space-2 font-semibold"
        >
          <ChevronLeft aria-hidden="true" size={20} strokeWidth={2} />
          {t.backToLibrary}
        </Link>
        <h1 className="type-heading">{t.title}</h1>
        <button
          type="button"
          onClick={() => setOpen({ kind: 'new' })}
          className="flex min-h-11 items-center gap-space-1 rounded-md border-bold border-outline bg-accent px-space-3 font-bold text-on-accent"
        >
          <Plus aria-hidden="true" size={18} strokeWidth={2.5} />
          {t.new}
        </button>
      </header>

      <main className="flex-1 px-space-3 py-space-2">
        {topics.isPending && (
          <p role="status" className="p-space-2 text-ink-muted">
            {messages.loading}
          </p>
        )}
        {topics.isError && <ErrorNotice error={topics.error} />}
        {topics.isSuccess && rows.length === 0 && (
          <section className="flex flex-col items-start gap-space-3 px-space-2 py-space-5">
            <p className="type-heading">{t.empty}</p>
            <p className="text-ink-muted">{t.emptyHint}</p>
            <button
              type="button"
              disabled={addStarter.isPending}
              onClick={() => addStarter.mutate()}
              className="flex h-12 items-center rounded-md border-bold border-outline bg-surface px-space-4 font-semibold disabled:opacity-45"
            >
              {t.addStarter}
            </button>
            {addStarter.isError && <ErrorNotice error={addStarter.error} />}
          </section>
        )}
        {visible.length > 0 && (
          <ul>
            {visible.map((row) => (
              <TopicRow
                key={row.id}
                row={row}
                expanded={expanded.has(row.id)}
                onToggle={() => toggle(row.id)}
                onActions={() => setOpen({ kind: 'actions', topic: row })}
              />
            ))}
          </ul>
        )}
      </main>

      {open?.kind === 'new' && (
        <NameSheet
          title={t.newTitle}
          initialName=""
          parents={rows}
          onClose={close}
          send={(name, parentId) => topicApi.create({ name, parentId: parentId ?? null })}
        />
      )}
      {open?.kind === 'rename' && (
        <NameSheet
          title={t.renameTitle}
          initialName={open.topic.name}
          onClose={close}
          send={(name) => topicApi.rename({ id: open.topic.id, name })}
        />
      )}
      {open?.kind === 'actions' && (
        <Sheet title={open.topic.path} onClose={close}>
          <div className="flex flex-col gap-space-2">
            <SheetButton onClick={() => setOpen({ kind: 'rename', topic: open.topic })}>
              {t.rename}
            </SheetButton>
            <SheetButton onClick={() => setOpen({ kind: 'move', topic: open.topic })}>
              {t.move}
            </SheetButton>
            <SheetButton onClick={() => setOpen({ kind: 'delete', topic: open.topic })}>
              {t.delete}
            </SheetButton>
            <SheetButton quiet onClick={close}>
              {t.cancel}
            </SheetButton>
          </div>
        </Sheet>
      )}
      {open?.kind === 'move' && <MoveSheet topic={open.topic} rows={rows} onClose={close} />}
      {open?.kind === 'delete' && <DeleteSheet topic={open.topic} onClose={close} />}
    </div>
  );
}

function TopicRow({
  row,
  expanded,
  onToggle,
  onActions,
}: {
  row: Row;
  expanded: boolean;
  onToggle: () => void;
  onActions: () => void;
}) {
  const nameId = useId();
  return (
    <li
      aria-labelledby={nameId}
      // One indent step per level, as in the wireframe.
      style={{ paddingLeft: row.depth * 24 }}
      className="flex min-h-12 items-center gap-space-2 border-b border-line"
    >
      {row.hasChildren ? (
        <button
          type="button"
          aria-expanded={expanded}
          aria-label={expanded ? t.collapse(row.name) : t.expand(row.name)}
          onClick={onToggle}
          className="flex size-11 items-center justify-center"
        >
          {expanded ? (
            <ChevronDown aria-hidden="true" size={20} strokeWidth={2} />
          ) : (
            <ChevronRight aria-hidden="true" size={20} strokeWidth={2} />
          )}
        </button>
      ) : (
        <span aria-hidden="true" className="w-11" />
      )}
      <span id={nameId} className={`flex-1 ${row.depth === 0 ? 'font-bold' : ''}`}>
        {row.name}
      </span>
      <span className="type-body-sm text-ink-muted" title={t.questions(row.questionCount)}>
        {row.questionCount}
      </span>
      <button
        type="button"
        aria-label={t.actionsFor(row.name)}
        onClick={onActions}
        className="flex size-11 items-center justify-center"
      >
        <Ellipsis aria-hidden="true" size={20} strokeWidth={2} />
      </button>
    </li>
  );
}

function SheetButton({
  quiet = false,
  onClick,
  children,
}: {
  quiet?: boolean;
  onClick: () => void;
  children: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex h-12 items-center justify-center rounded-md font-semibold ${
        quiet ? '' : 'border-bold border-outline'
      }`}
    >
      {children}
    </button>
  );
}

function SheetActions({
  busy,
  label,
  onCancel,
}: {
  busy: boolean;
  label: string;
  onCancel: () => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-space-3">
      <button
        type="button"
        onClick={onCancel}
        className="h-12 rounded-md border-bold border-outline font-semibold"
      >
        {t.cancel}
      </button>
      <button
        type="submit"
        disabled={busy}
        className="h-12 rounded-md border-bold border-outline bg-accent font-bold text-on-accent shadow-pop disabled:opacity-45 disabled:shadow-none"
      >
        {label}
      </button>
    </div>
  );
}

const field = 'h-12 w-full rounded-md border-2 border-outline bg-surface px-space-3';

function errorText(error: unknown): string | undefined {
  return error instanceof ApiError ? error.message : undefined;
}

function NameSheet({
  title,
  initialName,
  parents,
  onClose,
  send,
}: {
  title: string;
  initialName: string;
  /** Present for a new topic: the "Inside" picker. */
  parents?: Row[];
  onClose: () => void;
  send: (name: string, parentId: string | undefined) => Promise<unknown>;
}) {
  const [name, setName] = useState(initialName);
  const [parentId, setParentId] = useState('');
  const save = useTopicMutation(() => send(name, parentId === '' ? undefined : parentId));
  const errorId = useId();
  const error = save.isError ? (errorText(save.error) ?? messages.errors.generic) : undefined;

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate(undefined, { onSuccess: onClose });
  }

  return (
    <Sheet title={title} onClose={onClose}>
      <form onSubmit={submit} className="flex flex-col gap-space-4">
        <label className="flex flex-col gap-space-1">
          <span className="font-semibold">{t.name}</span>
          <input
            value={name}
            maxLength={60}
            onChange={(event) => setName(event.target.value)}
            aria-invalid={error !== undefined}
            aria-describedby={error === undefined ? undefined : errorId}
            className={field}
          />
          {error !== undefined && (
            <span id={errorId} className="type-body-sm text-missed">
              {error}
            </span>
          )}
        </label>
        {parents && (
          <label className="flex flex-col gap-space-1">
            <span className="font-semibold">{t.inside}</span>
            <select
              value={parentId}
              onChange={(event) => setParentId(event.target.value)}
              className={field}
            >
              <option value="">{t.topLevel}</option>
              {parents.map((row) => (
                <option key={row.id} value={row.id}>
                  {row.path}
                </option>
              ))}
            </select>
          </label>
        )}
        <SheetActions busy={save.isPending} label={t.save} onCancel={onClose} />
      </form>
    </Sheet>
  );
}

function MoveSheet({ topic, rows, onClose }: { topic: Row; rows: Row[]; onClose: () => void }) {
  const excluded = subtreeIds(rows, topic.id);
  const [parentId, setParentId] = useState(topic.parentId ?? '');
  const save = useTopicMutation(() =>
    topicApi.move({ id: topic.id, parentId: parentId === '' ? null : parentId }),
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate(undefined, { onSuccess: onClose });
  }

  return (
    <Sheet title={t.moveTitle(topic.name)} onClose={onClose}>
      <form onSubmit={submit} className="flex flex-col gap-space-4">
        <label className="flex flex-col gap-space-1">
          <span className="font-semibold">{t.moveUnder}</span>
          <select
            value={parentId}
            onChange={(event) => setParentId(event.target.value)}
            className={field}
          >
            <option value="">{t.topLevel}</option>
            {rows
              .filter((row) => !excluded.has(row.id))
              .map((row) => (
                <option key={row.id} value={row.id}>
                  {row.path}
                </option>
              ))}
          </select>
        </label>
        {save.isError && <ErrorNotice error={save.error} />}
        <SheetActions busy={save.isPending} label={t.save} onCancel={onClose} />
      </form>
    </Sheet>
  );
}

function DeleteSheet({ topic, onClose }: { topic: Row; onClose: () => void }) {
  const remove = useTopicMutation(() => topicApi.remove({ id: topic.id }));

  function submit(event: FormEvent) {
    event.preventDefault();
    remove.mutate(undefined, { onSuccess: onClose });
  }

  return (
    <Sheet title={t.deleteTitle(topic.name)} onClose={onClose}>
      <form onSubmit={submit} className="flex flex-col gap-space-4">
        <p className="text-ink-muted">{t.deleteHint}</p>
        {remove.isError && <ErrorNotice error={remove.error} />}
        <SheetActions busy={remove.isPending} label={t.delete} onCancel={onClose} />
      </form>
    </Sheet>
  );
}
