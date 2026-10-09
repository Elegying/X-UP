package android.content;import java.util.*;public class Intent {
 private final Map<String,String> values=new HashMap<>();private String action;public android.net.Uri data;
 public Intent(){}public Intent(Context c,Class<?> target){}public Intent putExtra(String key,String value){values.put(key,value);return this;}
 public String getStringExtra(String key){return values.get(key);}public Intent setAction(String action){this.action=action;return this;}public String getAction(){return action;}
 public Intent setData(android.net.Uri value){data=value;return this;}
}
