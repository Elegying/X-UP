package io.github.jared.xlowerseek;

import android.content.Context;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Storage/installation boundary; the tested Service, transfer and SDK orchestration are real. */
final class LocalModels {
    static int changes;
    static boolean opusReady;
    static boolean ready(Context c,LocalEngine e){return e==LocalEngine.MLKIT?MlKitModels.ready:e==LocalEngine.OPUS&&opusReady;}
    static long downloadProgress(Context c,LocalEngine e){return 0;}
    static void changed(Context c){changes++;}
}
final class ModelFiles {
    static File model(Context c){throw new AssertionError("Tencent transfer is not part of the service lifecycle test");}
    static void activate(Context c){throw new AssertionError("Unexpected Tencent activation");}
}
final class OpusFiles {
    static File root;
    static final byte[] CONTENT="a verified test model file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static final long BYTES=CONTENT.length;
    static final String URL="https://model.test/opus";
    static final String SHA256="f23878922064d0611691f03051a1d29f526a8e8802e98a6daf2f19eb04704b84";
    static CountDownLatch entered,release;
    static File archive(Context c){return new File(root,"opus.zip");}
    static void install(Context c,AtomicBoolean pause)throws Exception{
        entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw new AssertionError("Installation was not released");
        if(pause.get())throw new ModelTransfer.Paused();LocalModels.opusReady=true;
    }
}
