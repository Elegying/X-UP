package io.github.jared.xlowerseek;

/** The installed small models accept English and Japanese, not every foreign script. */
final class SmallModelPolicy {
 enum Route { KEEP, ENGLISH, JAPANESE }
 static Route route(String text){
  if(!PostTranslationPolicy.eligible(null,text))return Route.KEEP;
  boolean latin=false,han=false,kana=false;
  for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);
   if(!Character.isLetter(cp))continue;
   switch(Character.UnicodeScript.of(cp)){
    case LATIN:latin=true;break;
    case HAN:han=true;break;
    case HIRAGANA:case KATAKANA:kana=true;break;
    default:return Route.KEEP;
   }
  }
  if(kana)return Route.JAPANESE;
  // Chinese with an acronym/name is already readable. Sending it to EN loses Han characters.
  if(han)return Route.KEEP;
  return latin?Route.ENGLISH:Route.KEEP;
 }
 private SmallModelPolicy(){}
}
