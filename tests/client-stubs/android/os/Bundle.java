package android.os;
public final class Bundle extends java.util.HashMap<String,Object>{
 public void putString(String key,String value){put(key,value);} public String getString(String key){return (String)get(key);}
 public String getString(String key,String fallback){String s=getString(key);return s==null?fallback:s;}
 public void putInt(String key,int value){put(key,value);}public int getInt(String key,int fallback){Object v=get(key);return v==null?fallback:(Integer)v;}
 public void putLong(String key,long value){put(key,value);}
 public void putBoolean(String k,boolean v){put(k,v);}
 public boolean getBoolean(String k,boolean fallback){Object v=get(k);return v==null?fallback:(Boolean)v;}public long getLong(String k,long fallback){Object v=get(k);return v==null?fallback:(Long)v;}
 @SuppressWarnings("unchecked") public <T> T getParcelable(String k){return (T)get(k);}public <T> T getParcelable(String k,Class<T> type){return type.cast(get(k));}
 public void putParcelable(String key,Object value){put(key,value);}
}
