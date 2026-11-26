import { useEffect, useId } from 'react';
import type { UseFormRegisterReturn } from 'react-hook-form';
import { useSaveSettings, useSettings, type Settings } from '../api/settings';
import { ErrorNotice } from '../components/ErrorNotice';
import { useZodForm } from '../lib/forms';
import { z } from '../lib/zod';
import { messages } from '../messages';

const t = messages.me;

// The inputs hold text; a whole number in bounds passes. The server checks the same bounds.
const wholeNumber = (min: number, max: number, message: string) =>
  z
    .string()
    .trim()
    .regex(/^\d+$/, message)
    .transform(Number)
    .pipe(z.number().min(min, message).max(max, message));

const schema = z.object({
  newPerDay: wholeNumber(0, 100, t.newPerDayRange),
  reviewsPerDay: wholeNumber(1, 500, t.reviewsPerDayRange),
});

type Fields = z.input<typeof schema>;

const asFields = (settings: Settings): Fields => ({
  newPerDay: String(settings.newPerDay),
  reviewsPerDay: String(settings.reviewsPerDay),
});

/** Me → Settings: how many new questions and reviews Today hands out per day. */
export function DailyLimits() {
  const settings = useSettings();
  const save = useSaveSettings();
  const form = useZodForm(schema, { defaultValues: { newPerDay: '', reviewsPerDay: '' } });
  const { register, handleSubmit, reset, formState } = form;

  // Fill the form once the saved limits arrive, and again after each save.
  useEffect(() => {
    if (settings.data) {
      reset(asFields(settings.data));
    }
  }, [settings.data, reset]);

  // Reset from the answer too: the same values may come back as the same cached object.
  const submit = handleSubmit((values) =>
    save.mutate(values, { onSuccess: (saved) => reset(asFields(saved)) }),
  );

  return (
    <form
      aria-label={t.dailyLimits}
      onSubmit={(event) => void submit(event)}
      noValidate
      className="flex flex-col"
    >
      {settings.isError && <ErrorNotice error={settings.error} />}
      <NumberRow
        label={t.newPerDay}
        hint={t.newPerDayHint}
        min={0}
        max={100}
        error={formState.errors.newPerDay?.message}
        field={register('newPerDay')}
      />
      <NumberRow
        label={t.reviewsPerDay}
        hint={t.reviewsPerDayHint}
        min={1}
        max={500}
        error={formState.errors.reviewsPerDay?.message}
        field={register('reviewsPerDay')}
      />
      {save.isError && <ErrorNotice error={save.error} />}
      <div className="flex min-h-13 items-center gap-space-3 border-b border-line">
        <button
          type="submit"
          disabled={save.isPending || !settings.data}
          className="h-11 rounded-md border-bold border-outline px-space-4 font-semibold disabled:opacity-45"
        >
          {save.isPending ? t.saving : t.saveLimits}
        </button>
        <p role="status" className="type-body-sm text-ink-muted">
          {save.isSuccess && !formState.isDirty ? t.saved : ''}
        </p>
      </div>
    </form>
  );
}

interface NumberRowProps {
  label: string;
  hint: string;
  min: number;
  max: number;
  error: string | undefined;
  field: UseFormRegisterReturn;
}

function NumberRow({ label, hint, min, max, error, field }: NumberRowProps) {
  const id = useId();
  return (
    <div className="flex flex-col gap-space-1 border-b border-line py-space-2">
      <div className="flex min-h-11 items-center justify-between gap-space-3">
        <div className="flex flex-col">
          <label htmlFor={id}>{label}</label>
          <span id={`${id}-hint`} className="type-body-sm text-ink-muted">
            {hint}
          </span>
        </div>
        <input
          id={id}
          type="number"
          inputMode="numeric"
          min={min}
          max={max}
          step={1}
          aria-invalid={error !== undefined}
          aria-describedby={error ? `${id}-hint ${id}-error` : `${id}-hint`}
          className="h-11 w-20 rounded-md border-2 border-line bg-surface px-space-3 text-right"
          {...field}
        />
      </div>
      {error && (
        <span id={`${id}-error`} className="type-body-sm text-missed">
          {error}
        </span>
      )}
    </div>
  );
}
