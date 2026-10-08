package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;import org.json.*;
public class UpdateReleaseTest {
 private JSONObject release(String version,boolean prerelease)throws Exception{
  String v=version.substring(1),name="X-UP-"+v+".apk";
  return new JSONObject().put("tag_name",version).put("prerelease",prerelease).put("body","更新说明").put("assets",new JSONArray().put(new JSONObject().put("name",name).put("state","uploaded").put("browser_download_url",UpdateRelease.REPO+"/releases/download/"+version+"/"+name).put("digest","sha256:"+"a".repeat(64)).put("size",63000000)));
 }
 @Test public void comparesNumericVersionsAndStableChannels(){assertTrue(UpdateRelease.compare("1.8.0-beta10","1.8.0-beta9")>0);assertTrue(UpdateRelease.compare("1.8.0","1.8.0-rc9")>0);assertTrue(UpdateRelease.compare("1.8.0-rc1","1.8.0-beta10")>0);assertTrue(UpdateRelease.compare("1.10.0","1.9.99")>0);assertEquals(0,UpdateRelease.compare("v1.8.0","1.8.0"));}
 @Test public void ignoresOrderingModelsAndDrafts()throws Exception{
  JSONArray a=new JSONArray().put(release("v1.8.0-beta6",true)).put(release("v1.8.0-beta10",true)).put(new JSONObject().put("tag_name","models-opus-v1")).put(release("v9.0.0",false).put("draft",true));
  assertEquals("1.8.0-beta10",UpdateRelease.newest(a.toString(),"1.8.0-beta5",true).version);
  assertNull(UpdateRelease.newest(a.toString(),"1.8.0-beta5",false));
 }
 @Test public void noDowngradeOrSameVersion()throws Exception{String a=new JSONArray().put(release("v1.8.0-beta5",true)).toString();assertNull(UpdateRelease.newest(a,"1.8.0-beta5",true));assertNull(UpdateRelease.newest(a,"1.8.0-beta6",true));}
 @Test public void stableSelectedAndPersistentRoundtrip()throws Exception{String a=new JSONArray().put(release("v1.8.0",false)).put(release("v1.9.0-beta1",true)).toString();UpdateRelease r=UpdateRelease.newest(a,"1.8.0-beta6",false);assertEquals("1.8.0",r.version);UpdateRelease p=UpdateRelease.decode(r.encode());assertEquals(r.url,p.url);assertEquals(r.sha,p.sha);}
 @Test public void rejectsMissingDigestForeignUrlAndOversize()throws Exception{
  for(String field:new String[]{"digest","browser_download_url","size"}){JSONObject r=release("v1.8.0-beta6",true);JSONObject asset=r.getJSONArray("assets").getJSONObject(0);asset.put(field,field.equals("size")?999999999:field.equals("digest")?"":"https://evil.example/update.apk");try{UpdateRelease.newest(new JSONArray().put(r).toString(),"1.8.0-beta5",true);fail(field);}catch(IllegalArgumentException expected){}}
 }
 @Test public void incompleteNewestDoesNotClaimNoUpdate()throws Exception{JSONArray a=new JSONArray().put(release("v1.8.0-beta6",true)).put(release("v1.8.0-beta7",true).put("assets",new JSONArray()));try{UpdateRelease.newest(a.toString(),"1.8.0-beta5",true);fail();}catch(IllegalArgumentException expected){}}
 @Test public void pendingTamperIsRejected()throws Exception{UpdateRelease r=UpdateRelease.newest(new JSONArray().put(release("v1.8.0-beta6",true)).toString(),"1.8.0-beta5",true);JSONObject o=new JSONObject(r.encode());o.put("url","file:///etc/passwd");try{UpdateRelease.decode(o.toString());fail();}catch(JSONException expected){}}
}
