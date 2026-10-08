#include <jni.h>
#include "opus_engine.h"
#include <exception>
static void fail(JNIEnv *env,const char *text){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),text);}
extern "C" JNIEXPORT jlong JNICALL Java_io_github_jared_xlowerseek_OpusTranslator_open(JNIEnv *env,jclass,jstring path){
 const char *value=env->GetStringUTFChars(path,nullptr);if(!value)return 0;std::string file(value);env->ReleaseStringUTFChars(path,value);
 try{return reinterpret_cast<jlong>(new OpusEngine(file));}catch(const std::exception &e){fail(env,e.what());return 0;}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_io_github_jared_xlowerseek_OpusTranslator_run(JNIEnv *env,jclass,jlong handle,jbyteArray input,jboolean ja,jlong epoch){
 if(!handle){fail(env,"模型未加载");return nullptr;}auto size=env->GetArrayLength(input);if(size>32768){fail(env,"文本过长");return nullptr;}
 std::string text(size,'\0');env->GetByteArrayRegion(input,0,size,reinterpret_cast<jbyte*>(text.data()));if(env->ExceptionCheck())return nullptr;
 try{auto output=reinterpret_cast<OpusEngine*>(handle)->translate(text,ja,epoch);auto bytes=env->NewByteArray(output.size());if(bytes)env->SetByteArrayRegion(bytes,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));return bytes;}catch(const std::exception &e){fail(env,e.what());return nullptr;}
}
extern "C" JNIEXPORT jlong JNICALL Java_io_github_jared_xlowerseek_OpusTranslator_ticket(JNIEnv*,jclass,jlong h){return reinterpret_cast<OpusEngine*>(h)->ticket();}
extern "C" JNIEXPORT void JNICALL Java_io_github_jared_xlowerseek_OpusTranslator_cancel(JNIEnv*,jclass,jlong h){reinterpret_cast<OpusEngine*>(h)->cancel();}
extern "C" JNIEXPORT void JNICALL Java_io_github_jared_xlowerseek_OpusTranslator_close(JNIEnv*,jclass,jlong h){delete reinterpret_cast<OpusEngine*>(h);}
