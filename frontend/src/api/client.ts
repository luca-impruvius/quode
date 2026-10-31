// Fetch wrapper for the backend, typed by the generated OpenAPI paths (src/api/schema.d.ts, M1-08):
// a wrong path, method, path parameter, query or body field doesn't compile, and the response type
// comes from the spec. Same origin: in dev Vite forwards /api, in production Caddy.
import type { paths } from './schema';

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

type Method = 'get' | 'post' | 'put' | 'delete';

/** The paths that support this method. */
export type PathsWith<M extends Method> = {
  [P in keyof paths]: [NonNullable<paths[P][M]>] extends [never] ? never : P;
}[keyof paths];

type Operation<P extends keyof paths, M extends Method> = NonNullable<paths[P][M]>;

type PathParams<O> = O extends { parameters: { path: infer T } } ? T : never;
type QueryParams<O> = O extends { parameters: { query?: infer T } } ? T : never;
type Body<O> = O extends { requestBody: { content: { 'application/json': infer T } } } ? T : never;

/** The JSON of the 200/201 answer, or undefined for 204. */
export type Success<O> = O extends { responses: infer R }
  ? R extends { 200: { content: { 'application/json': infer T } } }
    ? T
    : R extends { 201: { content: { 'application/json': infer T } } }
      ? T
      : undefined
  : never;

type Options<O> = ([PathParams<O>] extends [never] ? { path?: never } : { path: PathParams<O> }) &
  ([QueryParams<O>] extends [never] ? { query?: never } : { query?: QueryParams<O> }) &
  ([Body<O>] extends [never] ? { body?: never } : { body: Body<O> });

// Options are required exactly when the operation has path parameters or a body.
type Args<O> = [PathParams<O>] extends [never]
  ? [Body<O>] extends [never]
    ? [options?: Options<O>]
    : [options: Options<O>]
  : [options: Options<O>];

interface RawOptions {
  path?: Record<string, string>;
  query?: Record<string, unknown>;
  body?: unknown;
}

/** GET a path from the spec: `apiGet('/v1/questions/{id}', { path: { id } })`. */
export function apiGet<P extends PathsWith<'get'>>(
  path: P,
  ...[options]: Args<Operation<P, 'get'>>
): Promise<Success<Operation<P, 'get'>>> {
  return request('GET', path, options as RawOptions | undefined) as Promise<
    Success<Operation<P, 'get'>>
  >;
}

/**
 * A state-changing request from the spec: `apiSend('post', '/v1/questions', { body })`. Sends the
 * CSRF token the backend set in the XSRF-TOKEN cookie back in the X-XSRF-TOKEN header, as Spring
 * Security expects.
 */
export function apiSend<M extends Exclude<Method, 'get'>, P extends PathsWith<M>>(
  method: M,
  path: P,
  ...[options]: Args<Operation<P, M>>
): Promise<Success<Operation<P, M>>> {
  return request(method.toUpperCase(), path, options as RawOptions | undefined) as Promise<
    Success<Operation<P, M>>
  >;
}

/** Sign out: Spring Security's logout endpoint, which isn't part of the OpenAPI spec. */
export function signOut(): Promise<unknown> {
  return request('POST', '/logout', undefined);
}

async function request(method: string, path: string, options: RawOptions | undefined) {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method !== 'GET') {
    const token = readCookie('XSRF-TOKEN');
    if (token !== undefined) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }
  const init: RequestInit = { method, headers };
  if (options?.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(options.body);
  }
  const response = await fetch(`/api${urlFor(path, options)}`, init);
  if (!response.ok) {
    throw await toApiError(response);
  }
  const text = await response.text();
  return text === '' ? undefined : (JSON.parse(text) as unknown);
}

// "/v1/questions/{id}" with { id } → "/v1/questions/q%201"; empty query values are left out.
export function urlFor(path: string, options: RawOptions | undefined): string {
  const filled = path.replace(/\{(\w+)\}/g, (_, name: string) => {
    const value = options?.path?.[name];
    if (value === undefined) {
      throw new Error(`Missing path parameter ${name} for ${path}`);
    }
    return encodeURIComponent(value);
  });
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(options?.query ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value));
    }
  }
  const search = query.toString();
  return search === '' ? filled : `${filled}?${search}`;
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
