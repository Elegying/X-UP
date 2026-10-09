package android.os;
import java.util.*;
public final class Handler {
 private static final class Job {Handler owner;Runnable action;long at;Job(Handler o,Runnable a,long t){owner=o;action=a;at=t;}}
 private static final List<Job> jobs=new ArrayList<>();
 public Handler(Looper looper){}
 public boolean post(Runnable r){return postDelayed(r,0);}
 public boolean postDelayed(Runnable r,long delay){synchronized(jobs){jobs.add(new Job(this,r,SystemClock.now+delay));}return true;}
 public void removeCallbacks(Runnable r){synchronized(jobs){jobs.removeIf(j->j.owner==this&&j.action==r);}}
 public void removeCallbacksAndMessages(Object ignored){synchronized(jobs){jobs.removeIf(j->j.owner==this);}}
 public static boolean drainOne(){Job next=null;synchronized(jobs){for(Job j:jobs)if(j.at<=SystemClock.now){next=j;break;}if(next!=null)jobs.remove(next);}if(next==null)return false;next.action.run();return true;}
 public static void drain(){while(drainOne()){} }
 public static void advance(long ms){SystemClock.now+=ms;drain();}
 public static int queued(){synchronized(jobs){return jobs.size();}}
}
