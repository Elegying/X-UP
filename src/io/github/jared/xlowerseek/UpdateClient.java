package io.github.jared.xlowerseek;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;

final class UpdateClient {
 static UpdateRelease check(String installed,boolean beta)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/Elegying/X-UP/releases?per_page=100").openConnection();
  c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
  c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","X-UP/"+installed);c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
  try{int code=c.getResponseCode();if(code==403||code==429)throw new IOException("检查过于频繁或网络受限，请稍后重试");if(code!=200)throw new IOException("更新服务暂不可用（"+code+"）");
   ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(b.size()+n>2_000_000)throw new IOException("更新响应过大");b.write(buf,0,n);}}
   return UpdateRelease.newest(b.toString(StandardCharsets.UTF_8.name()),installed,beta);
  }finally{c.disconnect();}
 }
 private UpdateClient(){}
}
