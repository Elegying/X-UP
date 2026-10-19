package io.github.jared.xlowerseek;

import androidx.compose.runtime.Composer;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdRenderSignatureTest {
    enum Timeline { HOME }
    private Class<?>[] shape(int masks) {
        Class<?>[] p=new Class<?>[23+masks];
        Arrays.fill(p,Object.class);
        p[0]=List.class;p[1]=Timeline.class;p[2]=boolean.class;p[3]=boolean.class;
        p[22]=Composer.class;
        for(int i=23;i<p.length;i++)p[i]=int.class;
        return p;
    }
    private int match(Class<?>[] p) {return AdRenderSignature.composerIndex(Modifier.STATIC,void.class,p);}
    @Test public void acceptsOldDefaultMaskAndNewTwoMaskBoundary() {
        assertEquals(22,match(shape(3)));
        assertEquals(22,match(shape(2)));
    }
    @Test public void rejectsUnknownComposeTails() {
        assertEquals(-1,match(shape(1)));
        assertEquals(-1,match(shape(4)));
        Class<?>[] p=shape(2);p[24]=Integer.class;assertEquals(-1,match(p));
        p=shape(3);p[23]=long.class;assertEquals(-1,match(p));
        p=shape(2);p[22]=Object.class;assertEquals(-1,match(p));
    }
    @Test public void preservesListBoundaryConstraints() {
        for(int index=0;index<4;index++) {
            Class<?>[] p=shape(2);p[index]=Object.class;assertEquals(-1,match(p));
        }
        assertEquals(-1,AdRenderSignature.composerIndex(0,void.class,shape(2)));
        assertEquals(-1,AdRenderSignature.composerIndex(Modifier.STATIC,Object.class,shape(2)));
        assertEquals(-1,match(new Class<?>[0]));
    }
}
