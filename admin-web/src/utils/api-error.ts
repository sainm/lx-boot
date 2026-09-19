type ApiErrorBody = {
  code?: string;
  message?: string;
};

/**
 * Extracts the localized backend message (and business code) from an axios
 * error so mutations can surface *why* they failed instead of failing silently
 * (F-18 / F-20 / F-22).
 */
export function resolveApiErrorMessage(error: unknown, fallback: string): string {
  const response = (error as { response?: { data?: ApiErrorBody } } | undefined)?.response;
  const message = response?.data?.message?.trim();
  const code = response?.data?.code?.trim();
  if (message && code && code !== "0") {
    return `${message} (${code})`;
  }
  if (message) {
    return message;
  }
  if (code && code !== "0") {
    return `${fallback} (${code})`;
  }
  return fallback;
}
