export function FullscreenInputDialog({
  value,
  onValueChange,
  onDismiss,
  onConfirm
}: {
  value: string;
  onValueChange: (value: string) => void;
  onDismiss: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="dialog-scrim" role="presentation">
      <div className="fullscreen-input-dialog" role="dialog">
        <header>
          <span>输入扩展</span>
          <h3>全屏输入</h3>
        </header>
        <textarea onChange={(event) => onValueChange(event.target.value)} value={value} />
        <footer>
          <button onClick={onDismiss} type="button">
            取消
          </button>
          <button onClick={onConfirm} type="button">
            完成
          </button>
        </footer>
      </div>
    </div>
  );
}
