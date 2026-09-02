/** 通用确认 Modal：危险操作（如删除）弹层确认。 */
export function ConfirmModal({
  open,
  title,
  message,
  confirmText,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  message: string;
  confirmText: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  if (!open) {
    return null;
  }
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onCancel}>
      <div
        className="w-[400px] rounded-2xl border border-app-border bg-app-panel p-6"
        onClick={(event) => event.stopPropagation()}
      >
        <h3 className="text-sm font-medium text-app-text">{title}</h3>
        <p className="mt-2 text-sm text-app-text-dim">{message}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg border border-app-border px-4 py-2 text-sm text-app-text-dim hover:bg-app-panel-hover hover:text-app-text"
          >
            取消
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-lg bg-red-500 px-4 py-2 text-sm text-white hover:opacity-90"
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
