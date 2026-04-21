export function getIsFloatingMode() {
  return typeof document !== 'undefined' && Boolean(document.fullscreenElement);
}

export async function toggleFloatingWindow() {
  if (typeof document === 'undefined') {
    return false;
  }

  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen?.();
    return true;
  }

  await document.exitFullscreen?.();
  return false;
}
