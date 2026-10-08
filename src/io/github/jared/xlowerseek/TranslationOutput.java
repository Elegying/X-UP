package io.github.jared.xlowerseek;

import java.util.*;
import java.util.regex.*;

/** Invalid rich-text anchors or untranslated output must not poison the success cache. */
final class TranslationOutput {
    private static final Pattern MARKER=Pattern.compile("⟪X[^⟫]*⟫");
    static boolean valid(String source,String translated){
        if(!clean(source,translated)||translated.length()>32768||translated.equals(source))return false;
        if(!translated.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))return false;
        return markers(source).equals(markers(translated));
    }
    /** Check each model segment before caching, as well as the assembled result. */
    static boolean clean(String source,String translated){
        if(translated==null||translated.trim().isEmpty())return false;
        for(String token:new String[]{"\u2047","\uFFFD","<unk>"})
            if(count(translated,token)>count(source,token))return false;
        if(repeated(translated)&&!repeated(source))return false;
        // Ordinary question marks are legal. A letter-bearing input turning into only punctuation is not.
        return !source.codePoints().anyMatch(Character::isLetter)
            ||translated.codePoints().anyMatch(Character::isLetter);
    }
    private static boolean repeated(String text){
        // Ignore separators: decoder loops often alternate words with commas/spaces.
        StringBuilder compact=new StringBuilder();text.codePoints().filter(Character::isLetterOrDigit).forEach(compact::appendCodePoint);
        String s=compact.toString();
        for(int width=1;width<=16;width++)for(int start=0;start+width*4<=s.length();start++){
            if(width*4<8)continue;
            boolean same=true;for(int repeat=1;repeat<4;repeat++)if(!s.regionMatches(start,s,start+width*repeat,width)){same=false;break;}
            if(same)return true;
        }
        return false;
    }
    private static int count(String text,String token){int n=0,at=0;while((at=text.indexOf(token,at))>=0){n++;at+=token.length();}return n;}
    private static List<String> markers(String value){List<String> out=new ArrayList<>();Matcher m=MARKER.matcher(value);while(m.find())out.add(m.group());return out;}
}
