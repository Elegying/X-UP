package io.github.jared.xlowerseek;

import java.util.*;

/** Bounded index of already loaded public posts. Never fetches a timeline or guesses a parent. */
final class PostContextIndex {
    static final class Post {
        final long id,parent,quote;final String text,visible;
        Post(long id,String text,String visible,long parent,long quote){this.id=id;this.text=text;this.visible=visible;this.parent=parent;this.quote=quote;}
    }
    static final class Context {final boolean post;final String text;Context(boolean p,String t){post=p;text=t;}}
    static final class Binding {
        private Context last=new Context(false,"");
        Context resolve(Context current){if(current.post)last=current;return last;}
    }
    private final LinkedHashMap<Long,Post> posts=new LinkedHashMap<>(16,.75f,true);
    private final Map<String,Set<Long>> byText=new HashMap<>();
    private Runnable onChange;
    synchronized void onChange(Runnable listener){onChange=listener;}
    synchronized void put(Post p){
        if(p.id<=0||p.text==null||p.text.length()>16384)return;
        Post current=posts.get(p.id);
        if(current!=null&&current.parent==p.parent&&current.quote==p.quote&&Objects.equals(current.text,p.text)&&Objects.equals(current.visible,p.visible))return;
        Post old=posts.put(p.id,p);if(old!=null)index(old,false);index(p,true);
        while(posts.size()>256){Long first=posts.keySet().iterator().next();index(posts.remove(first),false);}
        if(onChange!=null)onChange.run();
    }
    synchronized void putIfAbsent(Post p){if(!posts.containsKey(p.id))put(p);}
    private void index(Post p,boolean add){
        for(String key:new HashSet<>(Arrays.asList(normalize(p.text),normalize(p.visible)))){
            if(key.isEmpty())continue;
            if(add)byText.computeIfAbsent(key,k->new HashSet<>()).add(p.id);
            else {Set<Long> ids=byText.get(key);if(ids!=null){ids.remove(p.id);if(ids.isEmpty())byText.remove(key);}}
        }
    }
    synchronized Context forText(String text){
        Set<Long> ids=byText.get(normalize(text));
        if(ids==null||ids.isEmpty())return new Context(false,"");
        if(ids.size()!=1)return new Context(true,"");
        Post found=posts.get(ids.iterator().next());
        StringBuilder out=new StringBuilder();Set<Long> used=new HashSet<>();used.add(found.id);
        append(out,"上级回复",found.parent,1000,used);append(out,"引用",found.quote,600,used);
        return new Context(true,out.toString());
    }
    private void append(StringBuilder out,String role,long id,int limit,Set<Long> used){
        Post p=posts.get(id);if(p==null||!used.add(id))return;
        if(out.length()>0)out.append('\n');out.append(role).append('：').append(clip(p.text,limit));
    }
    static String clip(String value,int limit){int end=Math.min(value.length(),limit);if(end<value.length()&&end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;return value.substring(0,end);}
    private static String normalize(String text){return text==null?"":TextIdentifiers.withoutUrls(text).trim().replaceAll("\\s+"," ");}
}
