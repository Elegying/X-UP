package io.github.jared.xlowerseek;
/** Each presentation requests once; an unready state is distinct from a manual/native action. */
final class TranslationGate {
    private boolean queued, finished;
    boolean observe(boolean canTranslate,boolean enabled){return observe(canTranslate,enabled,!canTranslate);}
    boolean observe(boolean canTranslate,boolean enabled,boolean terminal){
        if(terminal){queued=false;finished=true;return false;}
        if(!canTranslate||!enabled){queued=false;return false;}
        if(finished||queued)return false;
        queued=true;return true;
    }
    boolean claim(){if(!queued||finished)return false;queued=false;finished=true;return true;}
    void defer(){queued=false;}
}
