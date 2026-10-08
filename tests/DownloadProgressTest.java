package io.github.jared.xlowerseek;
public final class DownloadProgressTest {
 public static void main(String[] args){
  long[][] cases={{1024,-1,-1},{1024,0,-1},{0,100,0},{25,100,25},{70,100,70},{100,100,99},{101,100,99},{Long.MAX_VALUE/2,Long.MAX_VALUE,50}};
  for(long[] c:cases)if(DownloadProgress.percent(c[0],c[1])!=c[2])throw new AssertionError(java.util.Arrays.toString(c));
  System.out.println("DownloadProgress: 8 assertions passed");
 }
}
