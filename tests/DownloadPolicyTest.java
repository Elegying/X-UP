import io.github.jared.xlowerseek.DownloadPolicy;
public class DownloadPolicyTest {
 public static void main(String[] args){
  String[] yes={"https://video.twimg.com/amplify_video/1/vid/720x720/test.mp4?tag=14","https://video.twimg.com:443/a.mp4"};
  String[] no={"http://video.twimg.com/a.mp4","https://video.twimg.com.evil.test/a.mp4","https://evil.test/video.twimg.com/a.mp4","https://video.twimg.com@127.0.0.1/a.mp4","https://video.twimg.com:8000/a.mp4","https://video.twimg.com/a.m3u8","file:///a.mp4",null};
  for(String s:yes)if(!DownloadPolicy.validUrl(s))throw new AssertionError("Rejected valid URL");
  for(String s:no)if(DownloadPolicy.validUrl(s))throw new AssertionError("Accepted invalid URL");
  System.out.println("PASS 10 download source boundary cases");
 }
}
