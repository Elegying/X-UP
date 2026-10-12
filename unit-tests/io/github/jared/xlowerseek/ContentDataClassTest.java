package io.github.jared.xlowerseek;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContentDataClassTest {
    static final class Before {String a;Object b;long c;}
    static final class Renamed {String q;Object z;long p;}
    private Map<String,Field> bind(Class<?> type,String... fields)throws Exception {
        return ContentDataClass.bind(Arrays.asList("Item(text=",", promotedMetadata=",", sortIndex=",")"),
            Arrays.asList(type.getDeclaredField(fields[0]),type.getDeclaredField(fields[1]),type.getDeclaredField(fields[2])));
    }
    @Test public void renamedFieldsKeepTheirSemanticRoles()throws Exception {
        assertEquals("b",bind(Before.class,"a","b","c").get("promotedMetadata").getName());
        assertEquals("z",bind(Renamed.class,"q","z","p").get("promotedMetadata").getName());
        assertEquals(long.class,bind(Renamed.class,"q","z","p").get("sortIndex").getType());
    }
    @Test public void missingReadDoesNotShiftOtherProperties()throws Exception {
        try{ContentDataClass.bind(Arrays.asList("Item(text=",", promotedMetadata="),Arrays.asList(Before.class.getDeclaredField("a")));fail();}
        catch(IllegalArgumentException expected){}
    }
    @Test public void repeatedReadAndLabelAreRejected()throws Exception {
        Field value=Before.class.getDeclaredField("a");
        try{ContentDataClass.bind(Arrays.asList("Item(text=",", promotedMetadata="),Arrays.asList(value,value));fail();}
        catch(IllegalArgumentException expected){}
        try{ContentDataClass.bind(Arrays.asList("Item(text=",", text="),Arrays.asList(value,Before.class.getDeclaredField("b")));fail();}
        catch(IllegalArgumentException expected){}
    }
    @Test public void compilerReorderedLabelsAreRejected()throws Exception {
        try{ContentDataClass.bind(Arrays.asList(", promotedMetadata=","Item(text="),
            Arrays.asList(Before.class.getDeclaredField("a"),Before.class.getDeclaredField("b")));fail();}
        catch(IllegalArgumentException expected){}
    }
}
