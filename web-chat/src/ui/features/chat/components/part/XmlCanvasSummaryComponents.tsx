export type StructuredIconName =
  | 'arrow'
  | 'file'
  | 'search'
  | 'terminal'
  | 'code'
  | 'web'
  | 'difference'
  | 'subdirectory-arrow-right'
  | 'check'
  | 'close'
  | 'copy'
  | 'visibility'
  | 'code-toggle';

const ICON_PATHS: Record<StructuredIconName, string> = {
  arrow:
    'M12 4l-1.41 1.41L15.17 10H4v2h11.17l-4.58 4.59L12 18l7-7z',
  file:
    'M14 2H6c-1.1 0-2 .9-2 2l-.01 16c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm1 7V3.5L18.5 9H15zM8 13h8v2H8v-2zm0 4h8v2H8v-2zm0-8h5v2H8V9z',
  search:
    'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0016 9.5 6.5 6.5 0 109.5 16a6.47 6.47 0 004.23-1.57l.27.28v.79L20 21.5 21.5 20l-6-6zm-6 0A4.5 4.5 0 119.5 5a4.5 4.5 0 010 9z',
  terminal:
    'M4 5h16a1 1 0 011 1v12a1 1 0 01-1 1H4a1 1 0 01-1-1V6a1 1 0 011-1zm2 3v2.2l2.6 2.6L6 15.4 7.4 16.8l4-4-4-4L6 8zm6 7h6v-2h-6v2z',
  code:
    'M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0L19.2 12l-4.6-4.6L16 6l6 6-6 6-1.4-1.4z',
  web:
    'M12 2a10 10 0 100 20 10 10 0 000-20zm6.93 9h-3.19a15.48 15.48 0 00-1.15-5.01A8.03 8.03 0 0118.93 11zM12 4c.86 1.24 1.54 3.07 1.78 5H10.22C10.46 7.07 11.14 5.24 12 4zM4.07 13h3.19c.22 1.8.79 3.5 1.65 5.01A8.03 8.03 0 014.07 13zm3.19-2H4.07a8.03 8.03 0 014.8-5.01A15.4 15.4 0 007.26 11zM12 20c-.86-1.24-1.54-3.07-1.78-5h3.56c-.24 1.93-.92 3.76-1.78 5zm2.03-7H9.97a13.8 13.8 0 010-2h4.06c.08.66.12 1.33.12 2s-.04 1.34-.12 2zm.96 5.01A15.47 15.47 0 0016.74 13h3.19a8.03 8.03 0 01-4.94 5.01z',
  difference:
    'M3 5h8v4H3V5zm10 0h8v4h-8V5zM3 11h8v8H3v-8zm10 4h8v4h-8v-4zm0-4h8v2h-8v-2z',
  'subdirectory-arrow-right':
    'M19 15l-6 6-1.41-1.41L15.17 16H4V4h2v10h9.17l-3.58-3.59L13 9l6 6z',
  check:
    'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  close:
    'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  copy:
    'M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z',
  visibility:
    'M12 6.5c-5 0-9.27 3.11-11 7.5 1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zm0 13c-3.04 0-5.5-2.46-5.5-5.5S8.96 8.5 12 8.5s5.5 2.46 5.5 5.5-2.46 5.5-5.5 5.5zm0-9c-1.93 0-3.5 1.57-3.5 3.5s1.57 3.5 3.5 3.5 3.5-1.57 3.5-3.5-1.57-3.5-3.5-3.5z',
  'code-toggle':
    'M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0L19.2 12l-4.6-4.6L16 6l6 6-6 6-1.4-1.4zM11 19h2V5h-2v14z'
};

export function resolveToolIconName(toolName: string): StructuredIconName {
  const normalizedName = toolName.toLowerCase();

  if (
    normalizedName.includes('file') ||
    normalizedName.includes('read') ||
    normalizedName.includes('write')
  ) {
    return 'file';
  }
  if (
    normalizedName.includes('search') ||
    normalizedName.includes('find') ||
    normalizedName.includes('query')
  ) {
    return 'search';
  }
  if (
    normalizedName.includes('terminal') ||
    normalizedName.includes('exec') ||
    normalizedName.includes('command') ||
    normalizedName.includes('shell')
  ) {
    return 'terminal';
  }
  if (normalizedName.includes('code') || normalizedName.includes('ffmpeg')) {
    return 'code';
  }
  if (
    normalizedName.includes('http') ||
    normalizedName.includes('web') ||
    normalizedName.includes('visit')
  ) {
    return 'web';
  }
  return 'arrow';
}

