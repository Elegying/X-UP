#include <jni.h>
#include "engine.h"
#include <exception>
static void fail(JNIEnv *env,const char *text){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),text);}
extern "C" JNIEXPORT jlong JNICALL Java_io_github_jared_xlowerseek_NativeTranslator_open(JNIEnv *env,jclass,jstring path){
 const char *value=env->GetStringUTFChars(path,nullptr);if(!value)return 0;std::string file(value);env->ReleaseStringUTFChars(path,value);
 try{return reinterpret_cast<jlong>(new TranslationEngine(file));}catch(const std::exception &e){fail(env,e.what());return 0;}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_io_github_jared_xlowerseek_NativeTranslator_run(JNIEnv *env,jclass,jlong handle,jbyteArray input){
 if(!handle){fail(env,"模型未加载");return nullptr;}
 auto size=env->GetArrayLength(input);if(size>32768){fail(env,"文本过长");return nullptr;}
 std::string prompt(size,'\0');env->GetByteArrayRegion(input,0,size,reinterpret_cast<jbyte*>(prompt.data()));if(env->ExceptionCheck())return nullptr;
 try{auto output=reinterpret_cast<TranslationEngine*>(handle)->translate(prompt);auto bytes=env->NewByteArray(output.size());if(bytes)env->SetByteArrayRegion(bytes,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));return bytes;}
 catch(const std::exception &e){fail(env,e.what());return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_io_github_jared_xlowerseek_NativeTranslator_close(JNIEnv*,jclass,jlong handle){delete reinterpret_cast<TranslationEngine*>(handle);}
