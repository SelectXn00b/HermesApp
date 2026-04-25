function unescapeXml(input: string) {
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

function copyText(text: string) {
  void navigator.clipboard.writeText(text).catch(() => {});
}

export function ParamVisualizer({ xmlContent }: { xmlContent: string }) {
  const params = Array.from(
    xmlContent.matchAll(/<param\s+name="([^"]+)">([\s\S]*?)<\/param>/gi)
  ).map((match) => ({
    name: match[1],
    value: unescapeXml(match[2].trim())
  }));

  if (!params.length) {
    return <div className="param-visualizer-fallback">{xmlContent}</div>;
  }

  return (
    <div className="param-visualizer">
      {params.map((param) => (
        <section className="param-visualizer-card" key={`${param.name}-${param.value}`}>
          <header className="param-visualizer-head">
            <strong>{param.name}</strong>
            <button
              aria-label={`Copy ${param.name}`}
              className="param-visualizer-copy"
              onClick={() => copyText(param.value)}
              type="button"
            >
              复制
            </button>
          </header>
          <pre className="param-visualizer-value">{param.value}</pre>
        </section>
      ))}
    </div>
  );
}
