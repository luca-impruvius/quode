import { describe, expect, it, vi } from 'vitest';
import { ApiError, apiGet } from './client';

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
