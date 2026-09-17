package com.stupidbeauty.sisterfuture.tool;

import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CreateGitHubCommitAliasesTest {
    @Test public void schemaUsesCamelCaseAndRetainsToolName() throws Exception {
        CreateGitHubCommitTool tool=new CreateGitHubCommitTool(null);
        assertEquals("createGithubCommit",tool.getName());assertTrue(tool.isAsync());
        JSONObject function=tool.getDefinition().getJSONObject("function");
        assertEquals(tool.getName(),function.getString("name"));
        JSONObject schema=function.getJSONObject("parameters");
        JSONObject props=schema.getJSONObject("properties");
        for(Iterator<String> keys=props.keys();keys.hasNext();) assertFalse(keys.next().contains("_"));
        for(String key:new String[]{"commitMessage","readFromPhone","phonePath"}) assertTrue(props.has(key));
        JSONArray required=schema.getJSONArray("required");
        assertEquals(5,required.length());assertEquals("commitMessage",required.getString(4));
        assertTrue(tool.getDefaultSystemPromptEnhancement().contains("phonePath"));
    }
    @Test public void oldAndNewParametersNormalizeIdentically() throws Exception {
        JSONObject old=new JSONObject().put("commit_message","update").put("read_from_phone",true)
                .put("phone_path","/sdcard/Download/file.txt");
        JSONObject modern=new JSONObject().put("commitMessage","update").put("readFromPhone",true)
                .put("phonePath","/sdcard/Download/file.txt");
        JSONObject normalized=CreateGitHubCommitTool.normalizeArguments(old);
        assertEquals(modern.length(),normalized.length());
        for(Iterator<String> keys=modern.keys();keys.hasNext();) {
            String key=keys.next();assertEquals(modern.get(key),normalized.get(key));
        }
        assertTrue(old.has("phone_path"));assertFalse(old.has("phonePath"));
    }
    @Test public void mixedArgumentsPreferNewValuesIncludingFalseAndEmpty() throws Exception {
        JSONObject result=CreateGitHubCommitTool.normalizeArguments(new JSONObject()
                .put("commit_message","old").put("commitMessage","new")
                .put("read_from_phone",true).put("readFromPhone",false)
                .put("phone_path","old-path").put("phonePath",""));
        assertEquals("new",result.getString("commitMessage"));
        assertFalse(result.getBoolean("readFromPhone"));assertEquals("",result.getString("phonePath"));
        assertFalse(result.has("read_from_phone"));assertFalse(result.has("phone_path"));
    }
    @Test public void filePayloadAndDeletionParametersAreUntouched() throws Exception {
        String content="{\"phone_path\":\"content must not be rewritten\"}";
        JSONObject args=new JSONObject().put("commit_message","delete or upload").put("delete",true)
                .put("owner","owner").put("repo","repo").put("branch","feature/test")
                .put("path","dir/phone_path.txt").put("content",content).put("encoding","base64").put("token","test-token");
        JSONObject result=CreateGitHubCommitTool.normalizeArguments(args);
        for(String key:new String[]{"owner","repo","branch","path","content","encoding","delete","token"})
            assertEquals(args.get(key),result.get(key));
        assertFalse(result.has("readFromPhone"));assertFalse(result.has("phonePath"));
    }
}
