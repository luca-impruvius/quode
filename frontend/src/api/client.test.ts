import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiGet, apiSend, signOut, urlFor } from './client';

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});

describe('apiGet', () => {
  it('returns the JSON body of a successful answer', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({ name: 'Luca' }));

    await expect(apiGet('/me')).resolves.toEqual({ name: 'Luca' });
  });

  it('fills path parameters and sends no CSRF header', async () => {
    document.cookie = 'XSRF-TOKEN=token';
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({}));

    await apiGet('/v1/questions/{id}', { path: { id: 'q1' } });

    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/api/v1/questions/q1');
    expect(init?.method).toBe('GET');
    expect((init?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('turns Problem Details into an ApiError with the trace reference', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      Response.json(
        { status: 404, detail: 'Question not found', traceId: '7f3a2c9be1d04a6f8c2e5d7a1b3c4d5e' },
        { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    );

    const error = await apiGet('/v1/questions/{id}', { path: { id: '1' } }).catch(
      (e: unknown) => e,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, message: 'Question not found' });
    expect((error as ApiError).reference).toBe('7f3a2c');
  });

  it('falls back to the X-Trace-Id header when the body is not JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('<html>Bad gateway</html>', {
        status: 502,
        statusText: 'Bad Gateway',
        headers: { 'X-Trace-Id': 'abcdef0123456789abcdef0123456789' },
      }),
    );

    const error = (await apiGet('/me').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.message).toBe('Bad Gateway');
    expect(error.reference).toBe('abcdef');
  });
});

describe('apiSend', () => {
  it('sends the CSRF token from the cookie in the X-XSRF-TOKEN header', async () => {
    document.cookie = 'XSRF-TOKEN=abc%2B123';
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(null, { status: 204 }));

    await expect(apiSend('delete', '/v1/topics/{id}', { path: { id: 't1' } })).resolves.toBe(
      undefined,
    );

    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/api/v1/topics/t1');
    expect(init?.method).toBe('DELETE');
    expect((init?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('abc+123');
  });

  it('sends a JSON body and returns the JSON answer', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(Response.json({ id: 't1' }, { status: 201 }));

    await expect(apiSend('post', '/v1/topics', { body: { name: 'Java' } })).resolves.toEqual({
      id: 't1',
    });

    const init = fetchMock.mock.calls[0]?.[1];
    expect(init?.body).toBe('{"name":"Java"}');
    expect((init?.headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('turns an error answer into an ApiError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      Response.json({ status: 403, detail: 'Forbidden' }, { status: 403 }),
    );

    await expect(
      apiSend('delete', '/v1/topics/{id}', { path: { id: 't1' } }),
    ).rejects.toMatchObject({ status: 403 });
  });

  it('signs out with a CSRF-protected POST to /api/logout', async () => {
    document.cookie = 'XSRF-TOKEN=t';
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(null, { status: 204 }));

    await signOut();

    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/api/logout');
    expect(init?.method).toBe('POST');
    expect((init?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('t');
  });
});

describe('urlFor', () => {
  it('encodes path parameters', () => {
    expect(urlFor('/v1/questions/{id}', { path: { id: 'a b/c' } })).toBe('/v1/questions/a%20b%2Fc');
  });

  it('leaves empty query values out', () => {
    expect(
      urlFor('/v1/questions', { query: { status: 'active', q: '', topicId: undefined, page: 0 } }),
    ).toBe('/v1/questions?status=active&page=0');
  });

  it('refuses a missing path parameter', () => {
    expect(() => urlFor('/v1/questions/{id}', {})).toThrow(/Missing path parameter id/);
  });
});
