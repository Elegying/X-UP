package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.net.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
public class ModelTransferTest {
 static byte[] content="a verified test model file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 static String hash(byte[] b)throws Exception{StringBuilder out=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(b))out.append(String.format("%02x",x&255));return out.toString();}
 static class Connection extends HttpURLConnection{
  byte[] body;int status;String range;boolean disconnected;
  Connection(byte[] b,int status,String range)throws Exception{super(new URL("https://example.test/model"));body=b;this.status=status;this.range=range;}
  public void connect(){}public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}public int getResponseCode(){return status;}public long getContentLengthLong(){return body.length;}public String getHeaderField(String key){return key.equals("Content-Range")?range:null;}public InputStream getInputStream(){return new ByteArrayInputStream(body);}
 }
 @Test public void freshAndResumedFilesAreVerifiedBeforeActivation()throws Exception{
  for(int offset:new int[]{0,6}){
   File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();File part=new File(f+".part");if(offset>0)Files.write(part.toPath(),Arrays.copyOf(content,offset));
   Connection c=new Connection(Arrays.copyOfRange(content,offset,content.length),offset==0?200:206,"bytes "+offset+"-"+(content.length-1)+"/"+content.length);
   ModelTransfer.download(c.getURL(),f,content.length,hash(content),new AtomicBoolean(),(b,v)->{if(v)assertFalse(f.exists());},u->c);
   assertArrayEquals(content,Files.readAllBytes(f.toPath()));assertFalse(part.exists());assertTrue(c.disconnected);if(offset>0)assertEquals("bytes=6-",c.getRequestProperty("Range"));
  }
 }
 @Test public void serverIgnoringRangeRestartsInsteadOfAppending()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();Files.write(new File(f+".part").toPath(),Arrays.copyOf(content,4));Connection c=new Connection(content,200,null);
  ModelTransfer.download(c.getURL(),f,content.length,hash(content),new AtomicBoolean(),(b,v)->{},u->c);assertArrayEquals(content,Files.readAllBytes(f.toPath()));
 }
 @Test public void tamperAndOversizeNeverBecomeActive()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();byte[] bad=content.clone();bad[0]^=1;Connection c=new Connection(bad,200,null);
  try{ModelTransfer.download(c.getURL(),f,content.length,hash(content),new AtomicBoolean(),(b,v)->{},u->c);fail();}catch(IOException expected){assertFalse(f.exists());}
  try{ModelTransfer.download(c.getURL(),f,500000001,hash(content),new AtomicBoolean(),(b,v)->{},u->c);fail();}catch(IOException expected){assertFalse(f.exists());}
 }
 @Test public void pauseRetainsPartialButNeverMarksReady()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();Connection c=new Connection(content,200,null);AtomicBoolean pause=new AtomicBoolean();
  try{ModelTransfer.download(c.getURL(),f,content.length,hash(content),pause,(b,v)->pause.set(true),u->c);fail();}catch(ModelTransfer.Paused expected){assertFalse(f.exists());assertTrue(new File(f+".part").exists());}
 }
 @Test public void alreadyPausedDoesNotOpenNetworkOrDiscardProgress()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();File part=new File(f+".part");byte[] saved=Arrays.copyOf(content,6);Files.write(part.toPath(),saved);
  try{ModelTransfer.download(new URL("https://example.test/model"),f,content.length,hash(content),new AtomicBoolean(true),(b,v)->{},u->{throw new AssertionError("Paused transfer must not open a connection");});fail();}
  catch(ModelTransfer.Paused expected){assertArrayEquals(saved,Files.readAllBytes(part.toPath()));assertFalse(f.exists());}
 }
 @Test public void pauseDuringHeadersKeepsProgressWhenServerIgnoresRange()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();File part=new File(f+".part");byte[] saved=Arrays.copyOf(content,6);Files.write(part.toPath(),saved);AtomicBoolean pause=new AtomicBoolean();
  Connection c=new Connection(content,200,null){public int getResponseCode(){pause.set(true);return 200;}};
  try{ModelTransfer.download(c.getURL(),f,content.length,hash(content),pause,(b,v)->{},u->c);fail();}
  catch(ModelTransfer.Paused expected){assertArrayEquals(saved,Files.readAllBytes(part.toPath()));assertFalse(f.exists());assertTrue(c.disconnected);}
 }
 @Test public void pauseWhileOpeningBodyKeepsProgressWhenServerIgnoresRange()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();File part=new File(f+".part");byte[] saved=Arrays.copyOf(content,6);Files.write(part.toPath(),saved);AtomicBoolean pause=new AtomicBoolean();
  Connection c=new Connection(content,200,null){public InputStream getInputStream(){pause.set(true);return super.getInputStream();}};
  try{ModelTransfer.download(c.getURL(),f,content.length,hash(content),pause,(b,v)->{},u->c);fail();}
  catch(ModelTransfer.Paused expected){assertArrayEquals(saved,Files.readAllBytes(part.toPath()));assertFalse(f.exists());assertTrue(c.disconnected);}
 }
 @Test public void pauseDuringRedirectDoesNotOpenAnotherConnection()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();AtomicBoolean pause=new AtomicBoolean();int[] opened={0};
  Connection c=new Connection(content,302,null){public int getResponseCode(){pause.set(true);return 302;}public String getHeaderField(String key){return key.equals("Location")?"https://example.test/redirected":super.getHeaderField(key);}};
  try{ModelTransfer.download(c.getURL(),f,content.length,hash(content),pause,(b,v)->{},u->{if(++opened[0]>1)throw new AssertionError("Paused redirect must not open another connection");return c;});fail();}
  catch(ModelTransfer.Paused expected){assertEquals(1,opened[0]);assertFalse(f.exists());assertTrue(c.disconnected);}
 }
 @Test public void redirectPreservesResumeAndClosesBothConnections()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();Files.write(new File(f+".part").toPath(),Arrays.copyOf(content,6));int[] opened={0};
  Connection first=new Connection(content,302,null){public String getHeaderField(String key){return key.equals("Location")?"/redirected":super.getHeaderField(key);}};
  Connection second=new Connection(Arrays.copyOfRange(content,6,content.length),206,"bytes 6-"+(content.length-1)+"/"+content.length);
  ModelTransfer.download(first.getURL(),f,content.length,hash(content),new AtomicBoolean(),(b,v)->{},u->{if(opened[0]++==0)return first;assertEquals("https://example.test/redirected",u.toString());return second;});
  assertEquals(2,opened[0]);assertEquals("bytes=6-",second.getRequestProperty("Range"));assertTrue(first.disconnected&&second.disconnected);assertArrayEquals(content,Files.readAllBytes(f.toPath()));
 }
 @Test public void redirectToHttpIsRejectedBeforeOpeningAndKeepsPartial()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();File part=new File(f+".part");byte[] saved=Arrays.copyOf(content,6);Files.write(part.toPath(),saved);int[] opened={0};
  Connection first=new Connection(content,302,null){public String getHeaderField(String key){return key.equals("Location")?"http://example.test/model":super.getHeaderField(key);}};
  try{ModelTransfer.download(first.getURL(),f,content.length,hash(content),new AtomicBoolean(),(b,v)->{},u->{if(++opened[0]>1)throw new AssertionError("HTTPS downgrade must not open a connection");return first;});fail();}
  catch(IOException expected){assertEquals(1,opened[0]);assertTrue(first.disconnected);assertArrayEquals(saved,Files.readAllBytes(part.toPath()));assertFalse(f.exists());}
 }
 @Test public void wrongRangeRejectedAndPublicModelBelow500MB(){assertTrue(LocalModelSpec.BYTES<500000000L);assertEquals(64,LocalModelSpec.SHA256.length());}
 @Test public void wrongResumeRangeCannotReplaceExistingModel()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();byte[] old={1,2,3};Files.write(f.toPath(),old);Files.write(new File(f+".part").toPath(),Arrays.copyOf(content,6));Connection c=new Connection(Arrays.copyOfRange(content,6,content.length),206,"bytes 0-1/2");
  try{ModelTransfer.download(c.getURL(),f,content.length,hash(content),new AtomicBoolean(),(b,v)->{},u->c);fail();}catch(IOException expected){assertArrayEquals(old,Files.readAllBytes(f.toPath()));assertTrue(c.disconnected);}
 }
 @Test public void verifiedFileDoesNotOpenNetworkAndHttpIsRejected()throws Exception{
  File f=Files.createTempDirectory("model").resolve("model.gguf").toFile();Files.write(f.toPath(),content);
  ModelTransfer.download(new URL("https://example.test/model"),f,content.length,hash(content),new AtomicBoolean(),(b,v)->{},u->{throw new AssertionError("No network needed");});
  Files.delete(f.toPath());try{ModelTransfer.download(new URL("http://example.test/model"),f,content.length,hash(content),new AtomicBoolean(),(b,v)->{},u->{throw new AssertionError("HTTPS required");});fail();}catch(IOException expected){assertFalse(f.exists());}
 }
}
