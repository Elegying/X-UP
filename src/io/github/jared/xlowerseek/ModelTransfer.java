package io.github.jared.xlowerseek;
import java.io.*;import java.net.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Resumable fixed artifact transfer. Only a verified complete file becomes active. */
final class ModelTransfer {
 interface Progress {void update(long bytes,boolean verifying);}
 interface Connections {HttpURLConnection open(URL url)throws IOException;}
 static final class Paused extends IOException {Paused(){super("下载已暂停，可继续下载。");}}
 static void download(URL url,File target,long size,String digest,AtomicBoolean pause,Progress progress)throws Exception{
  download(url,target,size,digest,pause,progress,u->(HttpURLConnection)u.openConnection());
 }
 static void download(URL url,File target,long size,String digest,AtomicBoolean pause,Progress progress,Connections connections)throws Exception{
  if(size<=0||size>LocalModelSpec.MAX_BYTES||!digest.matches("[0-9a-f]{64}"))throw new IOException("模型超出允许大小或校验信息无效");
  File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建模型目录");
  if(target.isFile()&&target.length()==size){progress.update(size,true);if(verify(target,digest,pause)){progress.update(size,false);return;}}
  File partial=new File(target.getPath()+".part");if(partial.length()>size&&!partial.delete())throw new IOException("无法清理不完整模型");
  long offset=partial.length();if(parent.getUsableSpace()<size-offset+32*1024*1024L)throw new IOException("存储空间不足，请至少留出 500 MB 可用空间");
  if(offset<size){
   HttpURLConnection c=connect(url,offset,connections);
   try{
    int status=c.getResponseCode();
    if(status==200)offset=0;
    else if(status!=206)throw new IOException("模型下载失败（HTTP "+status+"），请检查网络后重试");
    if(status==206){String range=c.getHeaderField("Content-Range");if(range==null||!range.equals("bytes "+offset+"-"+(size-1)+"/"+size))throw new IOException("下载续传范围不正确");}
    long length=c.getContentLengthLong();if(length>=0&&length!=size-offset)throw new IOException("模型大小与发布版本不符");
    try(InputStream in=c.getInputStream();RandomAccessFile out=new RandomAccessFile(partial,"rw")){
     out.setLength(offset);out.seek(offset);byte[] bytes=new byte[128*1024];int n;long received=offset;
     while(true){if(pause.get())throw new Paused();n=in.read(bytes);if(n<0)break;if(received+n>size)throw new IOException("模型下载超过大小限制");out.write(bytes,0,n);received+=n;progress.update(received,false);}
     out.getFD().sync();if(received!=size)throw new IOException("下载中断，点击继续下载即可续传");
    }
   }finally{c.disconnect();}
  }
  progress.update(size,true);
  if(!verify(partial,digest,pause)){partial.delete();throw new IOException("模型完整性校验失败，请重新下载");}
  if(pause.get())throw new Paused();
  Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);progress.update(size,false);
 }
 private static HttpURLConnection connect(URL url,long offset,Connections connections)throws IOException{
  for(int i=0;i<6;i++){
   if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("模型下载只允许 HTTPS");
   HttpURLConnection c=connections.open(url);c.setConnectTimeout(12000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept-Encoding","identity");if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
   int status;try{status=c.getResponseCode();}catch(IOException e){c.disconnect();throw e;}
   if(status<300||status>=400)return c;
   String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("下载地址不可用");url=new URL(url,location);
  }
  throw new IOException("下载重定向过多，请稍后重试");
 }
 static boolean verify(File file,String expected,AtomicBoolean pause)throws Exception{
  MessageDigest sha=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1){if(pause.get())throw new Paused();sha.update(b,0,n);}}
  StringBuilder hex=new StringBuilder();for(byte b:sha.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString().equals(expected);
 }
}
