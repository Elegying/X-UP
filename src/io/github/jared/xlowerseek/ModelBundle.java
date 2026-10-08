package io.github.jared.xlowerseek;
import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.zip.*;
/** Exact allowlist, bounded extraction, per-file hash, and atomic file publication. */
final class ModelBundle {
 static final class Entry {final String path,sha;final long size;Entry(String p,long s,String h){path=p;size=s;sha=h;}}
 static void install(File archive,File directory,Entry[] expected,AtomicBoolean pause)throws Exception{
  Map<String,Entry> names=new HashMap<>();long total=0;for(Entry e:expected){if(e.path.startsWith("/")||e.path.contains("..")||names.put(e.path,e)!=null)throw new IOException("无效模型清单");total+=e.size;}if(total>LocalModelSpec.MAX_BYTES)throw new IOException("模型超过大小限制");
  Set<String> seen=new HashSet<>();try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))){ZipEntry next;
   while((next=zip.getNextEntry())!=null){Entry spec=names.get(next.getName());if(spec==null||!seen.add(next.getName())||next.isDirectory())throw new IOException("模型包包含未知或重复文件");
    File target=new File(directory,spec.path);File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建模型目录");
    File temp=new File(target.getPath()+".installing");MessageDigest hash=MessageDigest.getInstance("SHA-256");long count=0;
    try{try(FileOutputStream out=new FileOutputStream(temp)){byte[] b=new byte[131072];int n;while((n=zip.read(b))!=-1){if(pause.get())throw new ModelTransfer.Paused();count+=n;if(count>spec.size)throw new IOException("模型文件超过大小限制");hash.update(b,0,n);out.write(b,0,n);}out.getFD().sync();}
     StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format(Locale.ROOT,"%02x",b&255));if(count!=spec.size||!spec.sha.equals(digest.toString()))throw new IOException("模型文件校验失败");
     Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }finally{if(temp.exists())temp.delete();}
   }
  }
  if(seen.size()!=expected.length)throw new IOException("模型包缺少文件");if(pause.get())throw new ModelTransfer.Paused();
 }
}
