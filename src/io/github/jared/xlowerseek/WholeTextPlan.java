package io.github.jared.xlowerseek;

import java.util.*;
import java.util.regex.*;

/** One semantic request, with exact anchors for rich text and protected identifiers. */
final class WholeTextPlan {
    private static final Pattern TOKENS=Pattern.compile(TextIdentifiers.URL+"|[@#][\\p{L}\\p{N}_]+|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final class Marker {final String token,value;final int start,end;Marker(String t,String v,int s,int e){token=t;value=v;start=s;end=e;}}
    final String source,encoded;private final List<Marker> markers=new ArrayList<>();
    WholeTextPlan(String source,List<TextTranslationPlan.Range> annotations){
        this.source=source;TreeSet<Integer> bounds=new TreeSet<>();bounds.add(0);bounds.add(source.length());
        List<TextTranslationPlan.Range> protectedRanges=new ArrayList<>();
        for(TextTranslationPlan.Range r:annotations){if(r.start>=0&&r.end>=r.start&&r.end<=source.length()){bounds.add(r.start);bounds.add(r.end);}}
        Matcher matcher=TOKENS.matcher(source);while(matcher.find()){bounds.add(matcher.start());bounds.add(matcher.end());protectedRanges.add(new TextTranslationPlan.Range(matcher.start(),matcher.end(),true));}
        String prefix="⟪X";while(source.contains(prefix))prefix+="X";
        StringBuilder out=new StringBuilder();Integer previous=null;
        for(int end:bounds){
            if(previous!=null){int start=previous;boolean protect=false;for(TextTranslationPlan.Range r:protectedRanges)if(r.start<=start&&r.end>=end){protect=true;break;}
                String part=source.substring(start,end);
                if(protect){String token=prefix+markers.size()+"⟫";markers.add(new Marker(token,part,start,end));out.append(token);}else out.append(part);
            }
            if(end>0&&end<source.length()){String token=prefix+markers.size()+"⟫";markers.add(new Marker(token,"",end,end));out.append(token);}
            previous=end;
        }
        encoded=out.toString();
    }
    TextTranslationPlan.Result decode(String response){
        if(response.equals(encoded))return identity();
        StringBuilder out=new StringBuilder();TreeMap<Integer,Integer> anchors=new TreeMap<>();anchors.put(0,0);int cursor=0;
        for(Marker m:markers){int at=response.indexOf(m.token,cursor);if(at<0||response.indexOf(m.token,at+m.token.length())>=0)return identity();
            out.append(response,cursor,at);anchors.put(m.start,out.length());out.append(m.value);anchors.put(m.end,out.length());cursor=at+m.token.length();}
        out.append(response.substring(cursor));anchors.put(source.length(),out.length());
        // Unrecognized / reordered markers must not leak into the rendered post.
        if(out.indexOf("⟪X")>=0&&!source.contains("⟪X"))return identity();
        int[] offsets=new int[source.length()+1];Map.Entry<Integer,Integer> previous=null;
        for(Map.Entry<Integer,Integer> next:anchors.entrySet()){
            if(previous!=null){int start=previous.getKey(),end=next.getKey();for(int i=start;i<=end;i++)offsets[i]=previous.getValue()+(int)((long)(i-start)*(next.getValue()-previous.getValue())/Math.max(1,end-start));}
            previous=next;
        }
        return new TextTranslationPlan.Result(out.toString(),offsets);
    }
    private TextTranslationPlan.Result identity(){int[] offsets=new int[source.length()+1];for(int i=0;i<offsets.length;i++)offsets[i]=i;return new TextTranslationPlan.Result(source,offsets);}
}
