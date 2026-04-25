import { h } from "./runtime.js";

function cx(...classNames) {
  return classNames.filter(Boolean).join(" ");
}

export function Box(props = {}, ...children) {
  const tag = props.as || "div";
  const { as, ...rest } = props;
  return h(tag, rest, ...children);
}

export function Shell(props = {}, ...children) {
  return h("div", { className: cx("shell", props.className), ref: props.ref }, ...children);
}

export function Header(props = {}, ...children) {
  return h("header", { className: cx("app-header", props.className), ref: props.ref }, ...children);
}

export function HeaderTop(props = {}, ...children) {
  return h("div", { className: cx("app-header__top", props.className), ref: props.ref }, ...children);
}

export function Title(text, props = {}) {
  return h("h1", { className: cx("app-title", props.className), text, ref: props.ref });
}

export function Subtitle(text, props = {}) {
  return h("p", { className: cx("app-subtitle", props.className), text, ref: props.ref });
}

export function AppGrid(props = {}, ...children) {
  return h("div", { className: cx("app-grid", props.className), ref: props.ref }, ...children);
}

export function Column(props = {}, ...children) {
  return h("div", { className: cx("app-column", props.className), ref: props.ref }, ...children);
}

export function Toolbar(props = {}, ...children) {
  return h("div", { className: cx("toolbar", props.className), ref: props.ref }, ...children);
}

export function StatusRow(props = {}, ...children) {
  return h("div", { className: cx("status-row", props.className), ref: props.ref }, ...children);
}

export function StatusItem(props = {}) {
  return h(
    "div",
    { className: cx("status-item", props.className), ref: props.ref },
    h("span", { className: "status-item__label", text: props.label || "" }),
    h("span", { className: "status-item__value", text: props.value || "-", ref: props.valueRef })
  );
}

export function Notice(props = {}) {
  const toneClass = props.tone ? `is-${props.tone}` : "";
  return h("div", {
    className: cx("notice", toneClass, props.className),
    text: props.text || "",
    ref: props.ref
  });
}

export function Card(props = {}, ...children) {
  return h(
    "section",
    { className: cx("ui-card", props.className), ref: props.ref },
    h(
      "header",
      { className: "ui-card__header" },
      h(
        "div",
        {},
        h("h2", { className: "ui-card__title", text: props.title || "" }),
        props.subtitle ? h("p", { className: "ui-card__subtitle", text: props.subtitle }) : null
      ),
      h("div", { className: "toolbar" }, ...(props.actions || []))
    ),
    h("div", { className: "ui-card__body" }, ...children)
  );
}

export function Form(props = {}, ...children) {
  return h("form", { className: cx("ui-panel", props.className), ref: props.ref, attrs: props.attrs, on: props.on }, ...children);
}

export function Grid2(props = {}, ...children) {
  return h("div", { className: cx("ui-grid-2", props.className), ref: props.ref }, ...children);
}

export function Field(props = {}, control = null) {
  return h(
    "label",
    { className: cx("ui-field", props.className), ref: props.ref },
    h("span", { className: "ui-field__label", text: props.label || "" }),
    props.hint ? h("span", { className: "ui-field__hint", text: props.hint }) : null,
    control
  );
}

export function Input(props = {}) {
  return h("input", {
    className: cx("ui-input", props.className),
    ref: props.ref,
    attrs: {
      id: props.id,
      type: props.type || "text",
      placeholder: props.placeholder,
      min: props.min,
      max: props.max,
      step: props.step,
      autocomplete: props.autocomplete
    },
    value: props.value,
    on: props.on
  });
}

export function Select(props = {}) {
  const options = (props.options || []).map((item) =>
    h("option", {
      text: item.label,
      attrs: { value: item.value, disabled: item.disabled ? "disabled" : undefined }
    })
  );

  return h(
    "select",
    {
      className: cx("ui-select", props.className),
      ref: props.ref,
      attrs: { id: props.id },
      on: props.on
    },
    ...options
  );
}

export function Textarea(props = {}) {
  return h("textarea", {
    className: cx("ui-textarea", props.className),
    ref: props.ref,
    attrs: {
      id: props.id,
      rows: props.rows || 6,
      placeholder: props.placeholder
    },
    on: props.on
  });
}

export function Toggle(props = {}) {
  return h(
    "label",
    {
      className: cx("ui-toggle", props.className),
      ref: props.ref
    },
    h("input", {
      ref: props.inputRef,
      attrs: {
        id: props.id,
        type: "checkbox"
      },
      checked: !!props.checked,
      on: props.on
    }),
    h("span", { text: props.text || "" })
  );
}

export function Button(props = {}) {
  return h("button", {
    className: cx("ui-button", `ui-button--${props.variant || "primary"}`, props.className),
    ref: props.ref,
    text: props.text || "",
    attrs: {
      type: props.type || "button"
    },
    on: props.on
  });
}

export function ButtonGroup(props = {}, ...children) {
  return h("div", { className: cx("ui-button-group", props.className), ref: props.ref }, ...children);
}

export function Output(props = {}) {
  return h("pre", {
    className: cx("ui-output", props.className),
    ref: props.ref,
    text: props.text,
    attrs: {
      style: props.minHeight ? `min-height:${props.minHeight}px` : undefined
    }
  });
}

export function Panel(props = {}, ...children) {
  return h("div", { className: cx("ui-panel", props.className), ref: props.ref }, ...children);
}

export function PanelTitle(text, props = {}) {
  return h("p", { className: cx("ui-panel__title", props.className), text, ref: props.ref });
}

export function CheckList(props = {}, ...children) {
  return h("div", { className: cx("ui-check-list", props.className), ref: props.ref }, ...children);
}

export function CheckItem(props = {}) {
  return h(
    "label",
    {
      className: cx("ui-check", props.className),
      ref: props.ref
    },
    h("input", {
      ref: props.inputRef,
      attrs: {
        type: "checkbox",
        name: props.name || "allowedPreset",
        value: props.value
      },
      checked: !!props.checked
    }),
    h(
      "span",
      { className: "ui-check__main" },
      h("span", { className: "ui-check__name", text: props.title || "" }),
      h("span", { className: "ui-check__hint", text: props.hint || "" })
    )
  );
}

export function Text(props = {}) {
  return h(props.as || "span", {
    className: props.className,
    text: props.text || "",
    ref: props.ref
  });
}
