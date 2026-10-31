import { errorMessage } from '../lib/errorMessage';

/** An error box (role="alert"): the server's message for a 4xx, else the short reference. */
export function ErrorNotice({ error }: { error: unknown }) {
  return (
    <p role="alert" className="rounded-md bg-missed-bg px-space-4 py-space-3 text-ink">
      {errorMessage(error)}
    </p>
  );
}
