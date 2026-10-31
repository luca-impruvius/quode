import { ApiError } from '../api/client';
import { messages } from '../messages';

/**
 * An error box. A 4xx answer shows the server's message (a fixed, user-facing sentence by convention:
 * "Questions use this topic."); anything else shows the short reference, so a report can be matched
 * to the server logs.
 */
export function ErrorNotice({ error }: { error: unknown }) {
  let text: string = messages.errors.generic;
  if (error instanceof ApiError) {
    if (error.status >= 400 && error.status < 500 && error.message !== '') {
      text = error.message;
    } else if (error.reference !== undefined) {
      text = messages.errors.reference(error.reference);
    }
  }
  return (
    <p role="alert" className="rounded-md bg-missed-bg px-space-4 py-space-3 text-ink">
      {text}
    </p>
  );
}
