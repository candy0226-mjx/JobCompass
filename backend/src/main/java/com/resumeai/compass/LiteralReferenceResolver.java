package com.resumeai.compass;

import java.util.*;

/** Unique literal source alignment only. Whitespace/line wraps may differ; words may not. */
final class LiteralReferenceResolver {
    private record Origin(int segment,int offset) {}
    List<Evidence.Reference> resolve(Evidence.Reference ref,List<Snapshots.Segment> segments) {
        if(ref==null || ref.quote()==null || ref.quote().isBlank()) throw invalid();
        if(segments.stream().anyMatch(s->s.id().equals(ref.segmentId()) && s.text().contains(ref.quote()))) return List.of(ref);
        var text=new StringBuilder();var origins=new ArrayList<Origin>();
        for(int i=0;i<segments.size();i++) for(int j=0;j<segments.get(i).text().length();j++) {
            char c=segments.get(i).text().charAt(j);
            if(!blank(c)) {text.append(c);origins.add(new Origin(i,j));}
        }
        var needle=new StringBuilder();for(char c:ref.quote().toCharArray()) if(!blank(c)) needle.append(c);
        if(needle.isEmpty()) throw invalid();
        var matches=new ArrayList<Integer>();int position=text.indexOf(needle.toString());
        while(position>=0) {matches.add(position);position=text.indexOf(needle.toString(),position+1);}
        if(matches.size()!=1) throw invalid();
        int start=matches.getFirst(),stop=start+needle.length();var refs=new ArrayList<Evidence.Reference>();
        for(int i=start;i<stop;) {
            var first=origins.get(i);int end=i+1;
            while(end<stop && origins.get(end).segment()==first.segment()) end++;
            var segment=segments.get(first.segment());
            refs.add(new Evidence.Reference(segment.id(),segment.text().substring(first.offset(),origins.get(end-1).offset()+1)));
            i=end;
        }
        // Whitespace must not join/split English words or numbers ("not able" != "notable").
        String source=refs.stream().map(Evidence.Reference::quote).reduce((a,b)->a+"\n"+b).orElse("");
        if(refs.size()>5 || !tokens(source).equals(tokens(ref.quote()))) throw invalid();return List.copyOf(refs);
    }
    private List<String> tokens(String text) {return java.util.regex.Pattern.compile("[A-Za-z0-9_+#]+").matcher(text).results().map(java.util.regex.MatchResult::group).toList();}
    private boolean blank(char c) {return Character.isWhitespace(c)||Character.isSpaceChar(c);}
    private CompassException invalid() {return new CompassException(422,"INVALID_EVIDENCE","引用无法唯一对齐原文（仅允许空白/换行对齐），不得改写、跨过正文或猜测出处");}
}
