package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.memory.SemanticMemorySearch;
import android.content.res.AssetManager;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class SearchMemoryToolTest {
    @Test public void keepsToolNameAndExposesOptionalSearchControls() throws Exception {
        SearchMemoryTool tool=new SearchMemoryTool((q,l,s)->null);
        assertEquals("searchMemory",tool.getName()); assertTrue(tool.isAsync());
        JSONObject parameters=tool.getDefinition().getJSONObject("function").getJSONObject("parameters");
        assertEquals(1,parameters.getJSONArray("required").length());
        assertEquals("query",parameters.getJSONArray("required").getString(0));
        assertTrue(parameters.getJSONObject("properties").has("limit"));
        assertTrue(parameters.getJSONObject("properties").has("min_similarity"));
    }
    @Test public void oldQueryOnlyCallRunsOffCallerThreadAndReportsFallback() throws Exception {
        long caller=Thread.currentThread().getId();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        AtomicReference<JSONObject> response=new AtomicReference<>();
        CountDownLatch done=new CountDownLatch(1);
        SearchMemoryTool tool=new SearchMemoryTool((query,limit,min)->{
            assertNotEquals(caller,Thread.currentThread().getId());
            assertEquals(5,limit); assertEquals(0.5,min,0);
            return new SemanticMemorySearch((AssetManager)null).search(Collections.emptyList(),query,limit,min);
        });
        tool.executeAsync(new JSONObject().put("query","咖啡"),new Tool.OnResultCallback() {
            public void onResult(JSONObject result) { response.set(result); done.countDown(); }
            public void onError(Exception error) { failure.set(error); done.countDown(); }
        });
        assertTrue(done.await(5,TimeUnit.SECONDS)); assertNull(failure.get());
        assertEquals("success",response.get().getString("status"));
        assertEquals("keyword_fallback",response.get().getString("search_mode"));
        assertEquals(0,response.get().getInt("found_count"));
    }
    @Test public void optionalControlsReachBackend() throws Exception {
        SearchMemoryTool tool=new SearchMemoryTool((query,limit,min)->{
            assertEquals(10,limit); assertEquals(0.7,min,0);
            return new SemanticMemorySearch((AssetManager)null).search(Collections.emptyList(),query,limit,min);
        });
        assertEquals("success",tool.execute(new JSONObject().put("query","coffee").put("limit",10)
                .put("min_similarity",0.7)).getString("status"));
    }
}
