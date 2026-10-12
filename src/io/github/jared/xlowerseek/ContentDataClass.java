package io.github.jared.xlowerseek;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.UsingFieldData;

/** Resolves generated data-class properties from their semantic labels, never host field names. */
final class ContentDataClass {
    private static final Pattern LABEL=Pattern.compile("(?:^[A-Za-z0-9_.$]+\\(|, )([A-Za-z][A-Za-z0-9_]*)=");
    final Class<?> type;
    private final Map<String,Field> fields;

    ContentDataClass(TargetResolver resolver,Class<?> type)throws Exception {
        this.type=type;
        MethodData method=resolver.dex().getMethodData(type.getDeclaredMethod("toString"));
        if(method==null)throw new IllegalArgumentException("Missing generated model description");
        // DexKit 2.3.0 preserves instruction order for strings/field uses (dex_item.cpp).
        // Branches, repeated fields or labels would make their positional relation unsafe.
        for(int op:method.getOpCodes())if(op>=0x28&&op<=0x2c||op>=0x32&&op<=0x3d)
            throw new IllegalArgumentException("Conditional model description");
        List<Field> reads=new ArrayList<>();
        for(UsingFieldData use:method.getUsingFields()){
            if(!use.getUsingType().isRead()||!use.getField().getClassName().equals(type.getName()))
                throw new IllegalArgumentException("Non-model field in description");
            Field field=use.getField().getFieldInstance(resolver.loader);
            if(Modifier.isStatic(field.getModifiers()))throw new IllegalArgumentException("Static model property");
            field.setAccessible(true);reads.add(field);
        }
        fields=bind(method.getUsingStrings(),reads);
    }

    static Map<String,Field> bind(List<String> strings,List<Field> reads){
        List<String> names=new ArrayList<>();
        for(String text:strings){Matcher matcher=LABEL.matcher(text);while(matcher.find()){
            if(names.isEmpty()&&text.charAt(0)==',')throw new IllegalArgumentException("Reordered model labels");
            names.add(matcher.group(1));
        }}
        if(names.isEmpty()||names.size()!=reads.size())throw new IllegalArgumentException("Model labels and fields differ");
        Map<String,Field> result=new LinkedHashMap<>();
        for(int i=0;i<names.size();i++){
            Field field=reads.get(i);
            if(result.containsKey(names.get(i))||result.containsValue(field))throw new IllegalArgumentException("Ambiguous model property");
            result.put(names.get(i),field);
        }
        return result;
    }

    Field field(String name)throws NoSuchFieldException {
        Field field=fields.get(name);if(field==null)throw new NoSuchFieldException("Model property "+name);return field;
    }
    Field field(String name,Class<?> expected)throws NoSuchFieldException {
        Field field=field(name);if(field.getType()!=expected)throw new NoSuchFieldException("Model property type "+name);return field;
    }
}
