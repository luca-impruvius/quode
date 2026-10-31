import { lazy, Suspense } from 'react';

const MarkdownView = lazy(() => import('./MarkdownView'));

/** Renders Markdown safely; until the renderer has loaded, the plain text shows. */
export function Markdown({ text }: { text: string }) {
  return (
    <Suspense fallback={<p className="whitespace-pre-wrap">{text}</p>}>
      <MarkdownView text={text} />
    </Suspense>
  );
}
