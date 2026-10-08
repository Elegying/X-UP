package io.github.jared.xlowerseek;
import java.nio.charset.StandardCharsets;
/** Native inference is serialized by LocalTranslationService and runs only in the module process. */
final class NativeTranslator implements AutoCloseable {
 static {System.loadLibrary("xup_translation");}
 private long handle;
 NativeTranslator(String path){handle=open(path);if(handle==0)throw new IllegalStateException("模型初始化失败");}
 String translate(String prompt){return new String(run(handle,prompt.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8).trim();}
 public void close(){if(handle!=0){close(handle);handle=0;}}
 private static native long open(String path);
 private static native byte[] run(long handle,byte[] prompt);
 private static native void close(long handle);
}
