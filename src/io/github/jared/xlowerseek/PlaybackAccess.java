package io.github.jared.xlowerseek;

/** Validated player contract shared by gesture recognition and its host tests. */
interface PlaybackAccess {
    long duration(Object player);
    long position(Object player);
    boolean ad(Object player);
    boolean playing(Object player);
    void seek(Object player,long position);
    Object speed(Object player);
    Object doubled(Object speed);
    void speed(Object player,Object speed);
    float speedValue(Object speed);
}
