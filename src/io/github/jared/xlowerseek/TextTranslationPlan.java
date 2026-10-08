package io.github.jared.xlowerseek;

import java.util.*;
import java.util.regex.*;

/** Splits at annotation boundaries so clickable ranges remain exact after translation. */
final class TextTranslationPlan {
    interface Translator { String translate(String text); }
    static final class Range {
        final int start,end; final boolean protectedText;
        Range(int start,int end,boolean protect){this.start=start;this.end=end;this.protectedText=protect;}
    }
    static final class Result {
        final String text; private final int[] offsets;
        Result(String text,int[] offsets){this.text=text;this.offsets=offsets;}
        int offset(int old){return offsets[Math.max(0,Math.min(old,offsets.length-1))];}
    }
    private static final Pattern TOKENS=Pattern.compile("https?://\\S+|[@#][\\p{L}\\p{N}_]+|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    static Result translate(String source,List<Range> annotations,Translator translator){
        List<Range> ranges=new ArrayList<>(annotations);
        Matcher matcher=TOKENS.matcher(source);
        while(matcher.find())ranges.add(new Range(matcher.start(),matcher.end(),true));
        TreeSet<Integer> bounds=new TreeSet<>();bounds.add(0);bounds.add(source.length());
        for(Range range:ranges){
            if(range.start<0||range.end<range.start||range.end>source.length())throw new IllegalArgumentException("Invalid text range");
            bounds.add(range.start);bounds.add(range.end);
        }
        for(int i=0;i<source.length();i++)if(source.charAt(i)=='\n'||source.charAt(i)=='\r'){bounds.add(i);bounds.add(i+1);ranges.add(new Range(i,i+1,true));}
        int[] offsets=new int[source.length()+1];StringBuilder output=new StringBuilder();
        Integer previous=null;
        for(int boundary:bounds){
            if(previous!=null&&boundary>previous){
                int start=previous,end=boundary;boolean protect=false;
                for(Range range:ranges)if(range.protectedText&&range.start<end&&range.end>start){protect=true;break;}
                String original=source.substring(start,end),translated=original;
                if(!protect){
                    int left=0,right=original.length();
                    while(left<right&&Character.isWhitespace(original.charAt(left)))left++;
                    while(right>left&&Character.isWhitespace(original.charAt(right-1)))right--;
                    if(right>left){String value=translator.translate(original.substring(left,right));
                        if(value!=null&&!value.isEmpty())translated=original.substring(0,left)+value+original.substring(right);}
                }
                if(translated==null||translated.isEmpty())translated=original;
                int base=output.length();output.append(translated);
                for(int i=start;i<=end;i++)offsets[i]=base+(int)((long)(i-start)*translated.length()/(end-start));
            }
            previous=boundary;
        }
        return new Result(output.toString(),offsets);
    }
}
