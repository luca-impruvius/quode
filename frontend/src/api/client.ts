// Thin fetch wrapper for the backend. Same origin: in dev Vite forwards /api, in production Caddy.

/** An error answer from the backend: Problem Details (RFC 9457) with the request's trace ID. */
export class ApiError extends Error {
  readonly status: number;
  readonly traceId: string | undefined;

  constructor(status: number, detail: string, traceId: string | undefined) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.traceId = traceId;
  }

  /** The short reference the UI shows; the full trace ID is in the logs. */
  get reference(): string | undefined {
    return this.traceId?.slice(0, 6);
  }
}

interface ProblemDetail {
  detail?: string;
  title?: string;
  traceId?: string;
}

export async function apiGet<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    headers: { Accept: 'application/json', ...init?.headers },
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as T;
}

/**
 * A state-changing request (POST, PUT, PATCH, DELETE). Sends the CSRF token the backend set in the
 * XSRF-TOKEN cookie back in the X-XSRF-TOKEN header, as Spring Security expects. Returns the JSON
 * body, or undefined for an empty answer (204).
 */
export async function apiSend<T = undefined>(
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = readCookie('XSRF-TOKEN');
  if (token !== undefined) {
    headers['X-XSRF-TOKEN'] = token;
  }
  const init: RequestInit = { method, headers };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  const response = await fetch(`/api${path}`, init);
  if (!response.ok) {
    throw await toApiError(response);
  }
  const text = await response.text();
  return (text === '' ? undefined : JSON.parse(text)) as T;
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const match = document.cookie.split('; ').find((cookie) => cookie.startsWith(prefix));
  return match === undefined ? undefined : decodeURIComponent(match.slice(prefix.length));
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {};
  try {
    problem = ((await response.json()) as ProblemDetail | null) ?? {};
  } catch {
    // Not JSON (for example a proxy error page): keep the status only.
  }
  const traceId = problem.traceId ?? response.headers.get('X-Trace-Id') ?? undefined;
  return new ApiError(
    response.status,
    problem.detail ?? problem.title ?? response.statusText,
    traceId,
  );
}
