package io.github.jared.xlowerseek;
import java.io.*;import java.net.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Resumable fixed artifact transfer. Only a verified complete file becomes active. */
final class ModelTransfer {
 private static final int MAX_RANGE_RESPONSES=2048;
 interface Progress {void update(long bytes,boolean verifying);}
 interface Connections {HttpURLConnection open(URL url)throws IOException;}
 static final class Paused extends IOException {Paused(){super("下载已暂停，可继续下载。");}}
 static void download(URL url,File target,long size,String digest,AtomicBoolean pause,Progress progress)throws Exception{
  download(url,target,size,digest,pause,progress,u->(HttpURLConnection)u.openConnection());
 }
 static void download(URL url,File target,long size,String digest,AtomicBoolean pause,Progress progress,Connections connections)throws Exception{
  checkPaused(pause);
  if(size<=0||size>LocalModelSpec.MAX_BYTES||!digest.matches("[0-9a-f]{64}"))throw new IOException("模型超出允许大小或校验信息无效");
  File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建模型目录");
  if(target.isFile()&&target.length()==size){progress.update(size,true);if(verify(target,digest,pause)){progress.update(size,false);return;}}
  File partial=new File(target.getPath()+".part");if(partial.length()>size&&!partial.delete())throw new IOException("无法清理不完整模型");
  long offset=partial.length(),required=size-offset+32*1024*1024L;
  if(offset<size&&parent.getUsableSpace()<required)throw new IOException("存储空间不足，需要至少 "+((required+999999)/1000000)+" MB 可用空间");
  int responses=0;
  while(offset<size){
   checkPaused(pause);
   if(responses++>=MAX_RANGE_RESPONSES)throw new IOException("下载源连续返回过小分片，已保留进度，请稍后继续下载");
   HttpURLConnection c=connect(url,offset,pause,connections);
   try{
    int status=c.getResponseCode();checkPaused(pause);
    if(status==200)offset=0;
    else if(status!=206)throw new IOException("模型下载源 "+c.getURL().getHost()+" 返回 HTTP "+status+"，请稍后继续下载");
    // A server may deliver a shorter valid range. Continue from that range's end;
    // never append a mismatched start/total or publish an incomplete model.
    long end=status==206?rangeEnd(c.getHeaderField("Content-Range"),offset,size):size-1;
    long length=c.getContentLengthLong();if(length>=0&&length!=end-offset+1)throw new IOException("模型大小与发布版本不符");
    checkPaused(pause);
    try(InputStream in=c.getInputStream();RandomAccessFile out=new RandomAccessFile(partial,"rw")){
     // Opening the body can block; do not discard a resumable file if pause arrived meanwhile.
     checkPaused(pause);
     out.setLength(offset);out.seek(offset);byte[] bytes=new byte[128*1024];int n;long received=offset;
     while(true){if(pause.get())throw new Paused();n=in.read(bytes);if(n<0)break;if(received+n>end+1)throw new IOException("模型下载超过响应范围");out.write(bytes,0,n);received+=n;progress.update(received,false);}
     out.getFD().sync();if(received!=end+1)throw new IOException("下载中断，点击继续下载即可续传");
     offset=received;
    }
   }finally{c.disconnect();}
  }
  progress.update(size,true);
  if(!verify(partial,digest,pause)){partial.delete();throw new IOException("模型完整性校验失败，请重新下载");}
  if(pause.get())throw new Paused();
  Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);progress.update(size,false);
 }
 private static long rangeEnd(String value,long offset,long size)throws IOException{
  java.util.regex.Matcher range=java.util.regex.Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value==null?"":value);
  if(range.matches())try{
   long start=Long.parseLong(range.group(1)),end=Long.parseLong(range.group(2)),total=Long.parseLong(range.group(3));
   if(start==offset&&total==size&&end>=start&&end<size)return end;
  }catch(NumberFormatException ignored){}
  throw new IOException("下载源返回的续传范围不正确，已保留原有进度");
 }
 private static void checkPaused(AtomicBoolean pause)throws Paused{if(pause.get())throw new Paused();}
 private static HttpURLConnection connect(URL url,long offset,AtomicBoolean pause,Connections connections)throws IOException{
  for(int i=0;i<6;i++){
   checkPaused(pause);
   if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("模型下载只允许 HTTPS");
   HttpURLConnection c=connections.open(url);boolean retained=false;
   try{
    checkPaused(pause);
    c.setConnectTimeout(12000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept-Encoding","identity");if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
    int status=c.getResponseCode();checkPaused(pause);
    if(status<300||status>=400){retained=true;return c;}
    String location=c.getHeaderField("Location");checkPaused(pause);if(location==null)throw new IOException("下载地址不可用");url=new URL(url,location);
   }finally{if(!retained)c.disconnect();}
  }
  throw new IOException("下载重定向过多，请稍后重试");
 }
 static boolean verify(File file,String expected,AtomicBoolean pause)throws Exception{
  MessageDigest sha=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1){if(pause.get())throw new Paused();sha.update(b,0,n);}}
  StringBuilder hex=new StringBuilder();for(byte b:sha.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString().equals(expected);
 }
}
