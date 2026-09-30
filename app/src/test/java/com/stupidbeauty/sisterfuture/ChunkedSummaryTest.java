package com.stupidbeauty.sisterfuture;
import org.json.*;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ChunkedSummaryTest {
    @Test public void emptyResponseRetriesSameInputAndLogsOnlyStatistics() throws Exception {
        List<String> logs = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        String result = ChunkedSummary.run(Arrays.asList("PRIVATE SOURCE"), (previous, fragment, index, total) -> {
            assertEquals("PRIVATE SOURCE", fragment);
            return calls.incrementAndGet() == 1 ? "" : "PRIVATE SUMMARY";
        }, logs::add);
        assertEquals("PRIVATE SUMMARY", result);
        assertEquals(2, calls.get());
        assertTrue(logs.toString().contains("reason=empty"));
        assertFalse(logs.toString().contains("PRIVATE"));
    }
    @Test public void oversizedResponseIsRefinedAndThenCarriedToNextSegment() throws Exception {
        String longSummary = String.join("", Collections.nCopies(12001,"x"));
        AtomicInteger calls = new AtomicInteger();
        String result = ChunkedSummary.run(Arrays.asList("first","second"), (previous, fragment, index, total) -> {
            int call = calls.incrementAndGet();
            if(call==1) return longSummary;
            if(call==2) {
                assertEquals("",previous);
                assertTrue(fragment.endsWith(longSummary));
                return "short";
            }
            assertEquals("short",previous);
            return "final";
        });
        assertEquals("final",result);
        assertEquals(3,calls.get());
    }
    @Test public void persistentOversizeStopsAfterThreeAttemptsWithSpecificError() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        try {
            ChunkedSummary.run(Arrays.asList("first","never processed"),(previous,fragment,index,total)->{
                calls.incrementAndGet();
                return String.join("",Collections.nCopies(12001,"x"));
            });
            fail();
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("1/2"));
            assertTrue(expected.getMessage().contains("12001"));
            assertTrue(expected.getMessage().contains("过长"));
        }
        assertEquals(3,calls.get());
    }
    @Test public void hugeMessageIsSplitWithoutLosingUnicodeOrCharacters() throws Exception {
        StringBuilder value = new StringBuilder();
        for (int i=0;i<100000;i++) value.append("中文😀abc");
        JSONObject message = new JSONObject().put("role","user").put("content",value.toString());
        List<String> chunks = ChunkedSummary.plan(Arrays.asList(message),48000);
        assertTrue(chunks.size()>8);
        StringBuilder restored = new StringBuilder();
        for(String chunk:chunks) {
            assertTrue(chunk.length()<=48000);
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length()-1)));
            assertFalse(Character.isLowSurrogate(chunk.charAt(0)));
            restored.append(chunk);
        }
        assertEquals(message.toString()+"\n",restored.toString());
    }
    @Test public void keepsNormalToolBatchAndUserTurnTogether() throws Exception {
        List<JSONObject> messages = Arrays.asList(
            new JSONObject().put("role","user").put("content","first"),
            new JSONObject().put("role","assistant").put("tool_calls",new JSONArray().put(new JSONObject().put("id","call"))),
            new JSONObject().put("role","tool").put("tool_call_id","call").put("content","result"),
            new JSONObject().put("role","user").put("content","second"));
        int groupSize=0;
        for(int i=0;i<3;i++) groupSize+=messages.get(i).toString().length()+1;
        List<String> chunks=ChunkedSummary.plan(messages,groupSize);
        assertEquals(2,chunks.size());
        assertTrue(chunks.get(0).contains("tool_call_id"));
        assertEquals(messages.get(3).toString()+"\n",chunks.get(1));
    }
    @Test public void rollingSummaryReceivesPreviousResultAndVisitsAllChunks() throws Exception {
        List<String> seen = new ArrayList<>();
        String result=ChunkedSummary.run(Arrays.asList("A","B","C"),(previous,fragment,index,total)->{
            assertEquals(3,total); seen.add(fragment);
            assertEquals(index-1,previous.length());
            return previous+fragment;
        });
        assertEquals("ABC",result);
        assertEquals(Arrays.asList("A","B","C"),seen);
    }
    @Test public void middleFailureDoesNotReturnPartialSummaryOrContinue() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        try {
            ChunkedSummary.run(Arrays.asList("A","B","C"),(previous,fragment,index,total)->{
                calls.incrementAndGet();
                if(index==2) throw new java.io.IOException("failed");
                return "partial";
            });
            fail("No result should be returned");
        } catch(java.io.IOException expected) { }
        assertEquals(2,calls.get());
    }
    @Test public void invalidIntermediateSummaryStopsTheOperation() throws Exception {
        try {
            ChunkedSummary.run(Arrays.asList("A","B"),(previous,fragment,index,total)->" ");
            fail();
        } catch(IllegalArgumentException expected) { }
    }
}
