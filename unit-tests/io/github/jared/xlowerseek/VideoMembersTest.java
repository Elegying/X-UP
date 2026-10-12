package io.github.jared.xlowerseek;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import org.junit.Test;

public final class VideoMembersTest {
    static final class Renamed {
        static long ignored;
        private final long arbitraryName=41;
        private final String opaque="video";
        private long unrelated(int x){return x;}
        private long position(){return 41;}
    }
    static final class Ambiguous {
        private long first,second;
        private long one(){return 1;}
        private long two(){return 2;}
    }
    @Test public void obfuscatedNamesDoNotMatter() {
        Renamed value=new Renamed();
        assertEquals(41L,VideoMembers.read(VideoMembers.field(Renamed.class,long.class),value));
        assertEquals(41L,VideoMembers.invoke(VideoMembers.method(Renamed.class,long.class),value));
        assertEquals(6L,VideoMembers.invoke(VideoMembers.method(Renamed.class,long.class,int.class),value,6));
        Field field=VideoMembers.field(Renamed.class,f->f.getType()==String.class&&"video".equals(VideoMembers.read(f,value)));
        assertEquals("video",VideoMembers.read(field,value));
    }
    @Test public void ambiguousAndMissingMembersAreRejected() {
        assertThrows(IllegalArgumentException.class,()->VideoMembers.field(Ambiguous.class,long.class));
        assertThrows(IllegalArgumentException.class,()->VideoMembers.method(Ambiguous.class,long.class));
        assertThrows(IllegalArgumentException.class,()->VideoMembers.field(Renamed.class,float.class));
        assertThrows(IllegalArgumentException.class,()->VideoMembers.method(Renamed.class,void.class));
    }
}
