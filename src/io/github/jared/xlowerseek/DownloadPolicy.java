package io.github.jared.xlowerseek;
import java.net.URI;
public final class DownloadPolicy {
    public static boolean validUrl(String value) {
        try {
            URI uri=new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && "video.twimg.com".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo()==null && (uri.getPort()==-1||uri.getPort()==443)
                && uri.getPath()!=null && uri.getPath().endsWith(".mp4");
        } catch(Exception e){return false;}
    }
}
