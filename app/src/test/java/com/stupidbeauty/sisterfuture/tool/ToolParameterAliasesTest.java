package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ToolParameterAliasesTest {
    @Test public void aliasesPreserveLongValuesAndDoNotMutateInput() throws Exception {
        JSONObject source=new JSONObject().put("parent_issue_id",889546934309L);
        JSONObject normalized=ToolParameterAliases.normalize(source,"parentIssueId");
        assertEquals(889546934309L,normalized.getLong("parentIssueId"));
        assertFalse(normalized.has("parent_issue_id"));
        assertTrue(source.has("parent_issue_id"));assertFalse(source.has("parentIssueId"));
    }
    @Test public void canonicalWinsRegardlessOfOrderIncludingEmptyAndNull() throws Exception {
        for(Object value:new Object[]{1L,"",JSONObject.NULL}) {
            for(boolean canonicalFirst:new boolean[]{true,false}) {
                JSONObject source=new JSONObject();
                if(canonicalFirst) source.put("apiKey",value);
                source.put("api_key","old");source.put("apiKey",value);
                JSONObject result=ToolParameterAliases.normalize(source,"apiKey");
                assertEquals(value,result.get("apiKey"));assertFalse(result.has("api_key"));
            }
        }
    }
    @Test public void onlyDeclaredTopLevelKeysAreChanged() throws Exception {
        JSONObject nested=new JSONObject().put("project_id",2);
        JSONObject source=new JSONObject().put("project_id",1).put("custom_field",nested).put("subject","x");
        JSONObject result=ToolParameterAliases.normalize(source,"projectId","subject","parentIssueId");
        assertTrue(result.has("custom_field"));assertSame(nested,result.getJSONObject("custom_field"));
        assertTrue(nested.has("project_id"));assertFalse(result.has("parentIssueId"));
        assertEquals(result.toString(),ToolParameterAliases.normalize(result,"projectId").toString());
    }
    @Test public void invalidCanonicalNameIsRejected() {
        assertThrows(IllegalArgumentException.class,()->ToolParameterAliases.normalize(new JSONObject(),"project_id"));
    }
}
