package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CreateGitHubCommitTokenTest {
    private CreateGitHubCommitTool withNote(String note) {
        return new CreateGitHubCommitTool(null) {
            @Override public String getNote(Context context) { return note; }
        };
    }

    @Test public void explicitTokenWinsWithoutReadingNote() throws Exception {
        CreateGitHubCommitTool tool = new CreateGitHubCommitTool(null) {
            @Override public String getNote(Context context) {
                throw new AssertionError("Explicit token must bypass note parsing");
            }
        };
        assertEquals("explicit", tool.resolveToken(new JSONObject().put("token", " explicit ")));
    }

    @Test public void missingOrInvalidTokenFallsBackWithoutMutatingArguments() throws Exception {
        CreateGitHubCommitTool tool = withNote("{\"github_token\":\" saved \"}");
        assertEquals("saved", tool.resolveToken(new JSONObject()));
        for (Object value : new Object[]{JSONObject.NULL, "", "  ", 42, true,
                new JSONObject().put("secret", "dummy"), new JSONArray()}) {
            JSONObject arguments = new JSONObject().put("token", value);
            String before = arguments.toString();
            assertEquals("saved", tool.resolveToken(arguments));
            assertEquals(before, arguments.toString());
        }
    }

    @Test public void malformedNotesAndInvalidSavedTokensGiveSafeMissingTokenError() throws Exception {
        for (String note : new String[]{null, "", " ", "GitHub dummy-secret", "{broken",
                "[]", "null", "{}", "{\"github_token\":null}",
                "{\"github_token\":123}", "{\"github_token\":true}",
                "{\"github_token\":{\"secret\":\"dummy-secret\"}}",
                "{\"github_token\":[]}", "{\"github_token\":\"  \"}"}) {
            try {
                withNote(note).resolveToken(new JSONObject());
                fail("Expected missing-token error");
            } catch (IllegalArgumentException expected) {
                assertEquals("缺少 GitHub 访问令牌 (token)，且未在备注中配置", expected.getMessage());
            }
        }
    }

    @Test public void sameCredentialResolutionForUploadAndDeletion() throws Exception {
        for (boolean delete : new boolean[]{false, true}) {
            JSONObject arguments = new JSONObject().put("delete", delete)
                    .put("content", "keep exact content\n");
            assertEquals("saved", withNote("{\"github_token\":\"saved\"}").resolveToken(arguments));
            assertEquals(delete, arguments.getBoolean("delete"));
            assertEquals("keep exact content\n", arguments.getString("content"));
        }
    }
}
