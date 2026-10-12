package com.google.mlkit.common;

/** Official ML Kit error values used by the download status mapping. */
public final class MlKitException extends Exception {
    public static final int PERMISSION_DENIED=7, RESOURCE_EXHAUSTED=8,
            FAILED_PRECONDITION=9, UNAVAILABLE=14, NETWORK_ISSUE=17, UNSUPPORTED=18,
            MODEL_INCOMPATIBLE_WITH_TFLITE=100, NOT_ENOUGH_SPACE=101, MODEL_HASH_MISMATCH=102;
    private final int code;
    public MlKitException(String message,int code){super(message);this.code=code;}
    public int getErrorCode(){return code;}
}
