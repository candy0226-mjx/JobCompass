package com.resumeai.compass;

import java.util.*;

/** Includes failures in denominators. Percentiles use the documented nearest-rank rule. */
final class EvaluationSummary {
    record Item(String expected, List<String> actual, boolean success, long durationMs,
                Long inputTokens, Long outputTokens, boolean usageComplete, int retries) {}
    static Map<String,Object> summarize(List<Item> items) {
        var out=new LinkedHashMap<String,Object>(); int n=items.size(),success=0,correct=0,negative=0,falseMatch=0,failedNegative=0,retries=0,complete=0;
        long input=0,output=0;
        for(var item:items) {
            if(item.success()) success++;
            if(item.success() && item.actual().size()==1 && item.actual().getFirst().equals(item.expected())) correct++;
            if(!item.expected().equals("MATCH")) { negative++; if(item.actual().contains("MATCH")) falseMatch++; if(!item.success()) failedNegative++; }
            retries+=item.retries();
            if(item.usageComplete() && item.inputTokens()!=null && item.outputTokens()!=null) complete++;
            if(item.inputTokens()!=null) input+=item.inputTokens(); if(item.outputTokens()!=null) output+=item.outputTokens();
        }
        var durations=items.stream().mapToLong(Item::durationMs).sorted().toArray();
        out.put("tasks",n);out.put("successfulTasks",success);out.put("failedTasks",n-success);out.put("labelMatches",correct);
        out.put("successRate",rate(success,n));out.put("labelAgreement",rate(correct,n));out.put("negativeTasks",negative);
        out.put("unsupportedMatches",falseMatch);out.put("failedNegativeTasks",failedNegative);out.put("falseMatchRate",rate(falseMatch,negative));
        out.put("retryCount",retries);out.put("usageCompleteTasks",complete);out.put("reportedInputTokens",input);out.put("reportedOutputTokens",output);
        out.put("meanTotalTokens",complete==n && n>0?(double)(input+output)/n:null);
        out.put("meanDurationMs",n==0?null:Arrays.stream(durations).average().orElseThrow());
        out.put("p50DurationMs",percentile(durations,0.50));out.put("p95DurationMs",percentile(durations,0.95));
        return out;
    }
    private static Double rate(int numerator,int denominator) { return denominator==0?null:(double)numerator/denominator; }
    private static Long percentile(long[] values,double p) { return values.length==0?null:values[(int)Math.ceil(values.length*p)-1]; }
}
