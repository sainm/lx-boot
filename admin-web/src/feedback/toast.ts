import { message } from "antd";

type ToastType = "success" | "info" | "warning" | "error";

export function showToast(type: ToastType, content: string, key?: string) {
  void message.open({
    type,
    content,
    key,
    duration: type === "error" ? 4 : 2.5
  });
}
