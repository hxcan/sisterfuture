package com.stupidbeauty.sisterfuture.memory;

import org.junit.Before;
import org.junit.Test;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class BgeTokenizerTest {
    private BgeTokenizer tokenizer;
    @Before public void loadVocabulary() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(
                "src/main/assets/memory/tokenizer.json"), StandardCharsets.UTF_8)) {
            tokenizer = new BgeTokenizer(reader);
        }
    }
    // Expected IDs from Hugging Face tokenizers 0.23.2 with the pinned tokenizer.json.
    @Test public void chinese() { check("我喜欢喝咖啡", 101,2769,1599,3614,1600,1476,1565,102); }
    @Test public void caseSensitiveAndAccents() {
        check("Hello WORLD, café!",101,100,100,117,100,106,102);
        check("é e\u0301 É",101,100,100,100,102);
    }
    @Test public void mixedText() {
        check("中文\tEnglish\n混合１２３。",101,704,3152,100,3921,1394,10351,9089,511,102);
        check("emoji😀，繁體記憶",101,100,8024,5246,7768,6250,2741,102);
        check("𠀀扩展汉字",101,100,2810,2245,3727,2099,102);
    }
    @Test public void specialTokensAndControls() {
        check("[CLS]你好[SEP][MASK]",101,101,872,1962,102,103,102);
        check("abc\u0000def\u200bghi",101,8425,8510,8189,11304,8169,102);
        check("x\ue000y",101,166,8179,102);
        check("x\u0378y",101,100,102);
    }
    @Test public void emptyAndLongWords() {
        check("",101,102);
        char[] longWord = new char[101]; Arrays.fill(longWord,'a');
        check(new String(longWord),101,100,102);
    }
    @Test public void truncatesKeepingSeparator() {
        char[] text = new char[600]; Arrays.fill(text,'我');
        int[][] actual = tokenizer.encode(new String(text));
        assertEquals(101,actual[0][0]); assertEquals(102,actual[0][511]);
        for(int i=1;i<511;i++) assertEquals(2769,actual[0][i]);
        for(int mask:actual[1]) assertEquals(1,mask);
    }
    private void check(String text, int... expected) {
        int[][] actual = tokenizer.encode(text);
        assertArrayEquals(expected,Arrays.copyOf(actual[0],expected.length));
        for(int i=0;i<512;i++) {
            assertEquals(i<expected.length?1:0,actual[1][i]);
            if(i>=expected.length) assertEquals(0,actual[0][i]);
        }
    }
}
