package com.resumeai.application.analysis;

import java.util.*;
import java.util.regex.Pattern;

/** Literal technology recognition only. Occurrences are not proof of proficiency. */
public final class TechnologyLexicon {
    public static final String VERSION = "technology-v1";
    public record Occurrence(String canonical, String original, int start, int end, String source) {}
    public record Scan(List<Occurrence> occurrences, List<String> unknownCandidates) {}
    private record Alias(String canonical, Pattern pattern, String source) {}
    private static final List<Alias> ALIASES = new ArrayList<>();
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9+#]*(?:[.\u002d][A-Za-z0-9+#]+)*");
    private static final Set<String> ORDINARY = new HashSet<>(Arrays.asList(("a an and the for with you your our are this that will have has from job role work team using "
        + "experience skills responsibilities requirements ability we need built services designed workflow years year "
        + "of in to on or is be as at by it its can good strong familiar knowledge proficient understanding development "
        + "developer engineer internship intern computer science bachelor degree preferred required basic basics "
        + "coding code documentation design system systems maintain support product business application applications "
        + "web backend frontend full stack fullstack api apis http https www com cn github address title technical "
        + "mission description boot cvnlp hr mail email io ttl gpa cet cet-4 cet-6 rbac jwt ioc bean "
        + "performance optimization testing test software data university learn learning use used including related "
        + "minimum least months month days day per week participate improve service platform programming language "
        + "please send resume plus such etc").split("\\s+")));
    static {
        term("java"); term("javascript", "js"); term("typescript", "ts"); term("python"); term("go", "golang");
        term("c++"); term("c#"); term("spring boot", "springboot", "spring-boot");
        term("spring ai", "springai"); term("spring cloud", "springcloud"); term("spring security"); term("spring");
        term("mysql"); alias("mysql", "mysqll", "CORRECTION");
        term("postgresql", "postgres", "pgsql"); term("redis"); term("redisson"); term("rabbitmq", "rabbit mq");
        term("kafka"); term("elasticsearch", "elastic search"); term("doris", "apache doris"); term("tomcat");
        term("jvm", "java虚拟机", "java 虚拟机"); term("shell"); term("linux"); term("docker");
        term("kubernetes", "k8s"); term("mybatis-plus", "mybatis plus"); term("mybatis");
        term("sql"); term("oracle"); term("mongodb"); term("pgvector"); term("minio"); term("nginx");
        term("rag", "检索增强生成"); term("llm", "大语言模型", "大模型", "large language model");
        term("agent", "智能体"); term("tool calling", "toolcalling", "工具调用"); term("prompt", "提示词");
        term("openai"); term("ollama"); term("deepseek"); term("react"); term("next.js", "nextjs");
        term("node.js", "nodejs"); term("vue", "vue.js"); term("fastapi"); term("jpa"); term("hibernate");
        term("flyway"); term("git"); term("maven"); term("gradle"); term("junit"); term("rpc");
        term("rest", "restful"); term("sse"); term("websocket"); term("aop"); term("servlet");
        term("hadoop"); term("spark"); term("flink"); term("hive"); term("dubbo"); term("nacos");
        term("microservices", "微服务"); term("multithreading", "多线程"); term("design patterns", "设计模式");
        term("algorithms", "算法"); term("data structures", "数据结构");
    }
    private static void term(String canonical, String... alternatives) {
        alias(canonical, canonical, "EXACT");
        for (String value : alternatives) alias(canonical, value, "ALIAS");
    }
    private static void alias(String canonical, String value, String source) {
        String literal = Pattern.quote(value).replace(" ", "\\E\\s+\\Q");
        ALIASES.add(new Alias(canonical, Pattern.compile("(?<![A-Za-z0-9_+#])" + literal
            + "(?![A-Za-z0-9_+#])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), source));
    }
    public Scan scan(String input) {
        String text = input == null ? "" : input;
        var candidates = new ArrayList<Occurrence>();
        for (var alias : ALIASES) {
            var matcher = alias.pattern().matcher(text);
            while (matcher.find()) candidates.add(new Occurrence(alias.canonical(), matcher.group(), matcher.start(), matcher.end(), alias.source()));
        }
        candidates.sort(Comparator.comparingInt(Occurrence::start)
            .thenComparing(Comparator.comparingInt((Occurrence o) -> o.end() - o.start()).reversed())
            .thenComparing(Occurrence::canonical));
        var selected = new ArrayList<Occurrence>(); int end = -1;
        for (var item : candidates) if (item.start() >= end) { selected.add(item); end = item.end(); }
        var unknown = new TreeSet<String>(); var token = TOKEN.matcher(text);
        while (token.find()) {
            int start = token.start(), stop = token.end(); String value = token.group().toLowerCase(Locale.ROOT);
            if (value.length() >= 3 && !ORDINARY.contains(value)
                && selected.stream().noneMatch(o -> start < o.end() && stop > o.start())) unknown.add(value);
        }
        return new Scan(List.copyOf(selected), List.copyOf(unknown));
    }
}
