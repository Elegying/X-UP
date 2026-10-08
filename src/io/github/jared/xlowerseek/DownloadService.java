package io.github.jared.xlowerseek;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class DownloadService extends Service {
    private static final String CHANNEL="video_download";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean busy;
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    private ResultReceiver receiver;
    private String activeUrl;

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"视频保存",NotificationManager.IMPORTANCE_LOW));}
    private Notification notice(String text,int progress,boolean running){
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("X 视频保存").setContentText(text).setOnlyAlertOnce(true).setOngoing(running);
        if(running){
            b.setProgress(100,Math.max(0,progress),progress<0);
            PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,DownloadService.class).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null,"取消",cancel).build());
        }
        return b.build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf(startId);return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())){if(!busy){stopSelf(startId);return START_NOT_STICKY;}cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();return START_NOT_STICKY;}
        ResultReceiver incoming=receiver(intent);
        String url=intent.getStringExtra("url");
        if(busy){send(incoming,3,Objects.equals(activeUrl,url)?"这个视频正在下载，请稍候":"正在下载另一个视频，请完成后再试");return START_NOT_STICKY;}
        if(!DownloadPolicy.validUrl(url)){send(incoming,3,"视频来源无效");stopSelf(startId);return START_NOT_STICKY;}
        busy=true;cancelled=false;receiver=incoming;activeUrl=url;
        try{startForeground(42,notice("正在连接…",-1,true));}catch(RuntimeException e){busy=false;send(receiver,3,"系统暂不允许后台下载，请重试");stopSelf(startId);return START_NOT_STICKY;}send(receiver,1,"正在连接…");
        worker.execute(()->download(url));return START_NOT_STICKY;
    }
    private void download(String url){
        Uri pending=null;String result;boolean success=false;
        try{
            // Clean only this module's unfinished row from a previously interrupted download.
            String old=getSharedPreferences("download",0).getString("pending",null);
            if(old!=null){cleanupPending(Uri.parse(old));getSharedPreferences("download",0).edit().remove("pending").commit();}
            HttpURLConnection c=null;boolean connected=false;
            for(int redirects=0;redirects<5;redirects++){
                if(cancelled)throw new IOException("已取消下载");
                if(!DownloadPolicy.validUrl(url))throw new IOException("来源地址不受支持");
                c=(HttpURLConnection)new URL(url).openConnection();connection=c;
                c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(25000);
                c.setRequestProperty("User-Agent","X-UP/1.7.0");
                c.setRequestProperty("Accept-Encoding","identity");
                int status=c.getResponseCode();
                if(status>=300&&status<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("下载地址已失效");url=new URL(new URL(url),location).toString();continue;}
                if(status!=200)throw new IOException("下载失败（HTTP "+status+"）");connected=true;break;
            }
            if(c==null||!connected)throw new IOException("重定向次数过多");
            if(cancelled)throw new IOException("已取消下载");
            long total=c.getContentLengthLong();
            if(total>2L*1024*1024*1024)throw new IOException("视频超过 2 GB，暂不支持保存");
            String name="X_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+"_"+Integer.toHexString(url.hashCode())+".mp4";
            ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,name);
            values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/X");values.put(MediaStore.Video.Media.IS_PENDING,1);
            pending=getContentResolver().insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values);
            if(pending==null)throw new IOException("无法创建相册文件");
            if(!getSharedPreferences("download",0).edit().putString("pending",pending.toString()).commit())throw new IOException("无法记录下载状态，请重试");
            final long[] last={0,0};
            try(InputStream in=c.getInputStream();OutputStream out=getContentResolver().openOutputStream(pending,"w")){
                if(out==null)throw new IOException("无法写入相册");
                Mp4Transfer.copy(in,out,total,2L*1024*1024*1024,()->cancelled,(count,length)->{
                    long now=SystemClock.elapsedRealtime();
                    if(now-last[0]>=150){last[0]=now;int percent=DownloadProgress.percent(count,length);
                        String message=percent<0?String.format(Locale.US,"下载中 · %.1f MB",count/1048576.0):"下载中 "+percent+"%";send(receiver,1,message,percent);
                        if(now-last[1]>=700){last[1]=now;notifySafely(42,notice(message,percent,true));}}
                });
            }
            if(cancelled)throw new IOException("已取消下载");
            values.clear();values.put(MediaStore.Video.Media.IS_PENDING,0);
            if(getContentResolver().update(pending,values,null,null)!=1)throw new IOException("相册保存未完成");
            getSharedPreferences("download",0).edit().remove("pending").commit();
            pending=null;success=true;result="已保存到相册 · Movies/X";
        }catch(Exception e){result=cancelled?"已取消下载":("保存失败："+(e instanceof IOException?e.getMessage():"请检查网络或可用空间"));}
        finally{
            if(connection!=null){connection.disconnect();connection=null;}
            if(pending!=null)try{cleanupPending(pending);getSharedPreferences("download",0).edit().remove("pending").commit();}catch(Exception ignored){}
        }
        final String message=result;final boolean saved=success;
        new Handler(Looper.getMainLooper()).post(()->{
            send(receiver,saved?2:3,message);stopForeground(STOP_FOREGROUND_REMOVE);
            notifySafely(43,notice(message,100,false));
            busy=false;activeUrl=null;stopSelf();
        });
    }
    private void cleanupPending(Uri uri)throws IOException{
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{MediaStore.Video.Media.IS_PENDING},null,null,null)){
            if(c==null)throw new IOException("暂时无法清理上次下载，请重试");
            if(c.moveToFirst()&&c.getInt(0)==1&&getContentResolver().delete(uri,null,null)!=1)throw new IOException("暂时无法清理上次下载，请重试");
        }
    }
    private void notifySafely(int id,Notification n){try{getSystemService(NotificationManager.class).notify(id,n);}catch(RuntimeException ignored){}}
    private static ResultReceiver receiver(Intent intent){
        try{Object value=intent.getParcelableExtra("receiver");return value instanceof ResultReceiver?(ResultReceiver)value:null;}
        catch(RuntimeException e){return null;}
    }
    private static void send(ResultReceiver receiver,int code,String text){send(receiver,code,text,-1);}
    private static void send(ResultReceiver receiver,int code,String text,int progress){if(receiver!=null)try{Bundle b=new Bundle();b.putString("message",text);b.putInt("progress",progress);receiver.send(code,b);}catch(RuntimeException ignored){}}
    @Override public void onDestroy(){cancelled=true;if(connection!=null)connection.disconnect();worker.shutdownNow();super.onDestroy();}
    @Override public void onTimeout(int startId,int fgsType){cancelled=true;if(connection!=null)connection.disconnect();stopSelf();}
}
