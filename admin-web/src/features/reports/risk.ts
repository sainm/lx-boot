export type RiskCategory = "critical" | "high" | "moderate" | "low" | "normal" | "unknown";

export function riskCategory(code?: string | null): RiskCategory {
  switch (code?.trim().toUpperCase()) {
    case "CRITICAL":
    case "P0":
      return "critical";
    case "HIGH":
    case "P1":
      return "high";
    case "MODERATE":
    case "MEDIUM":
    case "ATTENTION":
    case "P2":
      return "moderate";
    case "LOW":
      return "low";
    case "NORMAL":
      return "normal";
    default:
      return "unknown";
  }
}

export function riskColor(code?: string | null) {
  switch (riskCategory(code)) {
    case "critical":
      return "magenta";
    case "high":
      return "red";
    case "moderate":
      return "gold";
    case "low":
      return "blue";
    case "normal":
      return "green";
    default:
      return "default";
  }
}
