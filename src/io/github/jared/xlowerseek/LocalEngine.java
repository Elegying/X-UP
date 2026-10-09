package io.github.jared.xlowerseek;

/** Stable persisted identifiers: adding an engine does not rename existing model storage. */
enum LocalEngine {
 TENCENT("tencent", "腾讯 HY-MT2", "上下文优先 · 462 MB · 适合性能较好的手机"),
 MLKIT("mlkit", "Google ML Kit", "轻量优先 · 语言包按需下载 · 日语经英语中转"),
 OPUS("opus", "OPUS-MT", "专用小模型 · 英语直译，日语经英语中转");
 static final String KEY="local_engine";
 static final LocalEngine DEFAULT=MLKIT;
 final String id,title,description;
 LocalEngine(String id,String title,String description){this.id=id;this.title=title;this.description=description;}
 static LocalEngine parse(String id){for(LocalEngine e:values())if(e.id.equals(id))return e;return DEFAULT;}
}
