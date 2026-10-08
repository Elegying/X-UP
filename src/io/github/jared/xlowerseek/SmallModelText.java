package io.github.jared.xlowerseek;
import java.util.*;import java.util.regex.*;

/** Small MT models do not understand prompts. Preserve anchors outside inference, in sentence-sized units. */
final class SmallModelText {
 interface Translator {String translate(String text,boolean japanese)throws Exception;}
 private static final Pattern MARKERS=Pattern.compile("⟪X[^⟫]*⟫");
 static boolean japanese(String text){return text.codePoints().anyMatch(c->{Character.UnicodeScript s=Character.UnicodeScript.of(c);return s==Character.UnicodeScript.HIRAGANA||s==Character.UnicodeScript.KATAKANA;});}
 static String translate(String source,Translator translator,TranslationCancellation cancel)throws Exception{
  boolean ja=japanese(source);StringBuilder out=new StringBuilder();Matcher m=MARKERS.matcher(source);int at=0;
  while(m.find()){append(source.substring(at,m.start()),ja,translator,cancel,out);out.append(m.group());at=m.end();}
  append(source.substring(at),ja,translator,cancel,out);return out.toString();
 }
 private static void append(String text,boolean ja,Translator translator,TranslationCancellation cancel,StringBuilder out)throws Exception{
  // Preserve line breaks and whitespace. Bound pathological paragraphs before tokenization.
  int start=0;
  for(int i=0;i<text.length();i++){
   char c=text.charAt(i);boolean newline=c=='\n'||c=='\r';
   boolean boundary=newline||c=='。'||c=='！'||c=='？'||((c=='.'||c=='!'||c=='?')&&(i+1==text.length()||Character.isWhitespace(text.charAt(i+1))))||(i-start>=240&&(ja||Character.isWhitespace(c))&&!Character.isHighSurrogate(c));
   if(boundary){unit(text.substring(start,newline?i:i+1),ja,translator,cancel,out);if(newline)out.append(c);start=i+1;}
  }
  unit(text.substring(start),ja,translator,cancel,out);
 }
 private static void unit(String text,boolean ja,Translator translator,TranslationCancellation cancel,StringBuilder out)throws Exception{
  cancel.check();int left=0,right=text.length();while(left<right&&Character.isWhitespace(text.charAt(left)))left++;while(right>left&&Character.isWhitespace(text.charAt(right-1)))right--;
  String core=text.substring(left,right);out.append(text,0,left);
  if(!core.isEmpty()&&PostTranslationPolicy.eligible(null,core)){
   String translated=translator.translate(core,ja);cancel.check();if(translated==null||translated.trim().isEmpty())throw new IllegalStateException("模型未返回完整译文");out.append(translated.trim());
  }else out.append(core);
  out.append(text,right,text.length());
 }
}
