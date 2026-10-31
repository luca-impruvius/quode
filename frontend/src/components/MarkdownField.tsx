import { useId, useState, type ComponentPropsWithoutRef } from 'react';
import { messages } from '../messages';
import { Markdown } from './Markdown';

const e = messages.editor;

/**
 * A Markdown textarea with the wireframe's Write/Preview switch. `textarea` props come from React
 * Hook Form's register(); `value` is what Preview shows.
 */
export function MarkdownField({
  label,
  value,
  error,
  hint,
  rows = 4,
  textarea,
}: {
  label: string;
  value: string;
  error?: string | undefined;
  hint?: string;
  rows?: number;
  textarea: ComponentPropsWithoutRef<'textarea'>;
}) {
  const id = useId();
  const [preview, setPreview] = useState(false);
  // The error, when there is one, is what a screen reader should hear; otherwise the hint.
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : '';

  return (
    <div className="flex flex-col gap-space-1">
      <div className="flex items-center justify-between">
        <label htmlFor={id} className="font-bold">
          {label}
        </label>
        <div
          role="radiogroup"
          aria-label={e.viewOf(label)}
          className="flex rounded-sm border-2 border-outline type-body-sm"
        >
          {[false, true].map((isPreview) => (
            <button
              key={String(isPreview)}
              type="button"
              role="radio"
              aria-checked={preview === isPreview}
              onClick={() => setPreview(isPreview)}
              className="min-h-11 px-space-3 first:rounded-l-[6px] last:rounded-r-[6px] aria-checked:bg-ink aria-checked:text-surface"
            >
              {isPreview ? e.preview : e.write}
            </button>
          ))}
        </div>
      </div>
      {preview ? (
        <div className="min-h-24 rounded-md border-2 border-line p-space-3">
          {value.trim() === '' ? (
            <p className="text-ink-muted">{e.previewEmpty}</p>
          ) : (
            <Markdown text={value} />
          )}
        </div>
      ) : (
        <textarea
          id={id}
          rows={rows}
          aria-invalid={error !== undefined}
          aria-describedby={describedBy === '' ? undefined : describedBy}
          className="rounded-md border-2 border-outline bg-surface p-space-3"
          {...textarea}
        />
      )}
      {error !== undefined && (
        <span id={`${id}-error`} className="type-body-sm text-missed">
          {error}
        </span>
      )}
      {hint !== undefined && !preview && (
        <span id={`${id}-hint`} className="type-body-sm text-ink-muted">
          {hint}
        </span>
      )}
    </div>
  );
}
