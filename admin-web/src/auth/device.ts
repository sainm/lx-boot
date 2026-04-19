const DEVICE_ID_STORAGE_KEY = "psy-admin-web.device-id";

function generateDeviceId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `web-${crypto.randomUUID()}`;
  }
  return `web-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
}

export function getOrCreateDeviceId() {
  if (typeof window === "undefined") {
    return "web-server";
  }
  const existing = window.localStorage.getItem(DEVICE_ID_STORAGE_KEY)?.trim();
  if (existing) {
    return existing;
  }
  const created = generateDeviceId();
  window.localStorage.setItem(DEVICE_ID_STORAGE_KEY, created);
  return created;
}
