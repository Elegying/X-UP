package io.github.jared.xlowerseek;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Executes the production provider, Activity, task bridge and transfer service without external I/O. */
public final class DownloadIntegrationTest {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("download integration case "+checks);}
    static final String URL="https://video.twimg.com/test.mp4";
    static final byte[] MP4={0,0,0,16,'f','t','y','p','i','s','o','m',0,0,0,0};
    static String mode="ok";
    static volatile Connection connection;
    static final class Connection extends HttpURLConnection {
        final String behavior;final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        volatile boolean disconnected;
        Connection(URL u){super(u);behavior=mode;}
        public void connect(){}public boolean usingProxy(){return false;}
        public void disconnect(){disconnected=true;release.countDown();}
        public int getResponseCode(){return behavior.equals("redirect")?302:200;}
        public long getContentLengthLong(){return behavior.equals("oversize")?3L*1024*1024*1024:MP4.length;}
        public String getHeaderField(String key){return behavior.equals("redirect")?"https://untrusted.test/escape.mp4":null;}
        public InputStream getInputStream(){return new ByteArrayInputStream(MP4){
            @Override public synchronized int read(byte[] b,int off,int len){
                if(behavior.equals("block")){
                    entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new AssertionError("cancel did not disconnect");}catch(InterruptedException e){throw new AssertionError(e);}
                    return -1;
                }
                if(behavior.equals("truncated")&&pos>=12)return -1;
                return super.read(b,off,len);
            }
        };}
    }
    static final class Media extends ContentResolver {
        final Map<String,Integer> rows=new ConcurrentHashMap<>();int sequence,deleted,published;boolean noSpace;
        LocalTranslationProvider provider;
        public Bundle call(Uri uri,String method,String arg,Bundle extras){return provider.call(method,arg,extras);}
        public Uri insert(Uri uri,ContentValues values){String id="content://media/external_primary/video/media/"+(++sequence);rows.put(id,1);return Uri.parse(id);}
        public OutputStream openOutputStream(Uri uri,String mode){return new ByteArrayOutputStream(){public synchronized void write(byte[] b,int o,int n){if(noSpace)throw new IllegalStateException("disk full");super.write(b,o,n);}};}
        public Cursor query(Uri uri,String[] p,String s,String[] a,String o){Integer value=rows.get(uri.toString());return new Cursor(){public boolean moveToFirst(){return value!=null;}public int getInt(int i){return value;}public void close(){}};}
        public int update(Uri uri,ContentValues values,String s,String[] a){rows.put(uri.toString(),0);published++;return 1;}
        public int delete(Uri uri,String s,String[] a){if(rows.remove(uri.toString())!=null){deleted++;return 1;}return 0;}
    }
    static final Context context=new Context();
    static final LocalTranslationProvider provider=new LocalTranslationProvider();
    static final Media media=new Media();
    static Bundle call(String method,String arg){return provider.call(method,arg,null);}
    static DownloadActivity entry(Bundle offer){DownloadActivity a=new DownloadActivity();a.intent.putExtra("id",offer.getString("id")).putExtra("capability",offer.getString("capability")).putExtra("url","https://untrusted.test/forged.mp4");a.onResume();return a;}
    static DownloadService service(Intent intent){DownloadService s=new DownloadService();s.resolver=media;s.onCreate();s.onStartCommand(intent,0,1);return s;}
    static void await(BooleanSupplier done)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(!done.getAsBoolean()&&System.nanoTime()<end){Handler.drain();Thread.sleep(2);}Handler.drain();check(done.getAsBoolean());}
    static boolean active(){return VideoDownloadState.active(call("video.status",null).getString("state"));}
    static void resetProcess()throws Exception {for(String name:new String[]{"state","cancellation","persistedId","persistedState"}){var field=VideoDownloads.class.getDeclaredField(name);field.setAccessible(true);field.set(null,null);}}
    public static void main(String[] args)throws Exception {
        java.net.URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){connection=new Connection(u);return connection;}}:null);
        provider.context=context;media.provider=provider;context.resolver=media;SystemClock.now=1000;
        Binder.uid=222;
        for(String method:new String[]{"video.prepare","video.status","video.cancel"})try{call(method,URL);throw new AssertionError("untrusted caller");}catch(SecurityException expected){check(true);}
        Binder.uid=111;
        DownloadActivity bare=new DownloadActivity();bare.intent.putExtra("url",URL);bare.onResume();check(bare.launched==null);
        Bundle offer=call("video.prepare",URL);check(offer.getString("capability")!=null);
        check(call("video.status",null).getString("capability")==null);
        Bundle duplicate=call("video.prepare",URL);check(duplicate.getString("capability")==null);check(duplicate.getString("id").equals(offer.getString("id")));
        Bundle forged=new Bundle();forged.putString("id",offer.getString("id"));forged.putString("capability","forged");check(entry(forged).launched==null);
        DownloadActivity a=entry(offer);check(a.launched!=null);check(a.launched.getStringExtra("url").equals(URL));check(entry(offer).launched==null);
        DownloadService s=service(a.launched);await(()->!active());check(media.published==1);check(connection.disconnected);check(context.getSharedPreferences("download",0).getString("pending",null)==null);s.onDestroy();
        resetProcess();check(call("video.status",null).getString("state").equals(VideoDownloadState.SUCCEEDED));
        offer=call("video.prepare",URL);a=entry(offer);resetProcess();check(!active());check(call("video.status",null).getString("message").contains("中断"));
        s=service(a.launched);check(s.stopped);s.onDestroy();
        offer=call("video.prepare",URL);DownloadActivity rejected=new DownloadActivity();rejected.rejectLaunch=true;rejected.intent.putExtra("id",offer.getString("id")).putExtra("capability",offer.getString("capability"));rejected.onResume();check(!active());
        offer=call("video.prepare",URL);SystemClock.now+=30000;check(entry(offer).launched==null);check(!active());
        // A published row from an interrupted cleanup must never be deleted on the next run.
        media.rows.put("content://media/old",0);context.getSharedPreferences("download",0).edit().putString("pending","content://media/old").commit();
        for(String failure:new String[]{"truncated","oversize","redirect","disk"}){
            mode=failure;media.noSpace=failure.equals("disk");offer=call("video.prepare",URL);s=service(entry(offer).launched);await(()->!active());
            check(call("video.status",null).getString("state").equals(VideoDownloadState.FAILED));check(media.published==1);check(media.rows.values().stream().noneMatch(v->v==1));check(media.rows.containsKey("content://media/old"));s.onDestroy();
        }
        media.noSpace=false;mode="block";offer=call("video.prepare",URL);s=service(entry(offer).launched);
        await(()->connection!=null&&connection.entered.getCount()==0);Intent oldCancel=PendingIntent.lastCancel;
        Binder.uid=42;call("video.cancel",offer.getString("id"));await(()->!active());check(media.rows.values().stream().noneMatch(v->v==1));check(call("video.status",null).getString("state").equals(VideoDownloadState.CANCELLED));s.onDestroy();
        mode="block";offer=call("video.prepare",URL);connection=null;s=service(entry(offer).launched);await(()->connection!=null&&connection.entered.getCount()==0);
        s.onStartCommand(oldCancel,0,2);check(active());check(!connection.disconnected);
        // onDestroy must disconnect and clean pending content even without a notification action.
        s.onDestroy();await(()->!active());check(connection.disconnected);check(media.rows.values().stream().noneMatch(v->v==1));
        check(Context.disk.values().stream().flatMap(v->v.values().stream()).noneMatch(v->v.contains("video.twimg.com")));
        System.out.println("DownloadIntegration: "+checks+" assertions passed (production IPC, Activity, service; fake network/MediaStore)");
    }
}
