package android.content;
import java.util.*;
public class Context {
 public static final Map<String,Map<String,String>> disk=new HashMap<>();
 public ContentResolver resolver;public Intent launched;public boolean rejectLaunch;
 public Context getApplicationContext(){return this;} public ContentResolver getContentResolver(){return resolver;}
 public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
 public SharedPreferences getSharedPreferences(String name,int mode){Map<String,String> values=disk.computeIfAbsent(name,k->new HashMap<>());return new SharedPreferences(){
  public String getString(String key,String fallback){return values.getOrDefault(key,fallback);}
  public Editor edit(){return new Editor(){final Map<String,String> changed=new HashMap<>();public Editor putString(String k,String v){changed.put(k,v);return this;}public Editor remove(String k){changed.put(k,null);return this;}public boolean commit(){changed.forEach((k,v)->{if(v==null)values.remove(k);else values.put(k,v);});return true;}public void apply(){commit();}};}};}
 public <T> T getSystemService(Class<T> cls){try{return cls.getConstructor().newInstance();}catch(Exception e){throw new RuntimeException(e);}}
 public ComponentName startForegroundService(Intent i){if(rejectLaunch)throw new IllegalStateException();launched=i;return null;}
}
