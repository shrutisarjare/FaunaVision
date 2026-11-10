package com.endangered.species.ml;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The byte-level BPE tokenizer shipped with OpenAI CLIP, reimplemented so the text
 * encoder can be driven from Java without a Python runtime. Reads {@code vocab.json}
 * and {@code merges.txt} from the Hugging Face export.
 */
final class ClipTokenizer {

    /** CLIP always pads to 77 tokens; category embeddings need no more than that. */
    static final int CONTEXT_LENGTH = 77;

    /**
     * Same pre-tokenisation pattern as Hugging Face: contractions, word runs, single
     * digits and punctuation runs. {@code \p{L}} and {@code \p{N}} are Unicode-aware.
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+");

    private static final String END_OF_WORD = "</w>";

    private final Map<String, Integer> encoder;
    private final Map<String, Integer> ranks;
    private final Map<Integer, String> byteStrings = byteStrings();
    private final int bosId;
    private final int eosId;

    private ClipTokenizer(Map<String, Integer> encoder, Map<String, Integer> ranks, int bosId, int eosId) {
        this.encoder = encoder;
        this.ranks = ranks;
        this.bosId = bosId;
        this.eosId = eosId;
    }

    static ClipTokenizer load(Path vocabFile, Path mergesFile) throws IOException {
        Map<String, Integer> vocab = new ObjectMapper().readValue(
                Files.readAllBytes(vocabFile), new TypeReference<Map<String, Integer>>() {
                });

        Map<String, Integer> ranks = new HashMap<>();
        int rank = 0;
        for (String line : Files.readAllLines(mergesFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            ranks.put(trimmed, rank++);
        }

        Integer bos = vocab.get("<|startoftext|>");
        Integer eos = vocab.get("<|endoftext|>");
        if (bos == null || eos == null) {
            throw new IOException("Vocabulary is missing the CLIP start/end tokens");
        }
        return new ClipTokenizer(vocab, ranks, bos, eos);
    }

    /** Token ids for {@code text}, padded with the end-of-text id to a fixed length. */
    int[] encode(String text) {
        int[] ids = new int[CONTEXT_LENGTH];
        int position = 0;
        ids[position++] = bosId;

        Matcher matcher = TOKEN_PATTERN.matcher(normalize(text));
        while (matcher.find() && position < CONTEXT_LENGTH - 1) {
            for (int id : encodeToken(matcher.group())) {
                if (position >= CONTEXT_LENGTH - 1) {
                    break;
                }
                ids[position++] = id;
            }
        }

        ids[position++] = eosId;
        while (position < CONTEXT_LENGTH) {
            ids[position++] = eosId;
        }
        return ids;
    }

    private List<Integer> encodeToken(String token) {
        // No whole-word shortcut here: a standalone word must carry the end-of-word marker,
        // otherwise "eagle" resolves to the mid-word id and the encoder reads "eagle" as if
        // it continued into another word.
        List<Integer> ids = new ArrayList<>();
        for (String symbol : bpe(byteLevel(token))) {
            Integer id = encoder.get(symbol);
            if (id == null) {
                id = encoder.get(symbol.replace(END_OF_WORD, ""));
            }
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Greedy lowest-rank pair merging, the original CLIP {@code bpe()} loop. */
    private List<String> bpe(String token) {
        List<String> word = new ArrayList<>(token.length());
        for (int i = 0; i < token.length(); i++) {
            word.add(String.valueOf(token.charAt(i)));
        }
        int last = word.size() - 1;
        word.set(last, word.get(last) + END_OF_WORD);

        while (word.size() > 1) {
            int bestRank = Integer.MAX_VALUE;
            int bestIndex = -1;
            for (int i = 0; i < word.size() - 1; i++) {
                Integer rank = ranks.get(word.get(i) + " " + word.get(i + 1));
                if (rank != null && rank < bestRank) {
                    bestRank = rank;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0) {
                break;
            }
            word.set(bestIndex, word.get(bestIndex) + word.get(bestIndex + 1));
            word.remove(bestIndex + 1);
        }
        return word;
    }

    /** Lower-cases and collapses whitespace, as {@code CLIPTokenizer.normalize} does. */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Maps every UTF-8 byte onto a printable stand-in so BPE can see raw bytes. */
    private String byteLevel(String token) {
        StringBuilder builder = new StringBuilder();
        for (byte value : token.getBytes(StandardCharsets.UTF_8)) {
            builder.append(byteStrings.get(value & 0xFF));
        }
        return builder.toString();
    }

    /** The GPT-2/CLIP {@code bytes_to_unicode} table. */
    private static Map<Integer, String> byteStrings() {
        List<Integer> bytes = new ArrayList<>();
        for (int b = '!'; b <= '~'; b++) {
            bytes.add(b);
        }
        for (int b = 0xA1; b <= 0xAC; b++) {
            bytes.add(b);
        }
        for (int b = 0xAE; b <= 0xFF; b++) {
            bytes.add(b);
        }

        List<Integer> codePoints = new ArrayList<>(bytes);
        int extra = 0;
        for (int b = 0; b < 256; b++) {
            if (!bytes.contains(b)) {
                bytes.add(b);
                codePoints.add(256 + extra++);
            }
        }

        Map<Integer, String> table = new HashMap<>(512);
        for (int i = 0; i < bytes.size(); i++) {
            table.put(bytes.get(i), new String(Character.toChars(codePoints.get(i))));
        }
        return table;
    }
}
