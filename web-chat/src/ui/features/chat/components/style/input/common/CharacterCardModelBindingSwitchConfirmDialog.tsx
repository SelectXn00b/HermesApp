export function CharacterCardModelBindingSwitchConfirmDialog({
  open,
  onConfirm,
  onDismiss
}: {
  open: boolean;
  onConfirm: () => void;
  onDismiss: () => void;
}) {
  if (!open) {
    return null;
  }

  return (
    <div className="dialog-scrim" onClick={onDismiss} role="presentation">
      <div
        className="history-dialog model-selector-confirm-dialog"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <header>
          <h3>修改角色卡绑定模型</h3>
          <p>当前角色卡已绑定对话模型。继续后将修改该角色卡的模型绑定，不会修改全局对话模型配置。</p>
        </header>
        <footer>
          <button onClick={onDismiss} type="button">
            取消
          </button>
          <button onClick={onConfirm} type="button">
            确认修改
          </button>
        </footer>
      </div>
    </div>
  );
}
