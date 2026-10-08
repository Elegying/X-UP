package io.github.jared.xlowerseek;
final class TencentTranslator implements OfflineEngine {
 private final NativeTranslator model;
 TencentTranslator(String path){model=new NativeTranslator(path);}
 public String translate(String text,String context,TranslationCancellation cancellation)throws Exception{long ticket=model.ticket();cancellation.check();String out=model.translate(LocalTranslationPrompt.build(text,context),ticket);cancellation.check();return out;}
 public void cancel(){model.cancel();}public void close(){model.close();}
}
