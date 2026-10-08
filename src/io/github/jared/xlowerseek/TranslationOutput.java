package io.github.jared.xlowerseek;

import java.util.*;
import java.util.regex.*;

/** Invalid rich-text anchors or untranslated output must not poison the success cache. */
final class TranslationOutput {
    private static final Pattern MARKER=Pattern.compile("⟪X[^⟫]*⟫");
    static boolean valid(String source,String translated){
        if(translated==null||translated.trim().isEmpty()||translated.length()>32768||translated.equals(source))return false;
        if(!translated.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))return false;
        return markers(source).equals(markers(translated));
    }
    private static List<String> markers(String value){List<String> out=new ArrayList<>();Matcher m=MARKER.matcher(value);while(m.find())out.add(m.group());return out;}
}
