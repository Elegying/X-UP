package io.github.jared.xlowerseek;
import java.nio.charset.StandardCharsets;
/** Native inference is serialized by LocalTranslationService and runs only in the module process. */
final class NativeTranslator implements AutoCloseable {
 static {System.loadLibrary("xup_translation");}
 private long handle;
 NativeTranslator(String path){handle=open(path);if(handle==0)throw new IllegalStateException("模型初始化失败");}
 String translate(String prompt){return translate(prompt,ticket());}
 synchronized long ticket(){return ticket(handle);}
 synchronized void cancel(){if(handle!=0)cancel(handle);}
 String translate(String prompt,long ticket){return new String(run(handle,prompt.getBytes(StandardCharsets.UTF_8),ticket),StandardCharsets.UTF_8).trim();}
 public synchronized void close(){if(handle!=0){close(handle);handle=0;}}
 private static native long open(String path);
 private static native byte[] run(long handle,byte[] prompt,long ticket);
 private static native long ticket(long handle);
 private static native void cancel(long handle);
 private static native void close(long handle);
}
