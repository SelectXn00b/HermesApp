import { useRef } from 'react';
import { AttachIcon, CloseIcon, PlusIcon } from '../util/chatIcons';
import { InputOverlayPopup } from './style/input/common/InputOverlayPopup';

function AttachmentSelectorButton({
  title,
  disabled,
  onClick
}: {
  title: string;
  disabled?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      className={`attachment-selector-button ${disabled ? 'is-disabled' : ''}`}
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      <span className="attachment-selector-icon">
        <PlusIcon size={16} />
      </span>
      <strong>{title}</strong>
    </button>
  );
}

export function AttachmentSelector({
  visible,
  onUploadFiles,
  onDismiss
}: {
  visible: boolean;
  onUploadFiles: (files: FileList) => void;
  onDismiss: () => void;
}) {
  const imageInputRef = useRef<HTMLInputElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  if (!visible) {
    return null;
  }

  return (
    <InputOverlayPopup onDismiss={onDismiss} panelClassName="attachment-selector-panel">
      <header>
        <span>添加附件</span>
        <button onClick={onDismiss} type="button">
          <CloseIcon size={16} />
        </button>
      </header>

      <div className="attachment-selector-list">
        <AttachmentSelectorButton onClick={() => imageInputRef.current?.click()} title="照片" />
        <AttachmentSelectorButton disabled title="拍照" />
        <AttachmentSelectorButton disabled title="记忆" />
        <AttachmentSelectorButton disabled title="音频" />
        <AttachmentSelectorButton onClick={() => fileInputRef.current?.click()} title="文件" />
        <AttachmentSelectorButton disabled title="屏幕内容" />
        <AttachmentSelectorButton disabled title="通知" />
        <AttachmentSelectorButton disabled title="定位" />
      </div>

      <div className="attachment-selector-note">
        <AttachIcon size={14} />
        <span>当前 Web 端可直接选择图片和文件，其余入口保持 App 的面板位置与层级。</span>
      </div>

      <input
        accept="image/*"
        hidden
        multiple
        onChange={(event) => {
          if (event.target.files) {
            onUploadFiles(event.target.files);
          }
          event.target.value = '';
        }}
        ref={imageInputRef}
        type="file"
      />
      <input
        hidden
        multiple
        onChange={(event) => {
          if (event.target.files) {
            onUploadFiles(event.target.files);
          }
          event.target.value = '';
        }}
        ref={fileInputRef}
        type="file"
      />
    </InputOverlayPopup>
  );
}
