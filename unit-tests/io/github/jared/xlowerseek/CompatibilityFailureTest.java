package io.github.jared.xlowerseek;
import org.junit.Test;
import static org.junit.Assert.*;
public class CompatibilityFailureTest {
 @Test public void retriesOnlyStructuralFailureAfterCacheUse(){
  assertTrue(CompatibilityFailure.retry(true,new NoSuchMethodException()));
  assertTrue(CompatibilityFailure.retry(true,new CompatibilityFailure("test",0)));
  assertFalse(CompatibilityFailure.retry(false,new NoSuchFieldException()));
  assertFalse(CompatibilityFailure.retry(true,new SecurityException()));
  assertFalse(CompatibilityFailure.retry(true,new OutOfMemoryError()));
 }
 @Test public void failedRollbackCannotInstallDuplicateHooks(){
  Throwable e=new NoSuchMethodException();e.addSuppressed(new RuntimeException("unhook failed"));
  assertFalse(CompatibilityFailure.retry(true,e));
 }
 @Test public void diagnosticsSeparateMissingAndAmbiguousAndHideMessages(){
  assertNotEquals(CompatibilityFailure.describe(new CompatibilityFailure("model",0)),CompatibilityFailure.describe(new CompatibilityFailure("model",2)));
  assertFalse(CompatibilityFailure.describe(new IllegalStateException("private content")).contains("private content"));
 }
}
