package io.github.jared.xlowerseek;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.UsingFieldMatcher;
import org.luckypray.dexkit.query.enums.UsingType;

/** Content adapters are discovered independently, so an unfamiliar surface cannot disable others. */
final class ContentTargets {
    static Class<?> model(TargetResolver resolver,String prefix)throws Exception {
        return resolver.type("content.model."+prefix,ClassMatcher.create().usingEqStrings(prefix),type->{
            try{return type.getDeclaredMethod("toString").getReturnType()==String.class&&!type.isInterface();}
            catch(ReflectiveOperationException ignored){return false;}
        });
    }
    static ContentDataClass data(TargetResolver resolver,String prefix)throws Exception {
        return new ContentDataClass(resolver,model(resolver,prefix));
    }
    static Field onlyField(Class<?> owner,Class<?> type)throws NoSuchFieldException {
        Field result=null;
        for(Field field:owner.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&field.getType()==type){
            if(result!=null)throw new NoSuchFieldException("Ambiguous field type in "+owner.getName());result=field;
        }
        if(result==null)throw new NoSuchFieldException("Missing field type in "+owner.getName());
        result.setAccessible(true);return result;
    }
    static Method getter(TargetResolver resolver,String id,Class<?> owner,Field field)throws Exception {
        return resolver.method("content.getter."+id,MethodMatcher.create().declaredClass(owner).paramCount(0)
            .returnType(field.getType()).addUsingField(new UsingFieldMatcher(field,UsingType.Read)),
            method->!Modifier.isStatic(method.getModifiers())&&!method.getName().equals("toString")&&!method.getName().equals("hashCode"));
    }

    static final class Ads {
        final Class<?> post,user,imageAd,trend,module,moduleItem;
        final Field postAd,userAd,trendValue,trendAd,children,wrapped,dispensable;
        final Field[] moduleFields;
        final Constructor<?> copyModule,copyItem;
        Ads(TargetResolver resolver)throws Exception {
            ContentDataClass postData=data(resolver,"UrtTimelinePost(postResult=");post=postData.type;
            ContentDataClass userData=data(resolver,"UrtTimelineUser(user=");user=userData.type;
            imageAd=model(resolver,"UrtTimelineRtbImageAd(advertiserAvatarImageUrl=");
            ContentDataClass trendData=data(resolver,"UrtTimelineTrend(timelineTrend=");trend=trendData.type;
            ContentDataClass moduleData=data(resolver,"UrtTimelineModule(innerContent=");module=moduleData.type;
            ContentDataClass itemData=data(resolver,"UrtTimelineModuleItem(item=");moduleItem=itemData.type;
            Class<?> promotion=model(resolver,"TimelinePromotedMetadata(impressionId=");
            postAd=postData.field("promotedMetadata",promotion);userAd=userData.field("promotedMetadata",promotion);
            trendValue=trendData.field("timelineTrend");
            // The promotion metadata type is unique; unrelated trend strings can be reordered by R8.
            trendAd=onlyField(trendValue.getType(),promotion);
            children=moduleData.field("innerContent",List.class);wrapped=itemData.field("item");dispensable=itemData.field("isDispensable",boolean.class);
            String[] names={"moduleHeader","moduleFooter","displayType","sortIndex","entryId","clientEventInfo"};
            moduleFields=new Field[names.length];Class<?>[] params=new Class<?>[names.length+1];params[0]=List.class;
            for(int i=0;i<names.length;i++){moduleFields[i]=moduleData.field(names[i]);params[i+1]=moduleFields[i].getType();}
            if(moduleFields[3].getType()!=long.class||moduleFields[4].getType()!=String.class)throw new NoSuchFieldException("Timeline module shape");
            copyModule=module.getDeclaredConstructor(params);copyModule.setAccessible(true);
            copyItem=moduleItem.getDeclaredConstructor(wrapped.getType(),boolean.class);copyItem.setAccessible(true);
        }
    }
    private ContentTargets(){}
}
