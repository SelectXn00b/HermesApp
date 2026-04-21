import { useMemo, useState } from 'react';
import { FileDiffDisplay, type FileDiff } from './FileDiffDisplay';
import { ToolResultDetailDialog } from './DialogComponents';
import { ToolResultRow } from './XmlCanvasSummaryComponents';
import type { WebMessageContentBlock } from '../../util/chatTypes';

function extractTaggedContent(content: string, tagName: string) {
  const match = content.match(new RegExp(`<${tagName}[^>]*>([\\s\\S]*?)<\\/${tagName}>`, 'i'));
  return match?.[1]?.trim() ?? '';
}

function stripFileDiff(result: string) {
  return result.replace(/<file-diff[\s\S]*<\/file-diff>/gi, '').trim();
}

function decodeXmlText(input: string) {
  return input
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'");
}

function extractFileDiff(result: string): FileDiff | null {
  const fileDiffMatch = result.match(/<file-diff\b([^>]*)>([\s\S]*?)<\/file-diff>/i);
  if (!fileDiffMatch) {
    return null;
  }

  const attributes = fileDiffMatch[1] ?? '';
  const body = fileDiffMatch[2] ?? '';
  const path = attributes.match(/\bpath="([^"]*)"/i)?.[1] ?? '';
  const details = attributes.match(/\bdetails="([^"]*)"/i)?.[1] ?? '';
  const cdataContent = body.match(/<!\[CDATA\[([\s\S]*?)\]\]>/i)?.[1];
  const diffContent = decodeXmlText((cdataContent ?? body).trim());

  return {
    path,
    details,
    diffContent
  };
}

function copyText(text: string) {
  void navigator.clipboard.writeText(text).catch(() => {});
}

function normalizeToolResult(block: WebMessageContentBlock) {
  const toolName = block.attrs?.name?.trim() || '未知工具';
  const status = (block.attrs?.status?.trim()?.toLowerCase() || 'success');
  const outerContent = block.content ?? '';
  const rawResultContent = extractTaggedContent(outerContent, 'content') || outerContent.trim();
  const isSuccess = status === 'success';
  const fileDiff =
    toolName === 'apply_file' && isSuccess && rawResultContent.includes('<file-diff')
      ? extractFileDiff(rawResultContent)
      : null;
  const resultContent = isSuccess
    ? stripFileDiff(rawResultContent)
    : extractTaggedContent(rawResultContent, 'error') || rawResultContent;

  return {
    toolName,
    isSuccess,
    resultContent,
    fileDiff
  };
}

function buildSummaryText(result: string, isSuccess: boolean) {
  if (result.trim()) {
    return result.slice(0, 200);
  }
  return isSuccess ? '执行成功' : '执行失败';
}

function buildSemanticDescription(toolName: string, summaryText: string, result: string, isSuccess: boolean) {
  const normalizedPreview = result
    .replace(/\n/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  const preview =
    normalizedPreview.length <= 20 ? normalizedPreview : `${normalizedPreview.slice(0, 20)}...`;
  const statusLabel = isSuccess ? '成功' : '失败';
  return preview
    ? `工具执行结果: ${toolName}，${statusLabel}，${preview}`
    : `工具执行结果: ${toolName}，${statusLabel}，${summaryText}`;
}

export function ToolResultDisplay({ block }: { block: WebMessageContentBlock }) {
  const { toolName, isSuccess, resultContent, fileDiff } = useMemo(() => {
    return normalizeToolResult(block);
  }, [block]);
  const [detailOpen, setDetailOpen] = useState(false);
  const hasContent = resultContent.trim().length > 0;
  const summaryText = buildSummaryText(resultContent, isSuccess);
  const semanticDescription = buildSemanticDescription(
    toolName,
    summaryText,
    resultContent,
    isSuccess
  );

  return (
    <>
      {fileDiff ? (
        <FileDiffDisplay diff={fileDiff} />
      ) : (
        <ToolResultRow
          isSuccess={isSuccess}
          onClick={hasContent ? () => setDetailOpen(true) : null}
          onCopyClick={hasContent ? () => copyText(resultContent) : null}
          semanticDescription={semanticDescription}
          summary={summaryText}
        />
      )}

      {detailOpen && !fileDiff ? (
        <ToolResultDetailDialog
          isSuccess={isSuccess}
          onDismiss={() => setDetailOpen(false)}
          result={resultContent}
          toolName={toolName}
        />
      ) : null}
    </>
  );
}
