package io.github.jared.xlowerseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.util.Properties;
import io.github.libxposed.service.*;

/** Local durable preferences plus API 102 remote preferences; no world-readable files. */
public final class SettingsStore {
    static final String SECONDS="seconds",LATEST="latest_replies",TRANSLATE="auto_translate";
    private static final String DIRTY="pending_sync",SCHEMA="schema";
    private static SharedPreferences local,remote;
    private static Context application;
    private static XposedService boundService;
    private static Runnable observer;
    private static final Handler main=new Handler(Looper.getMainLooper());
    static synchronized void initialize(Context context){
        if(local!=null)return;
        application=context.getApplicationContext();
        local=context.getSharedPreferences("settings_local",Context.MODE_PRIVATE);
        if(!local.contains(SCHEMA)){
            Properties old=new Properties();
            try(InputStream in=context.openFileInput("settings_snapshot.properties")){old.load(in);}catch(IOException ignored){}
            int seconds=10;try{seconds=normalize(Integer.parseInt(old.getProperty(SECONDS,"10")));}catch(NumberFormatException ignored){}
            local.edit().putInt(SECONDS,seconds).putBoolean(LATEST,Boolean.parseBoolean(old.getProperty(LATEST,"true")))
                .putBoolean(TRANSLATE,Boolean.parseBoolean(old.getProperty(TRANSLATE,"true"))).putInt(SCHEMA,1).commit();
        }
        if(!local.contains(TranslationMode.KEY))local.edit().putInt(TranslationMode.KEY,TranslationMode.LOCAL).putBoolean(DIRTY,true).commit();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener(){
            public void onServiceBind(XposedService service){main.post(()->connect(service));}
            public void onServiceDied(XposedService service){main.post(()->{synchronized(SettingsStore.class){if(boundService==service){remote=null;boundService=null;changed();}}});}
        });
    }
    private static synchronized void connect(XposedService service){
        try{
            if(service.getApiVersion()<102)return;
            SharedPreferences target=service.getRemotePreferences("settings");
            if(!target.contains(SCHEMA)||local.getBoolean(DIRTY,false)){
                if(!copyFeatures(local,target)||!write(target,local.getInt(SECONDS,10),local.getBoolean(LATEST,true),local.getBoolean(TRANSLATE,true),false))throw new IllegalStateException("Remote commit failed");
            }
            if(!copyFeatures(target,local)||!write(local,target.getInt(SECONDS,10),target.getBoolean(LATEST,true),target.getBoolean(TRANSLATE,true),false))throw new IllegalStateException("Local commit failed");
            boundService=service;remote=target;publishModelState(application);
            android.util.Log.i("XLowerSeek","API 102 settings connected");
        }catch(RuntimeException e){remote=null;boundService=null;android.util.Log.w("XLowerSeek","Settings service unavailable; local changes retained");}
        changed();
    }
    static synchronized boolean isShared(){return remote!=null;}
    static synchronized void observe(Runnable callback){observer=callback;}
    private static void changed(){main.post(()->LocalTranslationService.settingsChanged(application));Runnable callback=observer;if(callback!=null)main.post(callback);}
    private static SharedPreferences prefs(Context c){if(local==null)initialize(c.getApplicationContext());return local;}
    public static synchronized int seconds(Context c){return normalize(prefs(c).getInt(SECONDS,10));}
    public static int normalize(int seconds){return seconds==5?5:10;}
    public static synchronized boolean latest(Context c){return prefs(c).getBoolean(LATEST,true);}
    public static synchronized boolean translate(Context c){return prefs(c).getBoolean(TRANSLATE,true);}
    private static boolean write(SharedPreferences p,int seconds,boolean latest,boolean translate,boolean dirty){
        return p.edit().putInt(SECONDS,normalize(seconds)).putBoolean(LATEST,latest).putBoolean(TRANSLATE,translate).putInt(SCHEMA,1).putBoolean(DIRTY,dirty).commit();
    }
    static synchronized void publishModelState(Context context){
        prefs(context);
        if(remote!=null)try{if(!remote.edit().remove("llm_enabled").remove("llm_other").remove("llm_revision").remove("fast_translation").remove("review_translation").putLong("local_revision",LocalModels.revision(context)).commit())throw new IllegalStateException("Sync failed");}catch(RuntimeException ignored){remote=null;}
        changed();
    }
    public static synchronized boolean feature(Context context,Feature feature){SharedPreferences p=prefs(context);if(feature==Feature.NATIVE_TRANSLATE||feature==Feature.LOCAL_TEXT)return TranslationMode.enabled(p.getInt(TranslationMode.KEY,TranslationMode.LOCAL),feature);return p.getBoolean(feature.key,true);}
    static synchronized LocalEngine engine(Context c){return LocalEngine.parse(prefs(c).getString(LocalEngine.KEY,LocalEngine.TENCENT.id));}
    static synchronized boolean setEngine(Context c,LocalEngine engine){
        prefs(c);if(!local.edit().putString(LocalEngine.KEY,engine.id).putBoolean(DIRTY,true).commit())return false;
        if(remote!=null)try{if(!copyFeatures(local,remote))throw new IllegalStateException();local.edit().putBoolean(DIRTY,false).commit();}catch(RuntimeException e){remote=null;}
        changed();return true;
    }
    private static boolean copyFeatures(SharedPreferences from,SharedPreferences to){
        int mode=TranslationMode.normalize(from.getInt(TranslationMode.KEY,TranslationMode.LOCAL));
        SharedPreferences.Editor edit=to.edit().putInt(TranslationMode.KEY,mode).putString(LocalEngine.KEY,LocalEngine.parse(from.getString(LocalEngine.KEY,LocalEngine.TENCENT.id)).id);for(Feature f:Feature.values())edit.putBoolean(f.key,f==Feature.NATIVE_TRANSLATE||f==Feature.LOCAL_TEXT?TranslationMode.enabled(mode,f):from.getBoolean(f.key,true));return edit.commit();
    }
    static synchronized boolean setFeature(Context context,Feature feature,boolean enabled){
        prefs(context);SharedPreferences.Editor edit=local.edit().putBoolean(feature.key,enabled).putBoolean(DIRTY,true);
        if(feature==Feature.NATIVE_TRANSLATE||feature==Feature.LOCAL_TEXT)edit.putInt(TranslationMode.KEY,TranslationMode.switchTo(local.getInt(TranslationMode.KEY,TranslationMode.LOCAL),feature,enabled));
        if(!edit.commit())return false;
        if(remote!=null)try{if(!copyFeatures(local,remote))throw new IllegalStateException("Sync failed");local.edit().putBoolean(DIRTY,false).commit();}catch(RuntimeException e){remote=null;}
        changed();return true;
    }
    static synchronized boolean retryTranslations(Context context){
        prefs(context);
        if(remote==null)return false;
        try{return remote.edit().putLong("translation_retry",System.currentTimeMillis()).commit();}
        catch(RuntimeException e){remote=null;return false;}
    }
    public static synchronized boolean save(Context context,int seconds,boolean latest,boolean translate){
        prefs(context);
        // Persist an outbox first: framework failure never loses the user's choice.
        if(!write(local,seconds,latest,translate,true))return false;
        if(remote!=null)try{
            if(!write(remote,seconds,latest,translate,false))throw new IllegalStateException("Remote commit failed");
            local.edit().putBoolean(DIRTY,false).commit();
        }catch(RuntimeException e){remote=null;android.util.Log.w("XLowerSeek","Settings saved locally; waiting for service");}
        return true;
    }
}
