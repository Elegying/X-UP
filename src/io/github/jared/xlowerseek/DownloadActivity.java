package io.github.jared.xlowerseek;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

public final class DownloadActivity extends Activity {
    private boolean started;
    @Override protected void onResume() {
        super.onResume();
        if(started)return;started=true;
        String url=getIntent().getStringExtra("url");
        ResultReceiver receiver=receiver(getIntent());
        if(!DownloadPolicy.validUrl(url)) { send(receiver,"当前视频没有可直接保存的 MP4 来源");finish();return; }
        Intent service=new Intent(this,DownloadService.class).putExtra("url",url).putExtra("receiver",receiver);
        try {startForegroundService(service);} catch(RuntimeException e) {send(receiver,"无法启动下载，请重试");}
        finish();
    }
    private static ResultReceiver receiver(Intent intent){
        try{Object value=intent.getParcelableExtra("receiver");return value instanceof ResultReceiver?(ResultReceiver)value:null;}
        catch(RuntimeException e){return null;}
    }
    private static void send(ResultReceiver r,String message){if(r!=null){Bundle b=new Bundle();b.putString("message",message);r.send(3,b);}}
}
