package io.github.jared.xlowerseek;

import org.json.*;
import java.util.regex.*;

/** Release policy is independent of Android UI and transport. */
final class UpdateRelease {
 static final String REPO="https://github.com/Elegying/X-UP";
 private static final Pattern VERSION=Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-(alpha|beta|rc)(\\d+))?");
 final String version,url,sha,notes;final long size;
 UpdateRelease(String version,String url,String sha,long size,String notes){this.version=version;this.url=url;this.sha=sha;this.size=size;this.notes=notes;}
 static int compare(String a,String b){
  Matcher x=VERSION.matcher(a),y=VERSION.matcher(b);if(!x.matches()||!y.matches())throw new IllegalArgumentException("无法识别版本号");
  for(int i=1;i<=3;i++){int c=Long.compare(Long.parseLong(x.group(i)),Long.parseLong(y.group(i)));if(c!=0)return c;}
  int c=Integer.compare(rank(x.group(4)),rank(y.group(4)));if(c!=0)return c;
  return Long.compare(x.group(5)==null?0:Long.parseLong(x.group(5)),y.group(5)==null?0:Long.parseLong(y.group(5)));
 }
 private static int rank(String s){return s==null?4:s.equals("rc")?3:s.equals("beta")?2:1;}
 static UpdateRelease newest(String json,String installed,boolean beta)throws Exception{
  JSONArray all=new JSONArray(json);JSONObject newest=null;String version=installed;
  for(int i=0;i<all.length();i++){
   JSONObject r=all.getJSONObject(i);String tag=r.optString("tag_name");
   if(r.optBoolean("draft")||!tag.startsWith("v")||!VERSION.matcher(tag).matches()||(!beta&&(r.optBoolean("prerelease")||tag.contains("-"))))continue;
   if(compare(tag,version)>0){newest=r;version=tag;}
  }
  if(newest==null)return null;
  String v=version.startsWith("v")?version.substring(1):version,name="X-UP-"+v+".apk";
  JSONArray assets=newest.getJSONArray("assets");
  for(int i=0;i<assets.length();i++){
   JSONObject a=assets.getJSONObject(i);if(!name.equals(a.optString("name"))||!"uploaded".equals(a.optString("state")))continue;
   String url=a.getString("browser_download_url"),digest=a.optString("digest");long size=a.getLong("size");
   if(!url.equals(REPO+"/releases/download/"+version+"/"+name)||!digest.matches("sha256:[a-fA-F0-9]{64}")||size<=0||size>300_000_000L)throw new IllegalArgumentException("更新附件校验信息不完整，请稍后重试");
   return new UpdateRelease(v,url,digest.substring(7).toLowerCase(java.util.Locale.ROOT),size,newest.optString("body","暂无更新说明"));
  }
  throw new IllegalArgumentException("新版附件尚未就绪，请稍后重试");
 }
 String encode()throws JSONException{return new JSONObject().put("version",version).put("url",url).put("sha",sha).put("size",size).put("notes",notes).toString();}
 static UpdateRelease decode(String json)throws JSONException{
  JSONObject o=new JSONObject(json);String v=o.getString("version"),u=o.getString("url"),s=o.getString("sha");long n=o.getLong("size");
  if(!VERSION.matcher(v).matches()||!u.equals(REPO+"/releases/download/v"+v+"/X-UP-"+v+".apk")||!s.matches("[a-f0-9]{64}")||n<=0||n>300_000_000L)throw new JSONException("更新记录无效");
  return new UpdateRelease(v,u,s,n,o.optString("notes"));
 }
}