export function StructuredIcon({
  name,
  size = 16,
  className
}: {
  name: StructuredIconName;
  size?: number;
  className?: string;
}) {
  return (
    <svg
      aria-hidden="true"
      className={className}
      fill="currentColor"
      height={size}
      viewBox="0 0 24 24"
      width={size}
    >
      <path d={ICON_PATHS[name]} />
    </svg>
  );
}

export function ToolSummaryRow({
  toolName,
  summary,
  semanticDescription,
  leadingIcon,
  onClick
}: {
  toolName: string;
  summary: string;
  semanticDescription: string;
  leadingIcon: StructuredIconName;
  onClick?: (() => void) | null;
}) {
  const content = (
    <span className="xml-canvas-tool-summary-grid">
      <StructuredIcon
        className="xml-canvas-tool-summary-leading"
        name={leadingIcon}
        size={16}
      />
      <span className="xml-canvas-tool-summary-title">{toolName}</span>
      <span className="xml-canvas-tool-summary-text">{summary}</span>
    </span>
  );

  if (onClick) {
    return (
      <button
        aria-label={semanticDescription}
        className="xml-canvas-tool-summary-button"
        onClick={onClick}
        type="button"
      >
        {content}
      </button>
    );
  }

  return (
    <div
      aria-label={semanticDescription}
      className="xml-canvas-tool-summary-button is-static"
    >
      {content}
    </div>
  );
}

export function ToolResultRow({
  summary,
  isSuccess,
  semanticDescription,
  onClick,
  onCopyClick
}: {
  summary: string;
  isSuccess: boolean;
  semanticDescription: string;
  onClick?: (() => void) | null;
  onCopyClick?: (() => void) | null;
}) {
  const rowContent = (
    <span className="xml-canvas-tool-result-grid">
      <StructuredIcon
        className={`xml-canvas-tool-result-arrow ${isSuccess ? 'is-success' : 'is-error'}`}
        name="subdirectory-arrow-right"
        size={18}
      />
      <StructuredIcon
        className={`xml-canvas-tool-result-status ${isSuccess ? 'is-success' : 'is-error'}`}
        name={isSuccess ? 'check' : 'close'}
        size={14}
      />
      <span className={`xml-canvas-tool-result-text ${isSuccess ? 'is-success' : 'is-error'}`}>
        {summary}
      </span>
      {onCopyClick ? (
        <span className="xml-canvas-tool-result-copy-slot" />
      ) : null}
    </span>
  );

  return (
    <div className="xml-canvas-tool-result-shell">
      {onClick ? (
        <button
          aria-label={semanticDescription}
          className="xml-canvas-tool-result-button"
          onClick={onClick}
          type="button"
        >
          {rowContent}
        </button>
      ) : (
        <div
          aria-label={semanticDescription}
          className="xml-canvas-tool-result-button is-static"
        >
          {rowContent}
        </div>
      )}

      {onCopyClick ? (
        <button
          aria-label={`${semanticDescription}, copy`}
          className="xml-canvas-tool-result-copy"
          onClick={onCopyClick}
          type="button"
        >
          <StructuredIcon name="copy" size={14} />
        </button>
      ) : null}
    </div>
  );
}

export function FileDiffRow({
  fileName,
  summary,
  semanticDescription,
  onClick
}: {
  fileName: string;
  summary: string;
  semanticDescription: string;
  onClick?: (() => void) | null;
}) {
  const content = (
    <span className="xml-canvas-file-diff-grid">
      <StructuredIcon
        className="xml-canvas-file-diff-arrow"
        name="subdirectory-arrow-right"
        size={18}
      />
      <StructuredIcon className="xml-canvas-file-diff-icon" name="difference" size={16} />
      <span className="xml-canvas-file-diff-copy">
        <span className="xml-canvas-file-diff-title">{fileName}</span>
        <span className="xml-canvas-file-diff-summary">{summary}</span>
      </span>
    </span>
  );

  if (onClick) {
    return (
      <button
        aria-label={semanticDescription}
        className="xml-canvas-file-diff-button"
        onClick={onClick}
        type="button"
      >
        {content}
      </button>
    );
  }

  return (
    <div aria-label={semanticDescription} className="xml-canvas-file-diff-button is-static">
      {content}
    </div>
  );
}
