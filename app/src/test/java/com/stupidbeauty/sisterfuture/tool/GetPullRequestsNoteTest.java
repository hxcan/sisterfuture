package com.stupidbeauty.sisterfuture.tool;

import org.junit.Test;
import static org.junit.Assert.*;

public class GetPullRequestsNoteTest {
  @Test public void invalidOrMissingNotesAreIgnored() {
    for (String note : new String[]{null, "", "  ", "GitHub", "GitHub 账号说明", "{broken", "[]", "null"}) {
      assertEquals("", GetPullRequestsTool.tokenFromNote(note));
    }
  }

  @Test public void missingNullAndWrongTypeTokensAreIgnored() {
    for (String note : new String[]{"{}", "{\"github_token\":null}",
        "{\"github_token\":{}}", "{\"github_token\":[]}",
        "{\"github_token\":123}", "{\"github_token\":false}", "{\"github_token\":\"  \"}"}) {
      assertEquals("", GetPullRequestsTool.tokenFromNote(note));
    }
  }

  @Test public void validNoteStillSuppliesToken() {
    assertEquals("test-token", GetPullRequestsTool.tokenFromNote("{\"github_token\":\" test-token \"}"));
  }
}
