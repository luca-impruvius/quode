import { Plus, Star, X } from 'lucide-react';
import { useEffect, useId, useMemo, useState } from 'react';
import { useFieldArray, type UseFormReturn } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client';
import {
  questionApi,
  useQuestion,
  useQuestionMutation,
  type QuestionType,
  type QuestionView,
} from '../api/questions';
import { useTopics, type Topic } from '../api/topics';
import { ErrorNotice } from '../components/ErrorNotice';
import { MarkdownField } from '../components/MarkdownField';
import { Sheet } from '../components/Sheet';
import { useZodForm } from '../lib/forms';
import {
  emptyOption,
  isChoice,
  MAX_OPTIONS,
  MAX_TOPICS,
  MIN_OPTIONS,
  questionSchema,
  toQuestionInput,
  type QuestionFormValues,
} from '../lib/questionSchema';
import { messages } from '../messages';

const e = messages.editor;
const types: QuestionType[] = ['FLASHCARD', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'OPEN_TEXT'];

const blankQuestion: QuestionFormValues = {
  type: 'FLASHCARD',
  promptMd: '',
  answerMd: '',
  explanationMd: '',
  difficulty: 2,
  sourceUrl: '',
  topicIds: [],
  primaryTopicId: '',
  options: [],
};

function formValues(q: QuestionView): QuestionFormValues {
  return {
    type: q.type,
    promptMd: q.promptMd,
    answerMd: q.answerMd ?? '',
    explanationMd: q.explanationMd ?? '',
    difficulty: q.difficulty,
    sourceUrl: q.sourceUrl ?? '',
    topicIds: q.topics.map((t) => t.id),
    primaryTopicId: q.topics.find((t) => t.primary)?.id ?? '',
    options: q.options.map((o) => ({ textMd: o.textMd, correct: o.correct })),
  };
}

// "Languages › Java" for every topic; the list comes parents first.
function topicPaths(topics: Topic[]): Map<string, string> {
  const paths = new Map<string, string>();
  for (const topic of topics) {
    const parent = topic.parentId === null ? undefined : paths.get(topic.parentId);
    paths.set(topic.id, parent === undefined ? topic.name : `${parent} › ${topic.name}`);
  }
  return paths;
}

/** /add for a new question, /questions/:id/edit for an existing one. */
export function EditorPage() {
  const { id } = useParams();
  // The form keeps what was loaded at mount: no background refetch may replace typed text.
  const question = useQuestion(id, { live: false });
  // Bumped only by "Reload latest", which is the one way to swap in the server's version.
  const [reloads, setReloads] = useState(0);

  if (id !== undefined && question.isPending) {
    return (
      <p role="status" className="p-space-4 text-ink-muted">
        {messages.loading}
      </p>
    );
  }
  if (id !== undefined && question.isError) {
    return (
      <main className="mx-auto max-w-md p-space-4">
        <ErrorNotice error={question.error} />
      </main>
    );
  }
  return (
    <EditorForm
      key={reloads}
      existing={question.data}
      onReload={() => void question.refetch().then(() => setReloads((n) => n + 1))}
    />
  );
}

