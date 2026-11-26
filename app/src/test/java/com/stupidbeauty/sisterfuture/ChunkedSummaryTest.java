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
        String longSummary = String.join("", Collections.nCopies(ChunkedSummary.CHUNK_CHARACTERS + 1,"x"));
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
    @Test public void persistentOversizeMarksSegmentFailedButGreedyRunStillReturns() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        ChunkedSummary.Result result = ChunkedSummary.runGreedy(Arrays.asList("first","second"),
            (previous,fragment,index,total)->{
                calls.incrementAndGet();
                if(index==1) return String.join("",Collections.nCopies(ChunkedSummary.CHUNK_CHARACTERS + 1,"x"));
                return "second segment summary";
            }, event -> { }, null);
        assertEquals(4,calls.get());
        assertFalse(result.isComplete());
        assertEquals(Arrays.asList(1), result.failedSegments);
        assertEquals(Arrays.asList(2), result.succeededSegments);
        assertEquals("second segment summary", result.summary);
        assertFalse(result.succeededSegments.isEmpty());
    }
    @Test public void hugeMessageIsSplitWithoutLosingUnicodeOrCharacters() throws Exception {
        StringBuilder value = new StringBuilder();
        for (int i=0;i<100000;i++) value.append("中文😀abc");
        JSONObject message = new JSONObject().put("role","user").put("content",value.toString());
        List<String> chunks = ChunkedSummary.plan(Arrays.asList(message),188000);
        assertTrue(chunks.size()>2);
        StringBuilder restored = new StringBuilder();
        for(String chunk:chunks) {
            assertTrue(chunk.length()<=188000);
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
    @Test public void greedyModeKeepsPartialSummaryAndContinuesAfterMiddleFailure() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        ChunkedSummary.Result result = ChunkedSummary.runGreedy(Arrays.asList("A","B","C"),
            (previous,fragment,index,total)->{
                calls.incrementAndGet();
                if(index==2) throw new java.io.IOException("failed");
                return previous+fragment;
            }, event -> { }, null);
        assertEquals(3,calls.get());
        assertEquals("AC",result.summary);
        assertEquals(Arrays.asList(1,3),result.succeededSegments);
        assertEquals(Arrays.asList(2),result.failedSegments);
        assertFalse(result.isComplete());
    }
    @Test public void greedyModeReportsCompleteWhenEverySegmentSucceeds() throws Exception {
        ChunkedSummary.Result result = ChunkedSummary.runGreedy(Arrays.asList("A","B"),
            (previous,fragment,index,total)-> previous+fragment, event -> { }, null);
        assertTrue(result.isComplete());
        assertTrue(result.failedSegments.isEmpty());
        assertEquals(Arrays.asList(1,2),result.succeededSegments);
        assertEquals("AB",result.summary);
    }
    @Test public void greedyModeReportsEverySegmentFailedWhenNothingSucceeds() throws Exception {
        ChunkedSummary.Result result = ChunkedSummary.runGreedy(Arrays.asList("A","B"),
            (previous,fragment,index,total)-> { throw new java.io.IOException("failed"); }, event -> { }, null);
        assertFalse(result.isComplete());
        assertEquals(Arrays.asList(1,2),result.failedSegments);
        assertTrue(result.succeededSegments.isEmpty());
        assertEquals("",result.summary);
    }
    @Test public void greedyModeKeepsFirstSegmentWhenSecondFails() throws Exception {
        ChunkedSummary.Result result = ChunkedSummary.runGreedy(Arrays.asList("A","B","C"),
            (previous,fragment,index,total)->{
                if(index==2) throw new java.io.IOException("failed");
                return previous+fragment;
            }, event -> { }, null);
        assertEquals("AC",result.summary);
        assertEquals(Arrays.asList(2),result.failedSegments);
    }
    @Test public void planWithBoundariesReportsMessageIndexForCompleteFragments() throws Exception {
        List<JSONObject> messages = Arrays.asList(
            new JSONObject().put("role","user").put("content","first"),
            new JSONObject().put("role","assistant").put("content","reply"),
            new JSONObject().put("role","user").put("content","second"));
        List<ChunkedSummary.Chunk> chunks =
            ChunkedSummary.planWithBoundaries(messages, 100000);
        assertEquals(1,chunks.size());
        assertEquals(3,chunks.get(0).endMessageIndex);
    }
    @Test public void planWithBoundariesMarksFragmentsSplitInsideMessageAsUnknown() throws Exception {
        StringBuilder value = new StringBuilder();
        for (int i=0;i<40000;i++) value.append("中文abc");
        List<JSONObject> messages = Arrays.asList(
            new JSONObject().put("role","user").put("content",value.toString()));
        List<ChunkedSummary.Chunk> chunks =
            ChunkedSummary.planWithBoundaries(messages, 188000);
        assertTrue(chunks.size() > 1);
        for (ChunkedSummary.Chunk chunk : chunks) {
            assertEquals(-1,chunk.endMessageIndex);
        }
    }
}