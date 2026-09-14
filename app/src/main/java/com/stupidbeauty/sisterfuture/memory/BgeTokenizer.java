package com.stupidbeauty.sisterfuture.memory;

import org.json.JSONObject;
import java.io.Reader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Case-sensitive BERT/WordPiece tokenizer for the pinned Chinese BGE model. */
public final class BgeTokenizer {
    public static final int LENGTH = 512;
    private final Map<String, Integer> vocabulary = new HashMap<>();

    public BgeTokenizer(Reader reader) throws Exception {
        StringBuilder json = new StringBuilder();
        char[] buffer = new char[4096];
        int n;
        while ((n = reader.read(buffer)) != -1) json.append(buffer, 0, n);
        JSONObject vocab = new JSONObject(json.toString()).getJSONObject("model").getJSONObject("vocab");
        Iterator<String> keys = vocab.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            vocabulary.put(key, vocab.getInt(key));
        }
        if (!Integer.valueOf(101).equals(vocabulary.get("[CLS]")) ||
                !Integer.valueOf(102).equals(vocabulary.get("[SEP]"))) {
            throw new IOException("Unexpected BGE vocabulary");
        }
    }

    public int[][] encode(String text) {
        List<Integer> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int offset = 0; offset < text.length() && tokens.size() < LENGTH - 2;) {
            // Added special tokens are preserved by the reference tokenizer.
            String special = null;
            for (String candidate : new String[]{"[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]"}) {
                if (text.startsWith(candidate, offset)) { special = candidate; break; }
            }
            if (special != null) {
                appendWord(word, tokens);
                tokens.add(vocabulary.get(special));
                offset += special.length();
                continue;
            }
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp == 0 || cp == 0xfffd) continue;
            if (cp == '\t' || cp == '\n' || cp == '\r' || Character.getType(cp) == Character.SPACE_SEPARATOR) {
                appendWord(word, tokens);
            } else if (isControl(cp)) {
                continue;
            } else if (isChinese(cp) || isPunctuation(cp)) {
                appendWord(word, tokens);
                word.appendCodePoint(cp);
                appendWord(word, tokens);
            } else {
                word.appendCodePoint(cp);
            }
        }
        appendWord(word, tokens);
        int[][] encoded = new int[2][LENGTH];
        encoded[0][0] = 101;
        int count = Math.min(tokens.size(), LENGTH - 2);
        for (int i = 0; i < count; i++) encoded[0][i + 1] = tokens.get(i);
        encoded[0][count + 1] = 102;
        for (int i = 0; i < count + 2; i++) encoded[1][i] = 1;
        return encoded;
    }

    private void appendWord(StringBuilder builder, List<Integer> output) {
        if (builder.length() == 0) return;
        String word = builder.toString();
        builder.setLength(0);
        if (word.codePointCount(0, word.length()) > 100) { output.add(100); return; }
        List<Integer> pieces = new ArrayList<>();
        int start = 0;
        while (start < word.length()) {
            int end = word.length();
            Integer id = null;
            while (end > start) {
                id = vocabulary.get((start == 0 ? "" : "##") + word.substring(start, end));
                if (id != null) break;
                end = word.offsetByCodePoints(end, -1);
            }
            if (id == null) { output.add(100); return; }
            pieces.add(id);
            start = end;
        }
        output.addAll(pieces);
    }

    private static boolean isControl(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONTROL || type == Character.FORMAT || type == Character.PRIVATE_USE ||
                type == Character.SURROGATE;
    }

    private static boolean isPunctuation(int cp) {
        if ((cp >= 33 && cp <= 47) || (cp >= 58 && cp <= 64) ||
                (cp >= 91 && cp <= 96) || (cp >= 123 && cp <= 126)) return true;
        int type = Character.getType(cp);
        return type == Character.CONNECTOR_PUNCTUATION || type == Character.DASH_PUNCTUATION ||
                type == Character.START_PUNCTUATION || type == Character.END_PUNCTUATION ||
                type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION ||
                type == Character.OTHER_PUNCTUATION;
    }

    private static boolean isChinese(int cp) {
        return (cp >= 0x4e00 && cp <= 0x9fff) || (cp >= 0x3400 && cp <= 0x4dbf) ||
                (cp >= 0x20000 && cp <= 0x2a6df) || (cp >= 0x2a700 && cp <= 0x2b73f) ||
                (cp >= 0x2b740 && cp <= 0x2b81f) || (cp >= 0x2b820 && cp <= 0x2ceaf) ||
                (cp >= 0xf900 && cp <= 0xfaff) || (cp >= 0x2f800 && cp <= 0x2fa1f);
    }
}
