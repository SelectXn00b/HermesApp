import type { ComposeColor, ComposeDslContext, ComposeNode } from "../../../types/compose-dsl";

type RenderTone = "start" | "progress" | "success" | "failure";

interface TonePalette {
  container: ComposeColor;
  title: ComposeColor;
  summary: ComposeColor;
  detail: ComposeColor;
  badge: ComposeColor;
}

function resolveTone(ctx: ComposeDslContext, tone: string): TonePalette {
  const scheme = ctx.MaterialTheme.colorScheme;
  switch (String(tone || "").trim().toLowerCase() as RenderTone) {
    case "start":
      return {
        container: scheme.secondaryContainer.copy({ alpha: 0.38 }),
        title: scheme.onSecondaryContainer,
        summary: scheme.onSecondaryContainer,
        detail: scheme.onSecondaryContainer.copy({ alpha: 0.78 }),
        badge: scheme.onSecondaryContainer.copy({ alpha: 0.74 }),
      };
    case "failure":
      return {
        container: scheme.errorContainer.copy({ alpha: 0.32 }),
        title: scheme.error,
        summary: scheme.error,
        detail: scheme.error.copy({ alpha: 0.82 }),
        badge: scheme.error.copy({ alpha: 0.76 }),
      };
    case "success":
      return {
        container: scheme.secondaryContainer.copy({ alpha: 0.30 }),
        title: scheme.onSecondaryContainer,
        summary: scheme.onSecondaryContainer,
        detail: scheme.onSecondaryContainer.copy({ alpha: 0.76 }),
        badge: scheme.onSecondaryContainer.copy({ alpha: 0.72 }),
      };
    case "progress":
    default:
      return {
        container: scheme.primaryContainer.copy({ alpha: 0.26 }),
        title: scheme.onPrimaryContainer,
        summary: scheme.onPrimaryContainer,
        detail: scheme.onPrimaryContainer.copy({ alpha: 0.76 }),
        badge: scheme.onPrimaryContainer.copy({ alpha: 0.72 }),
      };
  }
}

function buildHeader(
  ctx: ComposeDslContext,
  colors: TonePalette,
  title: string,
  badge: string
): ComposeNode {
  const children: ComposeNode[] = [
    ctx.UI.Text({
      text: title,
      style: "labelLarge",
      fontWeight: "semibold",
      color: colors.title,
      weight: 1,
    }),
  ];

  if (badge) {
    children.push(
      ctx.UI.Text({
        text: badge,
        style: "labelSmall",
        fontWeight: "medium",
        color: colors.badge,
      })
    );
  }

  return ctx.UI.Row(
    {
      fillMaxWidth: true,
      horizontalArrangement: "spaceBetween",
      verticalAlignment: "center",
    },
    children
  );
}

export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const [title] = ctx.useState("title", "Subagent");
  const [summary] = ctx.useState("summary", "");
  const [detail] = ctx.useState("detail", "");
  const [badge] = ctx.useState("badge", "");
  const [tone] = ctx.useState("tone", "progress");
  const colors = resolveTone(ctx, String(tone || ""));

  const bodyChildren: ComposeNode[] = [
    buildHeader(
      ctx,
      colors,
      String(title || "Subagent"),
      String(badge || "").trim()
    ),
  ];

  if (String(summary || "").trim()) {
    bodyChildren.push(
      ctx.UI.Text({
        text: String(summary || "").trim(),
        style: "bodySmall",
        color: colors.summary,
        fontSize: 11,
        maxLines: 4,
      })
    );
  }

  if (String(detail || "").trim()) {
    bodyChildren.push(
      ctx.UI.Text({
        text: String(detail || "").trim(),
        style: "labelSmall",
        color: colors.detail,
        fontSize: 10,
        maxLines: 2,
      })
    );
  }

  return ctx.UI.Card(
    {
      fillMaxWidth: true,
      paddingHorizontal: 4,
      paddingVertical: 2,
      containerColor: colors.container,
      elevation: String(tone || "").trim().toLowerCase() === "progress" ? 1 : 0,
      shape: { cornerRadius: 10 },
    },
    ctx.UI.Column(
      {
        padding: 10,
        spacing: 4,
      },
      bodyChildren
    )
  );
}
