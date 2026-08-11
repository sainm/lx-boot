import { describe, expect, it } from "vitest";
import { riskCategory, riskColor } from "./risk";

describe("risk model", () => {
  it.each([
    ["CRITICAL", "critical"], ["P0", "critical"],
    ["HIGH", "high"], ["P1", "high"],
    ["MODERATE", "moderate"], ["MEDIUM", "moderate"], ["ATTENTION", "moderate"], ["P2", "moderate"],
    ["LOW", "low"], ["NORMAL", "normal"]
  ])("maps %s to %s", (code, expected) => {
    expect(riskCategory(code)).toBe(expected);
  });

  it("does not present unknown risk as safe green", () => {
    expect(riskCategory("UNRECOGNIZED")).toBe("unknown");
    expect(riskColor("UNRECOGNIZED")).toBe("default");
  });
});
