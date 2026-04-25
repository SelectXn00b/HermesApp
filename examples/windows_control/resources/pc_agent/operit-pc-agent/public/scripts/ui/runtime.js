function flattenChildren(items, output = []) {
  for (const item of items) {
    if (Array.isArray(item)) {
      flattenChildren(item, output);
      continue;
    }

    if (item === null || item === undefined || item === false) {
      continue;
    }

    output.push(item);
  }

  return output;
}

function setAttributes(node, attrs = {}) {
  for (const [key, value] of Object.entries(attrs)) {
    if (value === undefined || value === null) {
      continue;
    }
    node.setAttribute(key, String(value));
  }
}

function setDataset(node, dataset = {}) {
  for (const [key, value] of Object.entries(dataset)) {
    node.dataset[key] = String(value);
  }
}

function setStyles(node, style = {}) {
  Object.assign(node.style, style);
}

function bindEvents(node, events = {}) {
  for (const [eventName, handler] of Object.entries(events)) {
    if (typeof handler === "function") {
      node.addEventListener(eventName, handler);
    }
  }
}

function applyProps(node, props, context) {
  if (!props) {
    return;
  }

  if (props.className) {
    node.className = props.className;
  }

  if (props.text !== undefined) {
    node.textContent = String(props.text);
  }

  if (props.html !== undefined) {
    node.innerHTML = String(props.html);
  }

  if (props.attrs) {
    setAttributes(node, props.attrs);
  }

  if (props.dataset) {
    setDataset(node, props.dataset);
  }

  if (props.style) {
    setStyles(node, props.style);
  }

  if (props.on) {
    bindEvents(node, props.on);
  }

  if (props.value !== undefined) {
    node.value = props.value;
  }

  if (props.checked !== undefined) {
    node.checked = !!props.checked;
  }

  if (props.disabled !== undefined) {
    node.disabled = !!props.disabled;
  }

  if (props.ref) {
    if (typeof props.ref === "string") {
      context.refs[props.ref] = node;
    } else if (typeof props.ref === "function") {
      props.ref(node, context);
    }
  }
}

function renderNode(vnode, context) {
  if (vnode === null || vnode === undefined || vnode === false) {
    return document.createDocumentFragment();
  }

  if (typeof vnode === "string" || typeof vnode === "number") {
    return document.createTextNode(String(vnode));
  }

  if (Array.isArray(vnode)) {
    const fragment = document.createDocumentFragment();
    for (const child of vnode) {
      fragment.appendChild(renderNode(child, context));
    }
    return fragment;
  }

  if (typeof vnode.type === "function") {
    const resolved = vnode.type({ ...(vnode.props || {}), children: vnode.children || [] }, context);
    return renderNode(resolved, context);
  }

  const node = document.createElement(vnode.type);
  applyProps(node, vnode.props, context);

  const children = vnode.children || [];
  for (const child of children) {
    node.appendChild(renderNode(child, context));
  }

  return node;
}

export function h(type, props = {}, ...children) {
  return {
    type,
    props: props || {},
    children: flattenChildren(children)
  };
}

export function render(vnode, context = { refs: {} }) {
  if (!context.refs) {
    context.refs = {};
  }
  return renderNode(vnode, context);
}

export function mount(rootNode, vnode, context = { refs: {} }) {
  if (!context.refs) {
    context.refs = {};
  }

  rootNode.replaceChildren(renderNode(vnode, context));
  return context;
}
