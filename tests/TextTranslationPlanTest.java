package io.github.jared.xlowerseek;
import java.util.*;
public final class TextTranslationPlanTest {
 static int count;
 static void check(boolean value){count++;if(!value)throw new AssertionError("case "+count);}
 public static void main(String[] args){
  String source="Hello @alice read https://x.com/test";
  TextTranslationPlan.Result r=TextTranslationPlan.translate(source,Collections.emptyList(),t->t.replace("Hello","你好").replace("read","阅读"));
  check(r.text.equals("你好 @alice 阅读 https://x.com/test"));
  int at=source.indexOf("@alice");check(r.text.substring(r.offset(at),r.offset(at+6)).equals("@alice"));
  int url=source.indexOf("https");check(r.text.substring(r.offset(url),r.offset(source.length())).equals("https://x.com/test"));
  source="Click here now";
  r=TextTranslationPlan.translate(source,Arrays.asList(new TextTranslationPlan.Range(6,10,true)),t->t.replace("Click","点击").replace("now","现在"));
  check(r.text.equals("点击 here 现在"));check(r.text.substring(r.offset(6),r.offset(10)).equals("here"));
  r=TextTranslationPlan.translate("Hello world",Arrays.asList(new TextTranslationPlan.Range(0,5,false)),t->t.replace("Hello","你好").replace("world","世界"));
  check(r.text.equals("你好 世界"));check(r.offset(5)==2);check(r.offset(11)==5);
  r=TextTranslationPlan.translate("😀 hi",Collections.emptyList(),t->"😀 你好");check(r.offset(0)==0);check(r.offset(5)==5);
  r=TextTranslationPlan.translate("",Collections.emptyList(),t->"意外");check(r.text.isEmpty());
  r=TextTranslationPlan.translate("raw",Collections.emptyList(),t->null);check(r.text.equals("raw"));
  boolean rejected=false;try{TextTranslationPlan.translate("a",Arrays.asList(new TextTranslationPlan.Range(0,9,true)),t->t);}catch(IllegalArgumentException ok){rejected=true;}check(rejected);
  r=TextTranslationPlan.translate("mail me@example.com #topic",Collections.emptyList(),t->t.replace("mail","邮件"));check(r.text.equals("邮件 me@example.com #topic"));
  System.out.println("TextTranslationPlan: "+count+" assertions passed");
 }
}
