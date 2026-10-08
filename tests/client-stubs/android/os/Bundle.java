package android.os;
public final class Bundle extends java.util.HashMap<String,Object>{
 public void putString(String key,String value){put(key,value);} public String getString(String key){return (String)get(key);}
 public void putLong(String key,long value){put(key,value);}
 public void putParcelable(String key,Object value){put(key,value);}
}
