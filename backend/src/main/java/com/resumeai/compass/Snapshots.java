package com.resumeai.compass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class Snapshots {
    private Snapshots() {}
    public record Segment(String id, String text) {}
    public record Version(UUID id, UUID resumeId, int versionNo, String text, String hash,
                          List<Segment> segments, OffsetDateTime createdAt) {}
    public record Job(UUID id, String title, String company, String text, String hash,
                      String location, String sourceUrl, LocalDate deadline, List<Segment> segments) {}
    public static String text(String input) {
        if(input == null || input.isBlank()) throw CompassException.bad("正文不能为空");
        String value = input.replace("\r\n","\n").replace('\r','\n').strip();
        if(value.length() > 20_000) throw CompassException.bad("正文不能超过 20,000 字符");
        return value;
    }
    public static String field(String input, String name, int max) {
        if(input == null || input.isBlank() || input.strip().length() > max) throw CompassException.bad(name+"必填且长度不能超过 "+max);
        return input.strip();
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static List<Segment> segments(String text, String prefix) {
        var result = new ArrayList<Segment>();
        for(String line : text.split("\n+")) {
            if(!line.isBlank()) result.add(new Segment(prefix+(result.size()+1),line.strip()));
        }
        return List.copyOf(result);
    }
}
