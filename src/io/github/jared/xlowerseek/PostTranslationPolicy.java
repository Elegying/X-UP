package io.github.jared.xlowerseek;

import java.util.Locale;

/** Only human-readable post content is eligible; handles missing language metadata. */
final class PostTranslationPolicy {
    static boolean eligible(String language,String text){
        if(text==null||text.trim().isEmpty())return false;
        if(text.trim().matches("(?i)\\d+(?:\\.\\d+)?[x×]"))return false;
        String lang=language==null?"":language.toLowerCase(Locale.ROOT).replace('_','-');
        if(lang.equals("zh")||lang.startsWith("zh-")||lang.equals("zxx"))return false;
        String body=text.replaceAll("https?://\\S+|@[\\p{L}\\p{N}_]+", "");
        for(int i=0;i<body.length();){
            int cp=body.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isLetter(cp)&&Character.UnicodeScript.of(cp)!=Character.UnicodeScript.HAN)return true;
        }
        return false;
    }
}
