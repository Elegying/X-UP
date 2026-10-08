package io.github.jared.xlowerseek;
/** Unknown totals must remain indeterminate; publication, not byte count, marks completion. */
final class DownloadProgress {
    static int percent(long bytes,long total){
        if(total<=0)return -1;
        if(bytes<=0)return 0;
        return (int)Math.min(99,bytes/(double)total*100);
    }
}
