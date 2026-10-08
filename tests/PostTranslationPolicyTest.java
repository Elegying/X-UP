package io.github.jared.xlowerseek;
public final class PostTranslationPolicyTest {
    private static int count;
    private static void check(boolean v){count++;if(!v)throw new AssertionError("case "+count);}
    public static void main(String[] args){
        check(PostTranslationPolicy.eligible("en","A reply to the original post"));
        check(PostTranslationPolicy.eligible("ja","これは返信です"));
        check(PostTranslationPolicy.eligible("ko","안녕하세요"));
        check(PostTranslationPolicy.eligible("ru","Привет"));
        check(PostTranslationPolicy.eligible("ar","مرحبا"));
        check(PostTranslationPolicy.eligible(null,"A quoted post"));
        check(PostTranslationPolicy.eligible("und","A nested reply"));
        check(!PostTranslationPolicy.eligible("zh-CN","这是中文 X-UP"));
        check(!PostTranslationPolicy.eligible("ZH_tw","繁體中文"));
        check(!PostTranslationPolicy.eligible(null,"中文"));
        check(!PostTranslationPolicy.eligible("en","https://x.com/example @someone"));
        check(!PostTranslationPolicy.eligible("en","12345 🎉🚀"));
        check(!PostTranslationPolicy.eligible("en"," "));
        check(!PostTranslationPolicy.eligible("en",null));
        check(!PostTranslationPolicy.eligible("zxx","abc"));
        check(PostTranslationPolicy.eligible("en","@author thanks! https://x.com/test"));
        check(!PostTranslationPolicy.eligible(null,"1x"));
        check(!PostTranslationPolicy.eligible(null,"1.25x"));
        check(!PostTranslationPolicy.eligible(null,"2×"));
        System.out.println("PostTranslationPolicy: "+count+" assertions passed");
    }
}
