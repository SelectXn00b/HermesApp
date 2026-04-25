import { useMemo, useState, type ReactNode } from 'react';
import { AnimatedExpandBody, StructuredExpandRow } from './StructuredExpand';
import type { WebMessageContentBlock } from '../../util/chatTypes';

function extractSummary(detailsInner: string) {
  const match = detailsInner.match(/<summary>([\s\S]*?)<\/summary>/i);
  return match?.[1]?.trim() ?? '';
}

function removeSummary(detailsInner: string) {
  return detailsInner.replace(/<summary>[\s\S]*?<\/summary>/i, '').trim();
}

function hasOpenAttribute(xml: string) {
  return /<\s*(details|detail)\b[^>]*\bopen\b/i.test(xml);
}

export function DetailsTagRenderer({
  block,
  renderMarkdown
}: {
  block: WebMessageContentBlock;
  renderMarkdown: (content: string) => ReactNode;
}) {
  const innerContent = block.content ?? '';
  const summary = useMemo(() => extractSummary(innerContent), [innerContent]);
  const body = useMemo(() => removeSummary(innerContent), [innerContent]);
  const defaultExpanded = useMemo(() => hasOpenAttribute(block.xml ?? ''), [block.xml]);
  const [expanded, setExpanded] = useState(() => defaultExpanded);
  const title = summary || 'Details';

  return (
    <section className="structured-details-block">
      <StructuredExpandRow
        className="is-details-tone"
        expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
        title={title}
      />
      {body ? (
        <AnimatedExpandBody className="structured-details-body" durationMs={200} visible={expanded}>
          {renderMarkdown(body)}
        </AnimatedExpandBody>
      ) : null}
    </section>
  );
}
