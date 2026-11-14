package io.github.jared.xlowerseek;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReplySortContractTest {
    enum Sort {Recency,Relevance,Likes}
    @Test public void acceptsOnlyKnownRepositoryLayouts(){
        assertTrue(ReplySortHook.repositoryConstructor(new Class<?>[]{Object.class,Object.class,long.class,Sort.class,Object.class,Object.class},Sort.class));
        assertTrue(ReplySortHook.repositoryConstructor(new Class<?>[]{Object.class,Object.class,long.class,Sort.class,Object.class,Object.class,boolean.class},Sort.class));
        assertFalse(ReplySortHook.repositoryConstructor(new Class<?>[]{Object.class,Object.class,long.class,Sort.class,Object.class,Object.class,String.class},Sort.class));
        assertFalse(ReplySortHook.repositoryConstructor(new Class<?>[]{long.class,Sort.class,Object.class,Object.class},Sort.class));
    }
}
