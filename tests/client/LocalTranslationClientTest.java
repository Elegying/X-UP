package io.github.jared.xlowerseek;
import android.content.*;import android.os.*;import android.net.*;import java.util.*;import java.util.concurrent.*;
public final class LocalTranslationClientTest {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("client case "+checks);}
 static class Resolver extends ContentResolver{
  List<String> cancels=new CopyOnWriteArrayList<>();
  List<Bundle> requests=new CopyOnWriteArrayList<>();
  public Bundle call(Uri u,String method,String arg,Bundle b){if(method.equals("cancel")){cancels.add(arg);return new Bundle();}check(method.equals("translate"));requests.add(b);return new Bundle();}
  void await(int n)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(requests.size()<n&&System.nanoTime()<end)Thread.sleep(2);check(requests.size()==n);}
  void result(int i,int code,String text){Bundle b=new Bundle();if(text!=null)b.putString("text",text);((ResultReceiver)requests.get(i).get("receiver")).send(code,b);Handler.drain();}
 }
 public static void main(String[] args)throws Exception{
  Resolver resolver=new Resolver();RemoteSettings settings=new RemoteSettings();Context app=new Context(){public ContentResolver getContentResolver(){return resolver;}};LocalTranslationClient c=new LocalTranslationClient(app,settings);int[] changes={0};LocalTranslationClient.Listener l=()->changes[0]++;
  try{
   check(c.lookup("Hello",l).equals("Hello"));resolver.await(1);c.lookup("Hello",l);check(resolver.requests.size()==1);resolver.result(0,0,"你好");check(c.lookup("Hello",l).equals("你好"));check(changes[0]>0);
   c.lookup("Two",l);c.lookup("Three",l);resolver.await(3);c.lookup("Four",l);check(resolver.requests.size()==3);resolver.result(1,0,"二");c.lookup("Four",l);resolver.await(4);resolver.result(2,0,"三");resolver.result(3,0,"四");
   c.lookup("Same","Parent A",l);c.lookup("Same","Parent B",l);resolver.await(6);check(resolver.requests.get(4).getString("context").equals("Parent A"));resolver.result(4,0,"甲语境");resolver.result(5,0,"乙语境");check(c.lookup("Same","Parent A",l).equals("甲语境"));check(c.lookup("Same","Parent B",l).equals("乙语境"));
   c.lookup("Stale",l);resolver.await(7);c.clear();c.lookup("Stale",l);resolver.await(8);resolver.result(6,0,"旧");check(c.lookup("Stale",l).equals("Stale"));resolver.result(7,0,"新");check(c.lookup("Stale",l).equals("新"));
   c.lookup("Timeout",l);resolver.await(9);Handler.advance(45000);Handler.advance(1000);c.lookup("Timeout",l);resolver.await(10);resolver.result(8,0,"超时旧结果");check(c.lookup("Timeout",l).equals("Timeout"));resolver.result(9,0,"重试成功");check(c.lookup("Timeout",l).equals("重试成功"));
   c.setForeground(false);c.lookup("Background",l);check(resolver.requests.size()==10);c.setForeground(true);c.lookup("Background",l);resolver.await(11);int before=changes[0];c.setForeground(false);resolver.result(10,0,"后台完成");check(changes[0]==before);c.setForeground(true);check(c.lookup("Background",l).equals("后台完成"));check(resolver.requests.size()==11);
   c.lookup("Temporary failure",l);resolver.await(12);resolver.result(11,1,null);Handler.advance(1000);c.lookup("Temporary failure",l);resolver.await(13);resolver.result(12,1,null);Handler.advance(10000);c.lookup("Temporary failure",l);check(resolver.requests.size()==13);c.setForeground(false);c.setForeground(true);c.lookup("Temporary failure",l);resolver.await(14);resolver.result(13,0,"恢复成功");
   c.lookup("Model not ready",l);resolver.await(15);resolver.result(14,2,null);c.setForeground(false);c.setForeground(true);c.lookup("Model not ready",l);check(resolver.requests.size()==15);settings.localRevision++;c.clear();c.lookup("Model not ready",l);resolver.await(16);resolver.result(15,0,"下载后已可用");check(c.lookup("Model not ready",l).equals("下载后已可用"));
   c.lookup("Disabled",l);resolver.await(17);settings.translate=false;resolver.result(16,0,"不得显示");check(c.lookup("Disabled",l).equals("Disabled"));settings.translate=true;
   check(c.lookup("中文",l).equals("中文"));check(c.lookup("a".repeat(8193),l).length()==8193);
   c.clear();settings.engine="opus";c.lookup("Engine specific",l);resolver.await(18);check(resolver.requests.get(17).getString("engine").equals("opus"));resolver.result(17,0,"OPUS 译文");settings.engine="tencent";c.clear();c.lookup("Engine specific",l);resolver.await(19);check(c.lookup("Engine specific",l).equals("Engine specific"));resolver.result(18,0,"腾讯译文");
   c.lookup("Invisible work",l);resolver.await(20);String obsolete=resolver.requests.get(19).getString("id");Handler.advance(2501);c.pruneInvisible();long cancelEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!resolver.cancels.contains(obsolete)&&System.nanoTime()<cancelEnd)Thread.sleep(2);check(resolver.cancels.contains(obsolete));c.lookup("Invisible work",l);resolver.await(21);check(!obsolete.equals(resolver.requests.get(20).getString("id")));resolver.result(19,0,"过期译文");check(c.lookup("Invisible work",l).equals("Invisible work"));resolver.result(20,0,"当前译文");check(c.lookup("Invisible work",l).equals("当前译文"));
   c.clear();check(Handler.queued()==0);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(ContentProviderClient.active.get()>0&&System.nanoTime()<end)Thread.sleep(2);check(ContentProviderClient.active.get()==0);check(TranslationLease.active.get()==0);
   System.out.println("LocalTranslationClient: "+checks+" assertions passed (offline path, fake Android transport)");
  }finally{c.close();}
 }
}
