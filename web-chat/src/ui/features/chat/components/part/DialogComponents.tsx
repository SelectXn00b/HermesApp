import { useEffect, useMemo, useRef, useState } from 'react';
import { ParamVisualizer } from './ParamVisualizer';
import {
  StructuredIcon,
  type StructuredIconName
} from './XmlCanvasSummaryComponents';

function copyText(text: string) {
  void navigator.clipboard.writeText(text).catch(() => {});
}

function normalizeIndent(line: string) {
  if (!line) {
    return line;
  }

  let index = 0;
  let levels = 0;
  let spaces = 0;

  while (index < line.length) {
    const char = line[index];
    if (char === '\t') {
      if (spaces > 0) {
        levels += 1;
        spaces = 0;
      }
      levels += 1;
      index += 1;
      continue;
    }
    if (char === ' ') {
      spaces += 1;
      if (spaces === 4) {
        levels += 1;
        spaces = 0;
      }
      index += 1;
      continue;
    }
    break;
  }

  if (spaces > 0) {
    levels += 1;
  }

  if (levels === 0) {
    return line;
  }

  return `${' '.repeat(levels)}${line.slice(index)}`;
}

function CodeContentWithLineNumbers({
  content
}: {
  content: string;
}) {
  const lines = useMemo(() => content.split(/\r?\n/), [content]);

  return (
    <div className="structured-code-lines">
      {lines.map((line, index) => (
        <div className="structured-code-row" key={`${index}-${line}`}>
          <span className="structured-code-gutter">{index + 1}</span>
          <pre className="structured-code-text">{normalizeIndent(line.trimEnd())}</pre>
        </div>
      ))}
    </div>
  );
}

function DiffContentView({
  content
}: {
  content: string;
}) {
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const lines = useMemo(() => content.split(/\r?\n/), [content]);

  useEffect(() => {
    const node = scrollRef.current;
    if (!node) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [lines]);

  return (
    <div className="structured-diff-lines" ref={scrollRef}>
      {lines.map((line, index) => {
        const trimmedLine = line.trimEnd();
        const variant =
          trimmedLine.startsWith('+')
            ? 'is-addition'
            : trimmedLine.startsWith('-')
              ? 'is-deletion'
              : trimmedLine.startsWith('@@')
                ? 'is-hunk'
                : 'is-default';

        return (
          <div className={`structured-diff-row ${variant}`} key={`${index}-${trimmedLine}`}>
            <pre className="structured-diff-text">{trimmedLine}</pre>
          </div>
        );
      })}
    </div>
  );
}

export function ContentDetailDialog({
  title,
  content,
  iconName,
  onDismiss,
  isDiffContent = false
}: {
  title: string;
  content: string;
  iconName: StructuredIconName;
  onDismiss: () => void;
  isDiffContent?: boolean;
}) {
  const [isRawView, setRawView] = useState(false);
  const isXmlContent = content.trim().startsWith('<');

  return (
    <div className="structured-modal-backdrop" onClick={onDismiss} role="presentation">
      <section
        className="structured-modal-card"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <div className="structured-modal-main">
          <header className="structured-modal-header">
            <div className="structured-modal-title-row">
              <StructuredIcon
                className="structured-modal-title-icon"
                name={iconName}
                size={20}
              />
              <strong>{title}</strong>
            </div>
            <div className="structured-modal-actions">
              {isXmlContent && !isDiffContent ? (
                <button
                  aria-label={isRawView ? '切换到可视视图' : '切换到原始视图'}
                  className="structured-modal-icon-button"
                  onClick={() => setRawView((value) => !value)}
                  type="button"
                >
                  <StructuredIcon name={isRawView ? 'visibility' : 'code-toggle'} size={20} />
                </button>
              ) : null}
            </div>
          </header>

          <div className="structured-modal-divider" />

          <div className={`structured-modal-surface ${isDiffContent ? 'is-diff' : ''}`}>
            {isXmlContent && !isRawView && !isDiffContent ? (
              <ParamVisualizer xmlContent={content} />
            ) : isDiffContent ? (
              <DiffContentView content={content} />
            ) : (
              <CodeContentWithLineNumbers content={content} />
            )}
          </div>

          <footer className="structured-modal-footer">
            <button className="structured-modal-primary" onClick={onDismiss} type="button">
              关闭
            </button>
          </footer>
        </div>
      </section>
    </div>
  );
}

export function ToolResultDetailDialog({
  toolName,
  result,
  isSuccess,
  onDismiss
}: {
  toolName: string;
  result: string;
  isSuccess: boolean;
  onDismiss: () => void;
}) {
  return (
    <div className="structured-modal-backdrop" onClick={onDismiss} role="presentation">
      <section
        className="structured-modal-card"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <div className="structured-modal-main">
          <header className="structured-modal-header">
            <div className="structured-modal-title-row">
              <StructuredIcon
                className={`structured-modal-title-icon ${isSuccess ? 'is-success' : 'is-error'}`}
                name={isSuccess ? 'check' : 'close'}
                size={20}
              />
              <div className="structured-modal-title-stack">
                <strong>{`${toolName} ${isSuccess ? '执行成功' : '执行失败'}`}</strong>
              </div>
            </div>
            <div className="structured-modal-actions">
              <button
                aria-label="复制结果"
                className="structured-modal-icon-button"
                onClick={() => copyText(result)}
                type="button"
              >
                <StructuredIcon name="copy" size={20} />
              </button>
            </div>
          </header>

          <div className="structured-modal-divider" />

          <div className={`structured-modal-surface is-result ${isSuccess ? 'is-success' : 'is-error'}`}>
            <pre className="structured-modal-pre">{result}</pre>
          </div>

          <footer className="structured-modal-footer">
            <button className="structured-modal-primary" onClick={onDismiss} type="button">
              关闭
            </button>
          </footer>
        </div>
      </section>
    </div>
  );
}
