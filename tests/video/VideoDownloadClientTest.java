package io.github.jared.xlowerseek;

import android.os.*;
import java.util.*;
import java.util.concurrent.Executor;

public final class VideoDownloadClientTest {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("video client case "+checks);}
    static final String URL="https://video.twimg.com/v.mp4";
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> jobs=new ArrayDeque<>();
        public void execute(Runnable job){jobs.add(job);}
        void drain(){while(!jobs.isEmpty()){jobs.remove().run();Handler.drain();}}
    }
    static final class Server implements VideoDownloadClient.Transport {
        VideoDownloadState state=new VideoDownloadState();boolean unavailable;int prepared,queries;
        public Bundle call(String method,String arg){
            if(unavailable)throw new IllegalStateException();
            String capability=null;
            if(method.equals("video.prepare")){prepared++;capability=state.prepare(arg,SystemClock.now).capability;}
            else if(method.equals("video.cancel"))state.cancel(arg,SystemClock.now);
            else queries++;
            VideoDownloadState.Snapshot s=state.snapshot(SystemClock.now);Bundle b=new Bundle();
            b.putString("id",s.id);b.putString("state",s.state);b.putString("message",s.message);b.putInt("progress",s.progress);
            if(capability!=null)b.putString("capability",capability);return b;
        }
    }
    static final class Listener implements VideoDownloadClient.Listener {
        final Server server;boolean busy;int launches,completions;boolean launchFails;
        Listener(Server server){this.server=server;}
        public void changed(boolean b,int p,String m){busy=b;}
        public void completed(String message){completions++;}
        public void launch(String id,String capability){
            if(launchFails)throw new IllegalStateException();
            launches++;check(server.state.consume(id,capability,SystemClock.now).equals(URL));
            check(server.state.start(id,URL,SystemClock.now));
        }
    }
    public static void main(String[] args){
        Queue q=new Queue();Server s=new Server();Listener l=new Listener(s);
        VideoDownloadClient c=new VideoDownloadClient(s,q,new Handler(Looper.getMainLooper()),l);
        c.visible(true);q.drain();c.start(URL);c.start(URL);q.drain();check(l.launches==1);check(s.prepared==1);check(l.busy);
        String first=s.state.snapshot(0).id;s.state.progress(first,"下载中",10);
        Handler.advance(2000);q.drain();s.unavailable=true;Handler.advance(600000);q.drain();check(l.busy);check(l.launches==1);
        s.unavailable=false;s.state=new VideoDownloadState();s.state.restore(first,VideoDownloadState.RUNNING,"下载中");
        c.visible(false);c.visible(true);q.drain();check(!l.busy);check(l.completions==1);
        c.start(URL);q.drain();check(l.launches==2);String second=s.state.snapshot(SystemClock.now).id;
        c.cancel();q.drain();check(l.busy);check(VideoDownloadState.CANCELLING.equals(s.state.snapshot(SystemClock.now).state));
        s.state.finish(second,false,"已取消");Handler.advance(2000);q.drain();check(!l.busy);
        c.start(URL);c.visible(false);q.drain();check(l.launches==2);check(!l.busy);check(Handler.queued()==0);
        c.visible(true);q.drain();l.launchFails=true;c.start(URL);q.drain();check(!l.busy);
        l.launchFails=false;c.start(URL);q.drain();String third=s.state.snapshot(SystemClock.now).id;
        s.state.finish(third,true,"已保存");Handler.advance(2000);q.drain();check(!l.busy);
        c.start(URL);Runnable pendingCancel=c.cancellation();pendingCancel.run();q.drain();check(!l.busy);
        c.start(URL);q.drain();Runnable staleDialog=c.cancellation();String previous=s.state.snapshot(SystemClock.now).id;
        s.state.finish(previous,true,"已保存");Handler.advance(2000);q.drain();c.start(URL);q.drain();
        staleDialog.run();q.drain();check(l.busy);check(VideoDownloadState.RUNNING.equals(s.state.snapshot(SystemClock.now).state));
        c.cancel();q.drain();s.state.finish(s.state.snapshot(SystemClock.now).id,false,"已取消");Handler.advance(2000);q.drain();
        int idleQueries=s.queries;Handler.advance(60000);q.drain();check(s.queries==idleQueries);check(Handler.queued()==0);
        int messages=l.completions;s.unavailable=true;c.start(URL);q.drain();check(!l.busy);check(l.completions==messages+1);s.unavailable=false;
        int count=s.queries;c.visible(false);Handler.advance(600000);q.drain();check(s.queries==count);check(Handler.queued()==0);
        System.out.println("VideoDownloadClient: "+checks+" assertions passed (lost transport, restart, cancellation, background, duplicate submit)");
    }
}
