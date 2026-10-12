package io.github.jared.xlowerseek;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only synthetic marker objects are inspected; no user text is serialized or logged. */
final class ContentModelProbe {
    static Field property(Object sample,String property,Class<?> expected)throws ReflectiveOperationException {
        String description=sample.toString();
        Matcher match=Pattern.compile("(?:\\(|, )"+Pattern.quote(property)+"=([^,)]*)").matcher(description);
        if(!match.find())throw new NoSuchFieldException("Missing probe property "+property);
        String marker=match.group(1);if(match.find())throw new NoSuchFieldException("Ambiguous probe property "+property);
        Field result=null;
        for(Field field:sample.getClass().getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&field.getType()==expected){
            field.setAccessible(true);
            if(marker.equals(String.valueOf(field.get(sample)))){
                if(result!=null)throw new NoSuchFieldException("Ambiguous probe marker");result=field;
            }
        }
        if(result==null)throw new NoSuchFieldException("Probe property type "+property);return result;
    }
    private ContentModelProbe(){}
}
