package io.github.jared.xlowerseek;
final class SettingsStore {static volatile LocalEngine selected=LocalEngine.TENCENT;static volatile boolean enabled=true;static LocalEngine engine(android.content.Context c){return selected;}static boolean translate(android.content.Context c){return enabled;}static boolean feature(android.content.Context c,Feature f){return enabled;}}
