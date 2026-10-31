import ReactMarkdown from 'react-markdown';
import rehypeHighlight from 'rehype-highlight';
import remarkGfm from 'remark-gfm';
import './markdown.css';

/**
 * The Markdown renderer itself, loaded lazily through <Markdown> (react-markdown, GFM and code
 * highlighting are a sizable chunk). Raw HTML in the text is dropped, never rendered (skipHtml),
 * and react-markdown's default URL check refuses javascript: and other unsafe links.
 */
export default function MarkdownView({ text }: { text: string }) {
  return (
    <div className="qd-markdown">
      <ReactMarkdown
        skipHtml
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeHighlight]}
        components={{
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noopener noreferrer">
              {children}
            </a>
          ),
        }}
      >
        {text}
      </ReactMarkdown>
    </div>
  );
}
