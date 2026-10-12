package io.github.jared.xlowerseek;

import android.content.Intent;
import android.os.Handler;
import com.google.mlkit.common.model.RemoteModelManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.*;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Production service lifecycle with deterministic SDK tasks and bounded fake HTTPS. */
public final class ModelDownloadServiceTest {
    private static final RemoteModelManager sdk=RemoteModelManager.getInstance();
    private static int checks,failures;
    private interface Case {void run()throws Exception;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void finishSdk(){for(var task:sdk.downloads)if(!task.isComplete())task.succeed(null);Handler.drain();}
    private static void await(BooleanSupplier done)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!done.getAsBoolean()&&System.nanoTime()<end){Handler.drain();Thread.sleep(2);}
        Handler.drain();check(done.getAsBoolean(),"Worker did not reach expected state");
    }
    private static ModelDownloadService start(String engine){
        ModelDownloadService service=new ModelDownloadService();service.onCreate();service.onStartCommand(new Intent().putExtra("engine",engine),0,1);return service;
    }
    private static void test(String name,Case action)throws Exception{
        finishSdk();sdk.reset();ModelDownloadService.running=false;MlKitModels.ready=MlKitModels.downloading=false;
        LocalModels.opusReady=false;OpusFiles.root=Files.createTempDirectory("model-service").toFile();OpusFiles.entered=new CountDownLatch(1);OpusFiles.release=new CountDownLatch(1);
        try{action.run();System.out.println("PASS "+name);}catch(AssertionError failure){failures++;System.out.println("FAIL "+name+": "+failure.getMessage());}
        finally{OpusFiles.release.countDown();finishSdk();}
    }
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url){return new HttpURLConnection(url){
            public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
            public int getResponseCode(){return 200;}public long getContentLengthLong(){return OpusFiles.BYTES;}
            public InputStream getInputStream(){return new ByteArrayInputStream(OpusFiles.CONTENT);}
        };}}:null);
        test("destroyed Google service releases the foreground download slot",()->{
            ModelDownloadService service=start("mlkit");check(ModelDownloadService.running&&sdk.downloads.size()==2,"Google task started once");
            service.onDestroy();check(!ModelDownloadService.running,"Destroyed Google service blocks every future model download");
            check(MlKitModels.downloading,"SDK task remains active without falsely reporting pause");
        });
        test("foreground service recreation attaches to existing SDK downloads",()->{
            ModelDownloadService old=start("mlkit");old.onDestroy();ModelDownloadService.running=false;
            ModelDownloadService current=start("mlkit");check(sdk.downloads.size()==2,"Reattach must reuse both SDK tasks");
            finishSdk();check(current.stopped,"Recreated service never receives SDK completion");current.onDestroy();
            check(!ModelDownloadService.running&&MlKitModels.ready,"Completion releases the slot and publishes the ready model");
        });
        test("late Google callback cannot replace a new OPUS installation status",()->{
            ModelDownloadService old=start("mlkit");old.onDestroy();ModelDownloadService.running=false;
            ModelDownloadService current=start("opus");await(()->OpusFiles.entered.getCount()==0);
            String installing=ModelDownloadService.status;check(installing.contains("OPUS"),"OPUS install is active");
            finishSdk();check(ModelDownloadService.status.equals(installing),"Late Google callback overwrote the OPUS installation status");
            check(ModelDownloadService.running&&!current.stopped,"Old SDK completion stopped the new service");
            OpusFiles.release.countDown();await(()->current.stopped);current.onDestroy();
            check(LocalModels.opusReady&&!ModelDownloadService.running,"OPUS can complete after Google finishes");
        });
        if(failures>0)throw new AssertionError("ModelDownloadService: "+failures+" failed cases");
        System.out.println("ModelDownloadService: "+checks+" assertions passed (SDK completion, service destruction/recreation, engine switch)");
    }
}
