// Compile-time checks for the typed client (M1-08), run by `npm run typecheck`. Every line marked
// as an expected error below must fail to compile; if one compiles, the typecheck fails. Never run.
import { apiGet, apiSend } from './client';
import type { QuestionView } from './questions';

export async function typedClientChecks(): Promise<void> {
  // A known path returns its schema type.
  const question: QuestionView = await apiGet('/v1/questions/{id}', { path: { id: 'q1' } });
  void question;

  // @ts-expect-error: unknown path
  await apiGet('/v1/nope');

  // @ts-expect-error: missing path parameter
  await apiGet('/v1/questions/{id}');

  // @ts-expect-error: the topics list has no POST body shape like this
  await apiSend('post', '/v1/topics', { body: { title: 'Java' } });

  // @ts-expect-error: GET is not a write
  await apiSend('get', '/v1/topics');

  // @ts-expect-error: wrong query value type
  await apiGet('/v1/questions', { query: { page: 'first' } });

  // @ts-expect-error: a field the response doesn't have
  void (await apiGet('/v1/topics'))[0]?.title;
}
