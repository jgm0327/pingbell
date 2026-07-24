package com.monit.pingbell.runbook.embedding;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RunbookChunker {
    static final List<String> REQUIRED_SECTIONS = List.of(
            "증상", "영향", "확인 절차", "안전한 완화", "복구 검증", "재발 방지", "참고 링크");
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "(?m)^(#{1,6})[\\t ]+(증상|영향|확인 절차|안전한 완화|복구 검증|재발 방지|참고 링크)[\\t ]*$");
    private static final int DEFAULT_MAX_CHUNK_CHARACTERS = 1_200;

    private final int maxChunkCharacters;

    public RunbookChunker() {
        this(DEFAULT_MAX_CHUNK_CHARACTERS);
    }

    public RunbookChunker(int maxChunkCharacters) {
        if (maxChunkCharacters < 100) {
            throw new IllegalArgumentException("maxChunkCharacters must be at least 100");
        }
        this.maxChunkCharacters = maxChunkCharacters;
    }

    public List<RunbookChunk> chunk(Long tenantId, String documentId, int version, String markdown) {
        String normalized = normalize(markdown);
        List<Section> sections = parseSections(normalized);
        List<RunbookChunk> chunks = new ArrayList<>();
        int order = 0;
        for (Section section : sections) {
            List<String> bodies = splitSection(section.heading(), section.body());
            for (int part = 0; part < bodies.size(); part++) {
                String content = bodies.get(part).isBlank()
                        ? section.heading()
                        : section.heading() + "\n\n" + bodies.get(part);
                String contentHash = sha256(content);
                String identity = documentId + "\u0000" + version + "\u0000" + section.title()
                        + "\u0000" + part + "\u0000" + contentHash;
                chunks.add(new RunbookChunk(tenantId, documentId, version, "rbch_" + sha256(identity),
                        order++, section.title(), content, contentHash));
            }
        }
        return List.copyOf(chunks);
    }

    private List<Section> parseSections(String markdown) {
        Matcher matcher = SECTION_HEADING.matcher(markdown);
        List<Heading> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new Heading(matcher.start(), matcher.end(), matcher.group(), matcher.group(2)));
        }
        if (headings.size() != REQUIRED_SECTIONS.size()) {
            throw new IllegalArgumentException("Runbook must contain each required section exactly once.");
        }
        for (int i = 0; i < REQUIRED_SECTIONS.size(); i++) {
            if (!REQUIRED_SECTIONS.get(i).equals(headings.get(i).title())) {
                throw new IllegalArgumentException("Runbook sections must use the required order.");
            }
        }

        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            int bodyEnd = i + 1 < headings.size() ? headings.get(i + 1).start() : markdown.length();
            String body = markdown.substring(heading.end(), bodyEnd).strip();
            sections.add(new Section(heading.heading().stripTrailing(), heading.title(), body));
        }
        return sections;
    }

    private List<String> splitSection(String heading, String body) {
        if (body.isBlank()) {
            return List.of("");
        }
        int bodyLimit = Math.max(1, maxChunkCharacters - heading.length() - 2);
        List<String> blocks = markdownBlocks(body);
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            for (String candidate : splitOversizedBlock(block, bodyLimit)) {
                int separator = current.isEmpty() ? 0 : 2;
                if (!current.isEmpty() && current.length() + separator + candidate.length() > bodyLimit) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private List<String> markdownBlocks(String body) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean fenced = false;
        for (String line : body.split("\n", -1)) {
            boolean fenceLine = line.stripLeading().startsWith("```");
            if (line.isBlank() && !fenced) {
                addBlock(blocks, current);
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line.stripTrailing());
            if (fenceLine) {
                fenced = !fenced;
            }
        }
        addBlock(blocks, current);
        return blocks;
    }

    private List<String> splitOversizedBlock(String block, int limit) {
        if (block.length() <= limit || block.stripLeading().startsWith("```")) {
            return List.of(block);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : block.split("\n")) {
            for (String piece : splitLine(line, limit)) {
                int separator = current.isEmpty() ? 0 : 1;
                if (!current.isEmpty() && current.length() + separator + piece.length() > limit) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(piece);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private List<String> splitLine(String line, int limit) {
        List<String> parts = new ArrayList<>();
        String remaining = line;
        while (remaining.length() > limit) {
            int splitAt = remaining.lastIndexOf(' ', limit);
            if (splitAt < 1) {
                splitAt = limit;
            }
            parts.add(remaining.substring(0, splitAt).stripTrailing());
            remaining = remaining.substring(splitAt).stripLeading();
        }
        if (!remaining.isEmpty()) {
            parts.add(remaining);
        }
        return parts.isEmpty() ? List.of("") : parts;
    }

    private void addBlock(List<String> blocks, StringBuilder current) {
        if (!current.isEmpty()) {
            blocks.add(current.toString());
            current.setLength(0);
        }
    }

    private String normalize(String markdown) {
        if (markdown == null) {
            throw new IllegalArgumentException("markdown must not be null");
        }
        return markdown.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record Heading(int start, int end, String heading, String title) {
    }

    private record Section(String heading, String title, String body) {
    }
}
