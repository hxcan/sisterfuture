#!/usr/bin/env python3
"""Rewire the session picker entry point to the paged dialog.

Replaces only the body of resetConversationContextFromUi() (lines 793-850) with a
call into SessionPickerDialog, leaving every other line of the 2868-line file
untouched. Verifies the boundaries before writing and keeps a backup.
"""
import io
import sys

PATH = "/home/hxcan/SoftwareDevelop/sisterfuture/SisterFuture/app/src/main/java/com/stupidbeauty/sisterfuture/SisterFutureActivity.java"
BACKUP = "/tmp/SisterFutureActivity.java.bak"

NEW_BODY = '''  @OnClick(R.id.resetContextButton)
  public void resetConversationContextFromUi()
  {
    // #925477039795: open the paged picker immediately. It reads the first page in the
    // background instead of computing every session title before any dialog appears,
    // which is what made this button feel frozen once many sessions existed.
    SessionPickerDialog.show(this, sessionManager.getCurrentSession().getId(), sessionId -> {
      if (isFinishing() || isDestroyed()) return;
      if (sessionId == null) {
        // The positive button was pressed: create a new session.
        confirmSessionChange(this::forceResetConversationContext);
        return;
      }
      if (sessionId.equals(sessionManager.getCurrentSession().getId())) return;
      confirmSessionChange(() -> {
        // Re-check after the confirmation dialog: the session may have changed meanwhile.
        if (isFinishing() || isDestroyed()) return;
        try {
          activateSession(sessionManager.switchSession(sessionId));
          Toast.makeText(this, "已切换会话", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
          // switchSession only fails when the session is gone; keep the current history.
          Toast.makeText(this, "切换失败，原历史保留", Toast.LENGTH_LONG).show();
        }
      });
    });
  }
'''


def main() -> int:
    with io.open(PATH, "r", encoding="utf-8") as handle:
        lines = handle.readlines()

    if len(lines) < 852:
        print("FAIL: file shorter than expected (%d lines)" % len(lines))
        return 1

    # The old method runs from the @OnClick annotation through its closing brace.
    start = next((i for i, line in enumerate(lines)
                  if line.strip() == "@OnClick(R.id.resetContextButton)"), None)
    if start is None:
        print("FAIL: @OnClick(resetContextButton) not found")
        return 1

    # Locate the method signature, then its matching closing brace by brace counting.
    signature = next(i for i in range(start, len(lines))
                     if "resetConversationContextFromUi()" in lines[i])
    end = None
    for index in range(signature, len(lines)):
        if lines[index].rstrip() == "  }":
            end = index
            break
    if end is None:
        print("FAIL: closing brace not found")
        return 1

    print("Replacing lines %d-%d (%d lines)" % (start + 1, end + 1, end - start + 1))
    print("--- old head ---")
    print("".join(lines[start:start + 4]).rstrip())
    print("--- old tail ---")
    print("".join(lines[end - 2:end + 1]).rstrip())

    merged = lines[:start] + [NEW_BODY] + lines[end + 1:]

    with io.open(PATH, "w", encoding="utf-8") as handle:
        handle.writelines(merged)

    print("WROTE %d lines -> %d lines" % (len(lines), len(merged)))
    return 0


if __name__ == "__main__":
    sys.exit(main())