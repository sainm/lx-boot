export function formatDateTime(value?: string | number | Date | null) {
  if (value == null || value === "") return "-";

  if (value instanceof Date || typeof value === "number") {
    return formatDate(new Date(value));
  }

  const normalized = value.trim();
  const localDateTime = normalized.match(
    /^(\d{4})-(\d{2})-(\d{2})[T\s](\d{2}):(\d{2})(?::(\d{2}))?/
  );
  if (localDateTime) {
    return `${localDateTime[1]}-${localDateTime[2]}-${localDateTime[3]} ${localDateTime[4]}:${localDateTime[5]}`;
  }

  const parsed = new Date(normalized);
  return Number.isNaN(parsed.getTime()) ? normalized : formatDate(parsed);
}

function formatDate(date: Date) {
  const pad = (value: number) => String(value).padStart(2, "0");
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate())
  ].join("-") + ` ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
