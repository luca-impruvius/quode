import { ApiError } from '../api/client';
import { messages } from '../messages';

/** An error box with the short reference, so a report can be matched to the server logs. */
export function ErrorNotice({ error }: { error: unknown }) {
  const reference = error instanceof ApiError ? error.reference : undefined;
  return (
    <p role="alert" className="rounded-md bg-missed-bg px-space-4 py-space-3 text-ink">
      {reference === undefined ? messages.errors.generic : messages.errors.reference(reference)}
    </p>
  );
}
