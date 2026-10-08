package io.github.jared.xlowerseek;
import java.io.*;
/** Validates a complete MP4 transfer before the service publishes its MediaStore row. */
final class Mp4Transfer {
 interface Cancelled { boolean get(); }
 interface Progress { void update(long bytes,long total); }
 static long copy(InputStream in,OutputStream out,long total,long limit,Cancelled cancel,Progress progress)throws IOException{
  if(total>limit)throw new IOException("视频超过大小限制");
  check(cancel);
  byte[] prefix=new byte[12];int received=0;
  while(received<prefix.length){check(cancel);int n=in.read(prefix,received,prefix.length-received);if(n<0)break;received+=n;}
  if(received<12||prefix[4]!='f'||prefix[5]!='t'||prefix[6]!='y'||prefix[7]!='p')throw new IOException("服务器未返回有效 MP4 视频");
  if(limit<12)throw new IOException("视频超过大小限制");
  check(cancel);out.write(prefix);long count=12;
  byte[] buffer=new byte[128*1024];
  while(true){check(cancel);int n=in.read(buffer);if(n<0)break;check(cancel);
   if(n>limit-count)throw new IOException("视频超过大小限制");
   out.write(buffer,0,n);count+=n;progress.update(count,total);
  }
  check(cancel);
  if(total>=0&&count!=total)throw new IOException("视频下载不完整，请重试");
  out.flush();return count;
 }
 private static void check(Cancelled c)throws IOException{if(c.get()||Thread.currentThread().isInterrupted())throw new IOException("已取消下载");}
}
