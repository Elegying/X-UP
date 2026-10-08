package io.github.jared.xlowerseek;
public final class TranslationGateTest {
 static int count;
 static void check(boolean v){count++;if(!v)throw new AssertionError("case "+count);}
 public static void main(String[] args){
  TranslationGate g=new TranslationGate();check(g.observe(true,true));check(!g.observe(true,true));check(g.claim());check(!g.claim());check(!g.observe(true,true));
  g=new TranslationGate();check(g.observe(true,true));check(!g.observe(false,true));check(!g.claim());check(!g.observe(true,true));
  g=new TranslationGate();check(!g.observe(true,false));check(g.observe(true,true));g.defer();check(!g.claim());check(g.observe(true,true));check(g.claim());
  g=new TranslationGate();check(!g.observe(false,true));check(!g.observe(true,true));
  // Temporarily hidden idle state can later become available.
  g=new TranslationGate();check(!g.observe(false,true,false));check(g.observe(true,true,false));check(g.claim());
  // Disabling while queued cancels it, then enabling allows one new request.
  g=new TranslationGate();check(g.observe(true,true,false));check(!g.observe(true,false,false));check(!g.claim());check(g.observe(true,true,false));check(g.claim());
  // A manual/native translation wins over the pending automatic action.
  g=new TranslationGate();check(g.observe(true,true,false));check(!g.observe(false,true,true));check(!g.claim());check(!g.observe(true,true,false));
  System.out.println("TranslationGate: "+count+" assertions passed");
 }
}
