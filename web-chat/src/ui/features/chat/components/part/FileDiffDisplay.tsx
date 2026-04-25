import { useMemo, useState } from 'react';
import { ContentDetailDialog } from './DialogComponents';
import { FileDiffRow } from './XmlCanvasSummaryComponents';

export interface FileDiff {
  path: string;
  diffContent: string;
  details: string;
}

function extractFileName(path: string) {
  const normalized = path.replace(/\\/g, '/');
  const segments = normalized.split('/');
  return segments[segments.length - 1] || path;
}

function buildDiffSummary(diffContent: string) {
  const diffLines = diffContent.trimEnd().split(/\r?\n/);
  const additions = diffLines.filter((line) => line.startsWith('+')).length;
  const deletions = diffLines.filter((line) => line.startsWith('-')).length;

  if (additions > 0 && deletions > 0) {
    return `${additions} insertions(+), ${deletions} deletions(-)`;
  }
  if (additions > 0) {
    return `${additions} insertions(+)`;
  }
  if (deletions > 0) {
    return `${deletions} deletions(-)`;
  }
  return 'No changes detected';
}

export function FileDiffDisplay({ diff }: { diff: FileDiff }) {
  const [detailOpen, setDetailOpen] = useState(false);
  const fileName = useMemo(() => extractFileName(diff.path), [diff.path]);
  const summary = useMemo(() => buildDiffSummary(diff.diffContent), [diff.diffContent]);
  const semanticDescription = `File changes: ${fileName}, ${summary}`;

  return (
    <>
      <FileDiffRow
        fileName={fileName}
        onClick={() => setDetailOpen(true)}
        semanticDescription={semanticDescription}
        summary={summary}
      />

      {detailOpen ? (
        <ContentDetailDialog
          content={diff.diffContent}
          iconName="difference"
          isDiffContent
          onDismiss={() => setDetailOpen(false)}
          title={`File Changes: ${fileName}`}
        />
      ) : null}
    </>
  );
}
