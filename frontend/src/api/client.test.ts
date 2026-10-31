import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiGet, apiSend } from './client';

describe('apiSend', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('sends the CSRF token from the cookie in the X-XSRF-TOKEN header', async () => {
    document.cookie = 'XSRF-TOKEN=abc%2B123';
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(null, { status: 204 }));

    await expect(apiSend('POST', '/logout')).resolves.toBeUndefined();

    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(url).toBe('/api/logout');
    expect(init?.method).toBe('POST');
    expect((init?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('abc+123');
  });

  it('sends a JSON body and returns the JSON answer', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(Response.json({ id: 1 }, { status: 201 }));

    await expect(apiSend('POST', '/v1/things', { name: 'x' })).resolves.toEqual({ id: 1 });

    const init = fetchSpy.mock.calls[0]?.[1];
    expect(init?.body).toBe('{"name":"x"}');
    expect((init?.headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('turns an error answer into an ApiError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      Response.json({ status: 403, detail: 'Forbidden' }, { status: 403 }),
    );

    await expect(apiSend('DELETE', '/v1/things/1')).rejects.toMatchObject({ status: 403 });
  });
});

describe('apiGet', () => {
  it('returns the JSON body of a successful answer', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({ status: 'UP' }));

    await expect(apiGet('/health')).resolves.toEqual({ status: 'UP' });
  });

  it('turns Problem Details into an ApiError with the trace reference', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      Response.json(
        { status: 404, detail: 'Question not found', traceId: '7f3a2c9be1d04a6f8c2e5d7a1b3c4d5e' },
        { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    );

    const error = await apiGet('/v1/questions/1').catch((e: unknown) => e);

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

    const error = (await apiGet('/health').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.message).toBe('Bad Gateway');
    expect(error.reference).toBe('abcdef');
  });
});
