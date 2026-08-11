const DRAFT_PREFIX = "psy-respondent-task-draft";
const SUBMIT_TOKEN_PREFIX = "psy-respondent-submit-token";

export type DraftCursor = {
  currentIndex: number;
  answerSheetId?: number;
  versionNo?: number;
};

function draftKey(taskId: string) {
  return `${DRAFT_PREFIX}:${taskId}`;
}

function submitTokenKey(taskId: string) {
  return `${SUBMIT_TOKEN_PREFIX}:${taskId}`;
}

function optionalPositiveNumber(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : undefined;
}

export function readDraftCursor(storage: Storage, taskId: string): DraftCursor | null {
  const raw = storage.getItem(draftKey(taskId));
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const currentIndex = typeof parsed.currentIndex === "number" && Number.isFinite(parsed.currentIndex)
      ? Math.max(0, Math.trunc(parsed.currentIndex))
      : 0;
    const sanitized: DraftCursor = {
      currentIndex,
      answerSheetId: optionalPositiveNumber(parsed.answerSheetId),
      versionNo: optionalPositiveNumber(parsed.versionNo)
    };
    // Rewrite legacy snapshots immediately so plaintext answers are removed.
    writeDraftCursor(storage, taskId, sanitized);
    return sanitized;
  } catch {
    storage.removeItem(draftKey(taskId));
    return null;
  }
}

export function writeDraftCursor(storage: Storage, taskId: string, cursor: DraftCursor) {
  storage.setItem(
    draftKey(taskId),
    JSON.stringify({
      currentIndex: Math.max(0, Math.trunc(cursor.currentIndex)),
      ...(cursor.answerSheetId ? { answerSheetId: cursor.answerSheetId } : {}),
      ...(cursor.versionNo ? { versionNo: cursor.versionNo } : {})
    })
  );
}

export function removeDraftCursor(storage: Storage, taskId: string) {
  storage.removeItem(draftKey(taskId));
}

export function getOrCreateSubmitToken(storage: Storage, taskId: string, createToken: () => string) {
  const key = submitTokenKey(taskId);
  const existing = storage.getItem(key)?.trim();
  if (existing) return existing;
  const token = createToken();
  storage.setItem(key, token);
  return token;
}

export function clearSubmitToken(storage: Storage, taskId: string) {
  storage.removeItem(submitTokenKey(taskId));
}
