package io.github.jared.xlowerseek;

import java.util.Locale;
import java.util.regex.Pattern;

/** Only human-readable post content is eligible; handles missing language metadata. */
final class PostTranslationPolicy {
    private static final Pattern COUNTER=Pattern.compile("(?i)[+-]?[0-9][0-9,.]*\\s*[KMB万亿千]?\\+?");
    private static final Pattern SPEED=Pattern.compile("(?i)\\d+(?:\\.\\d+)?[x×]");
    private static final Pattern IDENTIFIERS=Pattern.compile("https?://\\S+|@[\\p{L}\\p{N}_]+");
    static boolean eligible(String language,String text){
        if(text==null||text.trim().isEmpty())return false;
        if(SPEED.matcher(text.trim()).matches()||COUNTER.matcher(text.trim()).matches())return false;
        String lang=language==null?"":language.toLowerCase(Locale.ROOT).replace('_','-');
        if(lang.equals("zh")||lang.startsWith("zh-")||lang.equals("zxx"))return false;
        String body=IDENTIFIERS.matcher(text).replaceAll("");
        for(int i=0;i<body.length();){
            int cp=body.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isLetter(cp)&&Character.UnicodeScript.of(cp)!=Character.UnicodeScript.HAN)return true;
        }
        return false;
    }
}