function EditorForm({
  existing,
  onReload,
}: {
  existing: QuestionView | undefined;
  onReload: () => void;
}) {
  const navigate = useNavigate();
  const topics = useTopics();
  const form = useZodForm<QuestionFormValues, QuestionFormValues>(questionSchema, {
    defaultValues: existing ? formValues(existing) : blankQuestion,
  });
  const { register, watch, setValue, formState } = form;
  const [moreOpen, setMoreOpen] = useState(false);
  const [picking, setPicking] = useState(false);
  const [saved, setSaved] = useState(false);

  const create = useQuestionMutation(questionApi.create);
  const update = useQuestionMutation(questionApi.update);
  const save = existing ? update : create;

  const type = watch('type');
  const choice = isChoice(type);

  // Explanation and source link live under "More options": open it when one of them is wrong,
  // or Save would seem to do nothing.
  const hiddenError =
    formState.errors.explanationMd !== undefined || formState.errors.sourceUrl !== undefined;
  useEffect(() => {
    if (hiddenError) {
      setMoreOpen(true);
    }
  }, [hiddenError, formState.submitCount]);

  const sourceErrorId = useId();

  // After "Save & add another", straight back to typing the next question.
  useEffect(() => {
    if (saved) {
      form.setFocus('promptMd');
    }
  }, [saved, form]);

  // A choice question starts with two empty options.
  useEffect(() => {
    if (choice && form.getValues('options').length < MIN_OPTIONS) {
      setValue('options', [emptyOption(), emptyOption()]);
    }
  }, [choice, form, setValue]);

  const paths = useMemo(() => {
    const fromTree = topicPaths(topics.data ?? []);
    existing?.topics.forEach((t) => {
      if (!fromTree.has(t.id)) {
        fromTree.set(t.id, t.path);
      }
    });
    return fromTree;
  }, [topics.data, existing]);

  function submit(next: 'open' | 'another') {
    return form.handleSubmit((values) => {
      setSaved(false);
      const input = toQuestionInput(values);
      if (existing) {
        update.mutate(
          { id: existing.id, input: { ...input, version: existing.version } },
          { onSuccess: (q) => void navigate(`/questions/${q.id}`) },
        );
        return;
      }
      create.mutate(input, {
        onSuccess: (q) => {
          if (next === 'open') {
            void navigate(`/questions/${q.id}`);
            return;
          }
          // Fast capture: the next question keeps type, difficulty and topics.
          form.reset({
            ...blankQuestion,
            type: values.type,
            difficulty: values.difficulty,
            topicIds: values.topicIds,
            primaryTopicId: values.primaryTopicId,
            options: isChoice(values.type) ? [emptyOption(), emptyOption()] : [],
          });
          create.reset();
          setSaved(true);
        },
      });
    });
  }

  const stale = save.error instanceof ApiError && save.error.status === 409;

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col">
      <header className="flex items-center justify-between border-b border-line px-space-3 py-space-2">
        <Link
          to={existing ? `/questions/${existing.id}` : '/library'}
          className="flex min-h-11 items-center px-space-2"
        >
          {e.cancel}
        </Link>
        <h1 className="type-heading">{existing ? e.editTitle : e.newTitle}</h1>
        <span className="w-16" aria-hidden="true" />
      </header>

      <form
        noValidate
        onSubmit={(event) => void submit('open')(event)}
        className="flex flex-1 flex-col"
      >
        <main className="flex flex-1 flex-col gap-space-4 px-space-4 py-space-4">
          {saved && (
            <p role="status" className="rounded-md bg-correct-bg px-space-4 py-space-3">
              {e.saved}
            </p>
          )}
          <MarkdownField
            label={e.question}
            value={watch('promptMd')}
            error={formState.errors.promptMd?.message}
            rows={3}
            textarea={register('promptMd')}
          />
          <MarkdownField
            label={choice ? e.answerOptional : e.answer}
            value={watch('answerMd')}
            error={formState.errors.answerMd?.message}
            hint={e.markdownHint}
            textarea={register('answerMd')}
          />
          {choice && <OptionsEditor form={form} />}
          <TopicChips
            form={form}
            paths={paths}
            onAdd={() => setPicking(true)}
            error={formState.errors.topicIds?.message}
          />

          <div className="h-px bg-line" />
          <button
            type="button"
            aria-expanded={moreOpen}
            onClick={() => setMoreOpen((open) => !open)}
            className="flex min-h-11 items-center justify-between font-bold"
          >
            {e.moreOptions}
            <span aria-hidden="true">{moreOpen ? '▴' : '▾'}</span>
          </button>
          {moreOpen && (
            <>
              <Segmented
                legend={e.type}
                options={types.map((t) => ({ value: t, label: messages.questionTypes[t] }))}
                value={type}
                onChange={(t) => setValue('type', t, { shouldValidate: formState.isSubmitted })}
              />
              <Segmented
                legend={e.difficulty}
                options={e.difficulties.map((label, i) => ({ value: i + 1, label }))}
                value={watch('difficulty')}
                onChange={(d) => setValue('difficulty', d)}
              />
              <MarkdownField
                label={e.explanation}
                value={watch('explanationMd')}
                error={formState.errors.explanationMd?.message}
                rows={3}
                textarea={register('explanationMd')}
              />
              <label className="flex flex-col gap-space-1">
                <span className="font-bold">{e.source}</span>
                <input
                  type="url"
                  inputMode="url"
                  placeholder="https://"
                  aria-invalid={formState.errors.sourceUrl !== undefined}
                  aria-describedby={formState.errors.sourceUrl ? sourceErrorId : undefined}
                  className="h-12 rounded-md border-2 border-line bg-surface px-space-3"
                  {...register('sourceUrl')}
                />
              </label>
              {formState.errors.sourceUrl && (
                <span id={sourceErrorId} className="-mt-space-3 type-body-sm text-missed">
                  {formState.errors.sourceUrl.message}
                </span>
              )}
            </>
          )}

          {stale && (
            <div
              role="alert"
              className="flex flex-col gap-space-2 rounded-md bg-missed-bg p-space-4"
            >
              <p className="font-semibold">{e.stale}</p>
              <p className="type-body-sm">{e.staleHint}</p>
              <button
                type="button"
                onClick={onReload}
                className="h-11 self-start rounded-md border-bold border-outline bg-surface px-space-4 font-semibold"
              >
                {e.reload}
              </button>
            </div>
          )}
          {save.isError && !stale && <ErrorNotice error={save.error} />}
        </main>

        <div
          className={`sticky bottom-0 grid gap-space-3 border-t border-line bg-bg px-space-4 pt-space-3 pb-[calc(1.5rem+env(safe-area-inset-bottom))] ${
            existing ? 'grid-cols-1' : 'grid-cols-2'
          }`}
        >
          {!existing && (
            <button
              type="button"
              disabled={save.isPending}
              onClick={(event) => void submit('another')(event)}
              className="h-13 rounded-lg border-bold border-outline font-semibold disabled:opacity-45"
            >
              {e.saveAndAdd}
            </button>
          )}
          <button
            type="submit"
            disabled={save.isPending}
            className="h-13 rounded-lg border-bold border-outline bg-accent font-bold text-on-accent shadow-pop active:translate-x-0.5 active:translate-y-0.5 active:shadow-none disabled:opacity-45 disabled:shadow-none"
          >
            {e.save}
          </button>
        </div>
      </form>

      {picking && (
        <TopicPicker
          topics={topics.data ?? []}
          paths={paths}
          form={form}
          onClose={() => setPicking(false)}
        />
      )}
    </div>
  );
}

