package com.resumeai.compass;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.client.*;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.ChatResponse;

/** Per-task, numeric-only telemetry: never saves prompts, tool payloads, responses or credentials. */
public final class AnalysisMetrics {
    public record Attempt(String stage,int number,long durationMs,int toolCalls,String failureCode,Long inputTokens,Long outputTokens) {}
    public record Snapshot(String snapshotMode,List<Attempt> attempts,int retryCount,int toolCalls,int validationFailures,
                           int modelFailures,Long inputTokens,Long outputTokens,long reportedInputTokens,long reportedOutputTokens,
                           boolean usageComplete,long durationMs) {}
    private final String mode;
    private final long start=System.nanoTime();
    private final List<Attempt> attempts=new ArrayList<>();
    public AnalysisMetrics(String mode) { this.mode=mode; }
    public Capture begin(String stage,int number) { return new Capture(stage,number); }
    public Snapshot snapshot() {
        long input=0,output=0;int retries=0,tools=0,validation=0,model=0;boolean complete=!attempts.isEmpty();
        for(var a:attempts) {
            if(a.number()>1) retries++;tools+=a.toolCalls();
            if(a.failureCode()!=null) { if(a.failureCode().equals("MODEL_FAILED")) model++;else validation++; }
            if(a.inputTokens()==null || a.outputTokens()==null) complete=false;
            if(a.inputTokens()!=null) input+=a.inputTokens();if(a.outputTokens()!=null) output+=a.outputTokens();
        }
        return new Snapshot(mode,List.copyOf(attempts),retries,tools,validation,model,complete?input:null,complete?output:null,input,output,complete,elapsed(start));
    }
    private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start); }
    public final class Capture implements CallAdvisor {
        private final String stage;private final int number;private final long start=System.nanoTime();
        private Long input,output;private boolean finished;
        private Capture(String stage,int number) { this.stage=stage;this.number=number; }
        public String getName() { return "JobCompassTaskUsage"; }
        public int getOrder() { return Integer.MIN_VALUE+100; }
        public ChatClientResponse adviseCall(ChatClientRequest request,CallAdvisorChain chain) {
            var response=chain.nextCall(request);recordUsage(response.chatResponse());return response;
        }
        void recordUsage(ChatResponse response) {
            if(response==null || response.getMetadata()==null) return;
            var usage=response.getMetadata().getUsage();if(usage==null) return;
            var in=usage.getPromptTokens();var out=usage.getCompletionTokens();
            if(in!=null && out!=null && in>=0 && out>=0 && (long)in+out>0) { input=in.longValue();output=out.longValue(); }
        }
        public boolean finished() { return finished; }
        public void finish(int toolCalls,String failureCode) {
            if(finished) throw new IllegalStateException("Attempt already finished");finished=true;
            attempts.add(new Attempt(stage,number,elapsed(start),toolCalls,failureCode,input,output));
        }
    }
}
