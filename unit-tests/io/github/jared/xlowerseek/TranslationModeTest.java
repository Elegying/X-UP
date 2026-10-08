package io.github.jared.xlowerseek;
import org.junit.Test;import static org.junit.Assert.*;
public class TranslationModeTest {
 @Test public void localIsDefaultAndLegacyUnknownModesNormalize(){assertEquals(TranslationMode.LOCAL,TranslationMode.normalize(55));assertTrue(TranslationMode.enabled(1,Feature.NATIVE_TRANSLATE));assertFalse(TranslationMode.enabled(1,Feature.LOCAL_TEXT));}
 @Test public void disablingNativeSelectsLocalBackup(){assertEquals(2,TranslationMode.switchTo(1,Feature.NATIVE_TRANSLATE,false));}
 @Test public void bothEnginesCanNeverBeEnabled(){for(int m=-1;m<5;m++)for(Feature f:new Feature[]{Feature.NATIVE_TRANSLATE,Feature.LOCAL_TEXT})for(boolean on:new boolean[]{true,false}){int n=TranslationMode.switchTo(m,f,on);assertFalse(TranslationMode.enabled(n,Feature.LOCAL_TEXT)&&TranslationMode.enabled(n,Feature.NATIVE_TRANSLATE));}}
 @Test public void enablingNativeDisablesLocalAndLocalCanBeDisabled(){assertEquals(1,TranslationMode.switchTo(2,Feature.NATIVE_TRANSLATE,true));assertEquals(1,TranslationMode.switchTo(2,Feature.LOCAL_TEXT,false));assertEquals(2,TranslationMode.switchTo(1,Feature.LOCAL_TEXT,true));}
}
