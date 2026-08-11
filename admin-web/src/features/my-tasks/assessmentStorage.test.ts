import { describe, expect, it } from "vitest";
import { clearSubmitToken, getOrCreateSubmitToken, readDraftCursor, writeDraftCursor } from "./assessmentStorage";

function memoryStorage(): Storage {
  const values = new Map<string, string>();
  return {
    get length() { return values.size; },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => Array.from(values.keys())[index] ?? null,
    removeItem: (key) => { values.delete(key); },
    setItem: (key, value) => { values.set(key, value); }
  };
}

describe("assessment storage", () => {
  it("removes answers while migrating a legacy draft snapshot", () => {
    const storage = memoryStorage();
    storage.setItem("psy-respondent-task-draft:42", JSON.stringify({
      answers: { "question-1": "sensitive answer" },
      currentIndex: 3,
      answerSheetId: 9,
      versionNo: 2
    }));

    expect(readDraftCursor(storage, "42")).toEqual({ currentIndex: 3, answerSheetId: 9, versionNo: 2 });
    expect(storage.getItem("psy-respondent-task-draft:42")).not.toContain("sensitive answer");
    expect(storage.getItem("psy-respondent-task-draft:42")).not.toContain("answers");
  });

  it("stores only the navigation cursor and answer sheet metadata", () => {
    const storage = memoryStorage();
    writeDraftCursor(storage, "42", { currentIndex: 4, answerSheetId: 10, versionNo: 3 });

    expect(JSON.parse(storage.getItem("psy-respondent-task-draft:42") ?? "{}")).toEqual({
      currentIndex: 4,
      answerSheetId: 10,
      versionNo: 3
    });
  });

  it("reuses a submit token until the completed request clears it", () => {
    const storage = memoryStorage();
    let sequence = 0;
    const createToken = () => `token-${++sequence}`;

    expect(getOrCreateSubmitToken(storage, "42", createToken)).toBe("token-1");
    expect(getOrCreateSubmitToken(storage, "42", createToken)).toBe("token-1");
    clearSubmitToken(storage, "42");
    expect(getOrCreateSubmitToken(storage, "42", createToken)).toBe("token-2");
  });
});
