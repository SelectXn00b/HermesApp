import type { WebMessageAttachment, WebUploadedAttachment } from '../util/chatTypes';

export function uploadedAttachmentToMessageAttachment(
  upload: WebUploadedAttachment
): WebMessageAttachment {
  return {
    id: upload.attachment_id,
    file_name: upload.file_name,
    mime_type: upload.mime_type,
    file_size: upload.file_size
  };
}
