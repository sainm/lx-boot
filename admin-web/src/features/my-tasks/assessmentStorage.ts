const DRAFT_PREFIX = "psy-respondent-task-draft";
const SUBMIT_TOKEN_PREFIX = "psy-respondent-submit-token";
const volatileSubmitTokens = new Map<string, string>();
const storageIdentity = new WeakMap<object, number>();
let nextStorageIdentity = 1;

export type BrowserStorageKind = "local" | "session";

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

function volatileTokenKey(storage: Storage | null, key: string) {
  if (!storage) return `volatile:${key}`;
  let identity = storageIdentity.get(storage);
  if (!identity) {
    identity = nextStorageIdentity++;
    storageIdentity.set(storage, identity);
  }
  return `${identity}:${key}`;
}

function optionalPositiveNumber(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : undefined;
}

export function getOptionalBrowserStorage(kind: BrowserStorageKind): Storage | null {
  if (typeof window === "undefined") return null;
  try {
    return kind === "local" ? window.localStorage : window.sessionStorage;
  } catch {
    return null;
  }
}

function safeGetItem(storage: Storage | null, key: string): string | null {
  if (!storage) return null;
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
}

function safeSetItem(storage: Storage | null, key: string, value: string): boolean {
  if (!storage) return false;
  try {
    storage.setItem(key, value);
    return true;
  } catch {
    // Browser storage is an optional cache.  Private mode and quota limits
    // must not interrupt the server-backed assessment flow.
    return false;
  }
}

function safeRemoveItem(storage: Storage | null, key: string) {
  if (!storage) return;
  try {
    storage.removeItem(key);
  } catch {
    // Best effort cleanup only.
  }
}

export function readStorageItem(storage: Storage | null, key: string): string | null {
  return safeGetItem(storage, key);
}

export function writeStorageItem(storage: Storage | null, key: string, value: string) {
  safeSetItem(storage, key, value);
}

export function readDraftCursor(storage: Storage | null, taskId: string): DraftCursor | null {
  const key = draftKey(taskId);
  const raw = safeGetItem(storage, key);
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
    safeSetItem(storage, key, JSON.stringify(sanitized));
    return sanitized;
  } catch {
    safeRemoveItem(storage, key);
    return null;
  }
}

export function writeDraftCursor(storage: Storage | null, taskId: string, cursor: DraftCursor) {
  safeSetItem(
    storage,
    draftKey(taskId),
    JSON.stringify({
      currentIndex: Math.max(0, Math.trunc(cursor.currentIndex)),
      ...(cursor.answerSheetId ? { answerSheetId: cursor.answerSheetId } : {}),
      ...(cursor.versionNo ? { versionNo: cursor.versionNo } : {})
    })
  );
}

export function removeDraftCursor(storage: Storage | null, taskId: string) {
  safeRemoveItem(storage, draftKey(taskId));
}

export function getOrCreateSubmitToken(storage: Storage | null, taskId: string, createToken: () => string) {
  const key = submitTokenKey(taskId);
  const volatileKey = volatileTokenKey(storage, key);
  const existing = safeGetItem(storage, key)?.trim();
  if (existing) {
    volatileSubmitTokens.set(volatileKey, existing);
    return existing;
  }
  const volatile = volatileSubmitTokens.get(volatileKey);
  if (volatile) return volatile;
  const token = createToken();
  volatileSubmitTokens.set(volatileKey, token);
  safeSetItem(storage, key, token);
  return token;
}

export function clearSubmitToken(storage: Storage | null, taskId: string) {
  const key = submitTokenKey(taskId);
  volatileSubmitTokens.delete(volatileTokenKey(storage, key));
  safeRemoveItem(storage, key);
}
