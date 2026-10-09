package io.github.jared.xlowerseek;

import android.content.SharedPreferences;
import android.util.Log;

/** API 102 remote preferences, read-only inside X and updated by framework notifications. */
final class RemoteSettings {
    volatile int seconds=10;
    volatile String engine=LocalEngine.DEFAULT.id;
    volatile boolean latest=true;
    volatile boolean translate=true;
    volatile long localRevision,retry;
    private volatile java.util.Set<Feature> features=defaultFeatures();
    private static java.util.Set<Feature> defaultFeatures(){java.util.Set<Feature> result=java.util.EnumSet.allOf(Feature.class);result.remove(Feature.NATIVE_TRANSLATE);return result;}
    boolean feature(Feature f){return features.contains(f);}
    private final java.util.List<Runnable> observers=new java.util.concurrent.CopyOnWriteArrayList<>();
    void addListener(Runnable observer){observers.add(observer);}
    private final SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->refresh();
    RemoteSettings(SharedPreferences prefs){this.prefs=prefs;prefs.registerOnSharedPreferenceChangeListener(listener);refresh();}
    synchronized void refresh(){
        try{
            int next=SettingsStore.normalize(prefs.getInt(SettingsStore.SECONDS,10));
            boolean nextLatest=prefs.getBoolean(SettingsStore.LATEST,true);
            boolean nextTranslate=prefs.getBoolean(SettingsStore.TRANSLATE,true);
            if(next!=seconds || nextLatest!=latest || nextTranslate!=translate) Log.i("XLowerSeek","settings seconds="+next+" latest="+nextLatest+" translate="+nextTranslate);
            long nextRevision=prefs.getLong("local_revision",0);
            long nextRetry=prefs.getLong("translation_retry",0);
            int mode=TranslationMode.normalize(prefs.getInt(TranslationMode.KEY,TranslationMode.LOCAL));
            java.util.Set<Feature> nextFeatures=java.util.EnumSet.noneOf(Feature.class);for(Feature f:Feature.values())if(f==Feature.NATIVE_TRANSLATE||f==Feature.LOCAL_TEXT?TranslationMode.enabled(mode,f):prefs.getBoolean(f.key,true))nextFeatures.add(f);
            String nextEngine=LocalEngine.parse(prefs.getString(LocalEngine.KEY,LocalEngine.DEFAULT.id)).id;
            boolean changed=!engine.equals(nextEngine)||!features.equals(nextFeatures)||nextLatest!=latest||next!=seconds||nextRetry!=retry||nextTranslate!=translate||nextRevision!=localRevision;
            for(Feature f:Feature.values())if(features.contains(f)!=nextFeatures.contains(f))Log.i("XLowerSeek","feature "+f.key+"="+nextFeatures.contains(f));
            engine=nextEngine;features=nextFeatures;retry=nextRetry;localRevision=nextRevision;
            seconds=next;latest=nextLatest;translate=nextTranslate;
            if(changed)for(Runnable observer:observers)observer.run();
        }catch(RuntimeException e){Log.w("XLowerSeek","Settings unavailable; retaining last values");}
    }
}
