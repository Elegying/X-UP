package io.github.jared.xlowerseek;
/** Uses HY-MT's published context template. The background is never appended to the visible result. */
final class LocalTranslationPrompt {
 static String build(String text,String context){
  String instruction="将以下文本翻译为简体中文，注意只需要输出翻译后的结果，不要额外解释：";
  if(text.contains("⟪X"))instruction+="保留所有 ⟪X…⟫ 标记和它们的顺序。";
  String target=safe(text);
  if(context==null||context.trim().isEmpty())return instruction+"\n\n"+target;
  return safe(PostContextIndex.clip(context,800))+"\n参考上面的信息，把下面的文本翻译成简体中文，注意不需要翻译上文，也不要额外解释。"+(text.contains("⟪X")?"保留所有 ⟪X…⟫ 标记和它们的顺序。":"")+"\n\n"+target;
 }
 private static String safe(String s){return s.replace("<|","＜|").replace("|>","|＞").replace("<｜","＜｜").replace("｜>","｜＞");}
}
