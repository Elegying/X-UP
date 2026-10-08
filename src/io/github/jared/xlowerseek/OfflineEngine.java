package io.github.jared.xlowerseek;

/** Only the selected backend owns inference resources; all methods except cancel run on one worker. */
interface OfflineEngine extends AutoCloseable {
 String translate(String text,String context,TranslationCancellation cancellation)throws Exception;
 default void cancel(){}
 void close();
}
