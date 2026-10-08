package io.github.jared.xlowerseek;
import java.io.*;
import java.util.Arrays;
public final class Mp4TransferTest {
 static int checks;
 static byte[] video(int n){byte[] b=new byte[n];b[4]='f';b[5]='t';b[6]='y';b[7]='p';return b;}
 static void check(boolean b){checks++;if(!b)throw new AssertionError("case "+checks);}
 static void fails(byte[] b,long total,long limit,Mp4Transfer.Cancelled c)throws Exception{
  try{Mp4Transfer.copy(new ByteArrayInputStream(b),new ByteArrayOutputStream(),total,limit,c,(a,d)->{});throw new AssertionError("Expected failure");}catch(IOException expected){checks++;}
 }
 public static void main(String[] args)throws Exception{
  byte[] b=video(500000);ByteArrayOutputStream out=new ByteArrayOutputStream();long[] progress={0};
  check(Mp4Transfer.copy(new ByteArrayInputStream(b),out,b.length,1000000,()->false,(a,d)->progress[0]=a)==b.length);
  check(Arrays.equals(out.toByteArray(),b));check(progress[0]==b.length);
  out.reset();check(Mp4Transfer.copy(new ByteArrayInputStream(b),out,-1,1000000,()->false,(a,d)->{})==b.length);
  fails(b,b.length+1,1000000,()->false);fails(b,b.length-1,1000000,()->false);
  fails(b,-1,100,()->false);fails(b,b.length,100,()->false);
  fails(new byte[5],5,100,()->false);fails(new byte[100],100,200,()->false);
  fails(b,b.length,1000000,()->true);
  final int[] calls={0};fails(b,b.length,1000000,()->++calls[0]>6);
  InputStream shortReads=new ByteArrayInputStream(b){@Override public synchronized int read(byte[] x,int off,int n){return super.read(x,off,Math.min(n,3));}};
  out.reset();check(Mp4Transfer.copy(shortReads,out,b.length,1000000,()->false,(a,d)->{})==b.length);check(Arrays.equals(b,out.toByteArray()));
  try{Mp4Transfer.copy(new ByteArrayInputStream(b),new OutputStream(){public void write(int x)throws IOException{throw new IOException("disk full");}},b.length,1000000,()->false,(a,d)->{});throw new AssertionError("write failure swallowed");}catch(IOException expected){checks++;}
  System.out.println("Mp4Transfer: "+checks+" assertions passed");
 }
}
