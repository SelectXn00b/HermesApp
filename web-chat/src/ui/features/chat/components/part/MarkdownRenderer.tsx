import { useMemo, useState, type HTMLAttributes, type ImgHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react';
import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';

function copyText(text: string) {
  void navigator.clipboard.writeText(text).catch(() => {});
}

function normalizeCodeBlockText(children: ReactNode) {
  const raw = String(children ?? '');
  return raw.replace(/\n$/, '');
}

function MarkdownCodeBlock({
  code,
  language
}: {
  code: string;
  language: string;
}) {
  const [autoWrapEnabled, setAutoWrapEnabled] = useState(true);
  const lines = useMemo(() => code.split(/\r?\n/), [code]);
  const languageLabel = language || 'text';

  return (
    <section className="markdown-code-block">
      <header className="markdown-code-toolbar">
        <span className="markdown-code-language">{languageLabel}</span>
        <div className="markdown-code-actions">
          <button
            className={`markdown-code-action ${autoWrapEnabled ? 'is-active' : ''}`}
            onClick={() => setAutoWrapEnabled((value) => !value)}
            type="button"
          >
            自动换行
          </button>
          <button
            className="markdown-code-action"
            onClick={() => copyText(code)}
            type="button"
          >
            复制
          </button>
        </div>
      </header>
      <div className={`markdown-code-body ${autoWrapEnabled ? 'is-wrap' : 'is-scroll'}`}>
        <div className="markdown-code-lines">
          {lines.map((line, index) => (
            <div className="markdown-code-row" key={`${index}-${line}`}>
              <span className="markdown-code-gutter">{index + 1}</span>
              <pre className="markdown-code-text">{line}</pre>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

type MarkdownCodeProps = HTMLAttributes<HTMLElement> & {
  children?: ReactNode;
  className?: string;
  inline?: boolean;
};

function MarkdownCode({ children, className, inline }: MarkdownCodeProps) {
  const language = className?.match(/language-([\w-]+)/)?.[1] ?? '';
  const code = normalizeCodeBlockText(children);
  const isBlock = inline === false || Boolean(language) || code.includes('\n');

  if (isBlock) {
    return <MarkdownCodeBlock code={code} language={language} />;
  }

  return <code className="markdown-inline-code">{children}</code>;
}

function MarkdownTable({ children }: HTMLAttributes<HTMLTableElement>) {
  return (
    <div className="markdown-table-shell">
      <table className="markdown-table">{children}</table>
    </div>
  );
}

function MarkdownImage(props: ImgHTMLAttributes<HTMLImageElement>) {
  const { alt = '', src = '' } = props;
  if (!src) {
    return null;
  }

  return (
    <span className="markdown-image-shell">
      <img alt={alt} className="markdown-image" src={src} />
    </span>
  );
}

function MarkdownCheckbox(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className="markdown-task-checkbox" disabled readOnly type="checkbox" />;
}

const markdownComponents: Components = {
  a: ({ href, children }) => (
    <a href={href} rel="noreferrer" target="_blank">
      {children}
    </a>
  ),
  blockquote: ({ children }) => <blockquote className="markdown-blockquote">{children}</blockquote>,
  code: MarkdownCode as Components['code'],
  hr: () => <hr className="markdown-divider" />,
  img: MarkdownImage as Components['img'],
  input: MarkdownCheckbox as Components['input'],
  li: ({ children }) => <li className="markdown-list-item">{children}</li>,
  ol: ({ children }) => <ol className="markdown-list markdown-list-ordered">{children}</ol>,
  p: ({ children }) => <p className="markdown-paragraph">{children}</p>,
  pre: ({ children }) => <>{children}</>,
  table: MarkdownTable as Components['table'],
  tbody: ({ children }) => <tbody className="markdown-table-body">{children}</tbody>,
  td: ({ children }) => <td className="markdown-table-cell">{children}</td>,
  th: ({ children }) => <th className="markdown-table-head-cell">{children}</th>,
  thead: ({ children }) => <thead className="markdown-table-head">{children}</thead>,
  tr: ({ children }) => <tr className="markdown-table-row">{children}</tr>,
  ul: ({ children }) => <ul className="markdown-list markdown-list-unordered">{children}</ul>
};

export function MarkdownRenderer({
  content,
  className
}: {
  content: string;
  className?: string;
}) {
  if (!content.trim()) {
    return null;
  }

  return (
    <div className={className ?? 'markdown-block'}>
      <ReactMarkdown components={markdownComponents} remarkPlugins={[remarkGfm]}>
        {content}
      </ReactMarkdown>
    </div>
  );
}