function Segmented<T extends string | number>({
  legend,
  options,
  value,
  onChange,
}: {
  legend: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  const name = useId();
  return (
    <fieldset className="flex flex-col gap-space-2">
      <legend className="mb-space-2 font-bold">{legend}</legend>
      <div
        className="grid rounded-md border-2 border-outline"
        style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}
      >
        {options.map((option) => (
          <label
            key={String(option.value)}
            className="flex min-h-11 cursor-pointer items-center justify-center type-body-sm first:rounded-l-[10px] last:rounded-r-[10px] has-checked:bg-ink has-checked:font-semibold has-checked:text-surface has-focus-visible:outline-3 has-focus-visible:outline-focus"
          >
            <input
              type="radio"
              className="sr-only"
              name={name}
              checked={value === option.value}
              onChange={() => onChange(option.value)}
            />
            {option.label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function OptionsEditor({
  form,
}: {
  form: UseFormReturn<QuestionFormValues, unknown, QuestionFormValues>;
}) {
  const { control, register, watch, setValue, formState } = form;
  const options = useFieldArray({ control, name: 'options' });
  const single = watch('type') === 'SINGLE_CHOICE';
  const values = watch('options');
  const listError = formState.errors.options?.root?.message ?? formState.errors.options?.message;
  const id = useId();

  function markCorrect(index: number, correct: boolean) {
    if (single) {
      values.forEach((_, i) => setValue(`options.${i}.correct`, i === index));
    } else {
      setValue(`options.${index}.correct`, correct);
    }
  }

  return (
    <fieldset
      aria-describedby={listError ? `${id}-list` : undefined}
      className="flex flex-col gap-space-2"
    >
      <legend className="mb-space-2 font-bold">{e.options}</legend>
      {options.fields.map((field, i) => {
        const textError = formState.errors.options?.[i]?.textMd?.message;
        return (
          <div key={field.id} className="flex flex-col gap-space-1">
            <div className="flex items-center gap-space-2">
              {/* A 44px target around the small native control. */}
              <label className="flex size-11 shrink-0 cursor-pointer items-center justify-center">
                <input
                  type={single ? 'radio' : 'checkbox'}
                  name={`${id}-correct`}
                  aria-label={e.correctOption(i + 1)}
                  checked={values[i]?.correct ?? false}
                  onChange={(event) => markCorrect(i, event.target.checked)}
                  className="size-6 accent-ink"
                />
              </label>
              <input
                aria-label={e.option(i + 1)}
                aria-invalid={textError !== undefined}
                aria-describedby={textError ? `${id}-${i}` : undefined}
                className="h-12 min-w-0 flex-1 rounded-md border-2 border-outline bg-surface px-space-3"
                {...register(`options.${i}.textMd`)}
              />
              <button
                type="button"
                aria-label={e.removeOption(i + 1)}
                disabled={options.fields.length <= MIN_OPTIONS}
                onClick={() => options.remove(i)}
                className="flex size-11 shrink-0 items-center justify-center disabled:opacity-30"
              >
                <X aria-hidden="true" size={20} strokeWidth={2} />
              </button>
            </div>
            {textError && (
              <span id={`${id}-${i}`} className="type-body-sm text-missed">
                {textError}
              </span>
            )}
          </div>
        );
      })}
      {listError && (
        <span id={`${id}-list`} className="type-body-sm text-missed">
          {listError}
        </span>
      )}
      <button
        type="button"
        disabled={options.fields.length >= MAX_OPTIONS}
        onClick={() => options.append(emptyOption())}
        className="flex h-11 items-center gap-space-1 self-start rounded-pill border-2 border-dashed border-ink-muted px-space-3 type-body-sm disabled:opacity-30"
      >
        <Plus aria-hidden="true" size={16} strokeWidth={2} />
        {e.addOption}
      </button>
    </fieldset>
  );
}

function TopicChips({
  form,
  paths,
  onAdd,
  error,
}: {
  form: UseFormReturn<QuestionFormValues, unknown, QuestionFormValues>;
  paths: Map<string, string>;
  onAdd: () => void;
  error: string | undefined;
}) {
  const { watch, setValue } = form;
  const ids = watch('topicIds');
  const primary = watch('primaryTopicId');
  const labelId = useId();

  function remove(id: string) {
    const rest = ids.filter((t) => t !== id);
    setValue('topicIds', rest);
    if (primary === id) {
      setValue('primaryTopicId', rest[0] ?? '');
    }
  }

  return (
    <div
      role="group"
      aria-labelledby={labelId}
      aria-describedby={error ? `${labelId}-error` : undefined}
      className="flex flex-col gap-space-2"
    >
      <span id={labelId} className="font-bold">
        {e.topics}
      </span>
      <ul className="flex flex-wrap gap-space-2">
        {ids.map((id) => {
          const path = paths.get(id) ?? '';
          const name = path.split(' › ').at(-1) ?? path;
          const isPrimary = id === primary;
          return (
            <li
              key={id}
              className={`flex min-h-11 items-center gap-space-1 rounded-pill pl-space-3 type-body-sm ${
                isPrimary ? 'border-bold border-outline font-semibold' : 'bg-surface-sunken'
              }`}
            >
              {isPrimary ? `${path} · ${e.primary}` : path}
              {!isPrimary && (
                <button
                  type="button"
                  aria-label={e.makePrimary(name)}
                  onClick={() => setValue('primaryTopicId', id)}
                  className="flex size-11 items-center justify-center"
                >
                  <Star aria-hidden="true" size={16} strokeWidth={2} />
                </button>
              )}
              <button
                type="button"
                aria-label={e.removeTopic(name)}
                onClick={() => remove(id)}
                className="flex size-11 items-center justify-center"
              >
                <X aria-hidden="true" size={16} strokeWidth={2} />
              </button>
            </li>
          );
        })}
        <li>
          <button
            type="button"
            onClick={onAdd}
            disabled={ids.length >= MAX_TOPICS}
            className="flex min-h-11 items-center gap-space-1 rounded-pill border-2 border-dashed border-ink-muted px-space-3 type-body-sm disabled:opacity-30"
          >
            <Plus aria-hidden="true" size={16} strokeWidth={2} />
            {e.addTopic}
          </button>
        </li>
      </ul>
      {error && (
        <span id={`${labelId}-error`} className="type-body-sm text-missed">
          {error}
        </span>
      )}
    </div>
  );
}

function TopicPicker({
  topics,
  paths,
  form,
  onClose,
}: {
  topics: Topic[];
  paths: Map<string, string>;
  form: UseFormReturn<QuestionFormValues, unknown, QuestionFormValues>;
  onClose: () => void;
}) {
  const { watch, setValue } = form;
  const ids = watch('topicIds');
  const primary = watch('primaryTopicId');

  function toggle(id: string) {
    if (ids.includes(id)) {
      const rest = ids.filter((t) => t !== id);
      setValue('topicIds', rest);
      if (primary === id) {
        setValue('primaryTopicId', rest[0] ?? '');
      }
    } else if (ids.length < MAX_TOPICS) {
      setValue('topicIds', [...ids, id]);
      if (primary === '') {
        setValue('primaryTopicId', id);
      }
    }
  }

  return (
    <Sheet title={e.chooseTopics} onClose={onClose}>
      {topics.length === 0 ? (
        <p className="flex flex-col gap-space-2">
          <span className="text-ink-muted">{e.noTopics}</span>
          <Link to="/topics" className="font-semibold underline">
            {e.openTopics}
          </Link>
        </p>
      ) : (
        <>
          <p className="type-body-sm text-ink-muted">{e.topicLimit}</p>
          <ul className="flex max-h-[50dvh] flex-col overflow-y-auto">
            {topics.map((topic) => {
              const selected = ids.includes(topic.id);
              return (
                <li key={topic.id}>
                  <button
                    type="button"
                    aria-pressed={selected}
                    disabled={!selected && ids.length >= MAX_TOPICS}
                    onClick={() => toggle(topic.id)}
                    className="flex min-h-11 w-full items-center border-b border-line px-space-2 text-left aria-pressed:font-semibold disabled:opacity-45"
                  >
                    {paths.get(topic.id)}
                  </button>
                </li>
              );
            })}
          </ul>
        </>
      )}
      <button
        type="button"
        onClick={onClose}
        className="h-12 rounded-md border-bold border-outline bg-accent font-bold text-on-accent"
      >
        {e.done}
      </button>
    </Sheet>
  );
}
