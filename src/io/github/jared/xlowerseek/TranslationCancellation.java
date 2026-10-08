package io.github.jared.xlowerseek;
import java.util.concurrent.atomic.AtomicBoolean;
final class TranslationCancellation {
 private final AtomicBoolean cancelled=new AtomicBoolean();
 void cancel(){cancelled.set(true);}
 boolean cancelled(){return cancelled.get();}
 void check()throws InterruptedException{if(cancelled())throw new InterruptedException("翻译已取消");}
}
