import { useMemo, useState } from 'react';
import { ContentDetailDialog } from './DialogComponents';
import {
  resolveToolIconName,
  ToolSummaryRow
} from './XmlCanvasSummaryComponents';
import type { WebMessageContentBlock } from '../../util/chatTypes';

const TOOL_PARAM_TOKEN_THRESHOLD = 50;

function unescapeXmlForDisplay(input: string) {
  let result = input;

  if (result.startsWith('<![CDATA[') && result.endsWith(']]>')) {
    result = result.slice(9, -3);
  } else if (result.startsWith('<![CDATA[')) {
    result = result.slice(9);
  } else if (result.endsWith(']]>')) {
    result = result.slice(0, -3);
  }

  return result
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'");
}

function normalizeEscapedTextForDisplay(input: string) {
  const unescaped = unescapeXmlForDisplay(input).replace(/\\"/g, '"');
  const trimmed = unescaped.trim();

  if (
    (trimmed.startsWith('"{') && trimmed.endsWith('}"')) ||
    (trimmed.startsWith('"[') && trimmed.endsWith(']"'))
  ) {
    return trimmed.slice(1, -1).replace(/\\"/g, '"');
  }

  return unescaped;
}

function escapeXmlAttribute(input: string) {
  return input
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function escapeXmlText(input: string) {
  return input.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function jsonValueToParamText(value: unknown) {
  if (value === null || value === undefined) {
    return 'null';
  }
  return typeof value === 'string' ? value : String(value);
}

function parseProxyJsonParamsToXml(input: string) {
  const trimmed = input.trim();
  if (!trimmed) {
    return '';
  }

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (Array.isArray(parsed)) {
      return parsed
        .map((value, index) => {
          return `<param name="${index}">${escapeXmlText(jsonValueToParamText(value))}</param>`;
        })
        .join('\n');
    }
    if (parsed && typeof parsed === 'object') {
      return Object.entries(parsed as Record<string, unknown>)
        .map(([key, value]) => {
          return `<param name="${escapeXmlAttribute(key)}">${escapeXmlText(
            jsonValueToParamText(value)
          )}</param>`;
        })
        .join('\n');
    }
  } catch {
    return null;
  }

  return null;
}

function normalizeToolDisplayForStrictProxy(toolName: string, params: string) {
  if (toolName !== 'package_proxy') {
    return {
      displayToolName: toolName,
      displayParams: params
    };
  }

  const toolNameMatch = params.match(/<param\s+name="tool_name">([\s\S]*?)<\/param>/i);
  const paramsMatch = params.match(/<param\s+name="params">([\s\S]*?)<\/param>/i);
  const rawTargetToolName = toolNameMatch?.[1]?.trim() ?? '';
  const rawProxiedParams = paramsMatch?.[1]?.trim() ?? '';
  const displayToolName = normalizeEscapedTextForDisplay(rawTargetToolName).trim() || toolName;
  const displayParams = rawProxiedParams
    ? parseProxyJsonParamsToXml(normalizeEscapedTextForDisplay(rawProxiedParams)) ?? params
    : params;

  return {
    displayToolName,
    displayParams
  };
}

function estimateToolParamTokens(text: string) {
  let chineseCharCount = 0;
  let otherCharCount = 0;

  for (const char of text) {
    const code = char.charCodeAt(0);
    if (code >= 0x4e00 && code <= 0x9fff) {
      chineseCharCount += 1;
    } else {
      otherCharCount += 1;
    }
  }

  return Math.floor(chineseCharCount * 1.5 + otherCharCount * 0.25);
}

function extractParamPayloadsForSize(params: string) {
  const payloads: string[] = [];
  const tagRegex = /<\/?param\b[^>]*>/gi;
  let insideParam = false;
  let valueStart = -1;

  for (const match of params.matchAll(tagRegex)) {
    const tagText = match[0];
    const start = match.index ?? 0;
    const end = start + tagText.length;

    if (tagText.startsWith('</')) {
      if (insideParam) {
        payloads.push(normalizeEscapedTextForDisplay(params.slice(valueStart, start)));
        insideParam = false;
        valueStart = -1;
      }
      continue;
    }

    if (!insideParam) {
      insideParam = true;
      valueStart = end;
    }
  }

  if (insideParam && valueStart >= 0) {
    payloads.push(normalizeEscapedTextForDisplay(params.slice(valueStart)));
  }

  return payloads.length ? payloads : [normalizeEscapedTextForDisplay(params)];
}

function calculateToolParamsBytes(params: string) {
  return extractParamPayloadsForSize(params).reduce((sum, item) => {
    return sum + new TextEncoder().encode(item).length;
  }, 0);
}

function buildParamsHeadPreview(params: string, maxChars = 120) {
  const firstParamMatch = params.match(/<param.*?>([^<]*)<\/param>/i);
  const cleaned = (firstParamMatch?.[1]?.trim() || params).replace(/\n/g, ' ').trim();
  return cleaned.length <= maxChars ? cleaned : `${cleaned.slice(0, maxChars)}...`;
}

function buildToolSemanticDescription(toolName: string, params: string, useByteSummary: boolean) {
  if (!params.trim()) {
    return `工具调用: ${toolName}`;
  }

  const summary = useByteSummary ? `${calculateToolParamsBytes(params)} B` : buildParamsHeadPreview(params);
  return `工具调用: ${toolName}，调用参数: ${summary}`;
}

export function ToolDisplayComponent({ block }: { block: WebMessageContentBlock }) {
  const rawToolName = block.attrs?.name ?? 'tool';
  const rawParams = (block.content ?? '').trim();
  const { displayToolName, displayParams } = useMemo(() => {
    return normalizeToolDisplayForStrictProxy(rawToolName, rawParams);
  }, [rawParams, rawToolName]);
  const hasParams = displayParams.trim().length > 0;
  const isClosed = block.closed ?? true;
  const tokenEstimate = useMemo(() => estimateToolParamTokens(displayParams), [displayParams]);
  const useByteSummary =
    (displayToolName === 'apply_file' && !isClosed) ||
    (!isClosed && tokenEstimate > TOOL_PARAM_TOKEN_THRESHOLD);
  const summary = useByteSummary
    ? `${calculateToolParamsBytes(displayParams)} B`
    : buildParamsHeadPreview(displayParams);
  const [detailOpen, setDetailOpen] = useState(false);
  const iconName = resolveToolIconName(displayToolName);
  const semanticDescription = buildToolSemanticDescription(
    displayToolName,
    displayParams,
    useByteSummary
  );

  return (
    <>
      <ToolSummaryRow
        leadingIcon={iconName}
        onClick={hasParams ? () => setDetailOpen(true) : null}
        semanticDescription={semanticDescription}
        summary={summary}
        toolName={displayToolName}
      />

      {detailOpen ? (
        <ContentDetailDialog
          content={normalizeEscapedTextForDisplay(displayParams)}
          iconName={iconName}
          onDismiss={() => setDetailOpen(false)}
          title={`${displayToolName} 调用参数`}
        />
      ) : null}
    </>
  );
}
