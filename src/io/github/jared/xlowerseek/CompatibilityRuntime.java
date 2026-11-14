package io.github.jared.xlowerseek;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** One runtime report per feature and process, with a debounced, revisioned snapshot. */
final class CompatibilityRuntime {
    private final Context context;
    private final Bundle result;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.Set<String> reported=new java.util.HashSet<>();
    private final Runnable publish;
    CompatibilityRuntime(Context context,Bundle result){
        this.context=context;this.result=new Bundle(result);
        publish=()->{CompatibilityStatus.reporter(context,this.result).run();notifyPartial();};
    }
    void failed(String key,Throwable error){
        synchronized(reported){if(!CompatibilityStatus.FEATURES.containsKey(key)||!reported.add(key))return;}
        String kind=error.getClass().getSimpleName();
        main.post(()->{
            result.putBoolean("runtime."+key,true);
            result.putString("detail."+key,"运行回调异常（"+kind+"），该次调用已回退；同一入口累计异常 3 次会暂停拦截，重启 X 后重试");
            result.putLong("report_revision",result.getLong("report_revision",0)+1);
            main.removeCallbacks(publish);main.postDelayed(publish,1500);
        });
    }
    void notifyPartial(){
        StringBuilder fingerprint=new StringBuilder().append(result.getLong("x_version")).append(':').append(BuildConfig.VERSION_CODE);
        int failed=0;
        for(String key:CompatibilityStatus.FEATURES.keySet()){
            boolean problem=!result.getBoolean(key)||result.getBoolean("runtime."+key);
            fingerprint.append(problem?'1':'0');if(problem)failed++;
        }
        android.content.SharedPreferences prefs=context.getSharedPreferences("xup_compatibility_notice",Context.MODE_PRIVATE);
        String value=fingerprint.toString();
        if(value.equals(prefs.getString("last","")))return;
        prefs.edit().putString("last",value).apply();
        if(failed>0)main.post(()->Toast.makeText(context,"X-UP 部分功能需要适配，请打开插件的“状态与帮助”查看。",Toast.LENGTH_LONG).show());
    }
}
