package scala.tools.nsc
package settings

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import scala.tools.testing.AssertUtil.assertThrows

@RunWith(classOf[JUnit4])
class SettingsTest {
  @Test def booleanSettingColon() {
    def check(args: String*): MutableSettings#BooleanSetting = {
      val s = new MutableSettings(msg => throw new IllegalArgumentException(msg))
      val b1 = new s.BooleanSetting("-Ytest-setting", "")
      s.allSettings += b1
      val (ok, residual) = s.processArguments(args.toList, processAll = true)
      assert(residual.isEmpty)
      b1
    }
    assertTrue(check("-Ytest-setting").value)
    assertTrue(check("-Ytest-setting:true").value)
    assertTrue(check("-Ytest-setting:TRUE").value)
    assertFalse(check("-Ytest-setting:false").value)
    assertFalse(check("-Ytest-setting:FALSE").value)
    assertThrows[IllegalArgumentException](check("-Ytest-setting:rubbish"))
  }

  @Test def userSettingsHavePredecenceOverOptimize() {
    def check(args: String*): MutableSettings#BooleanSetting = {
      val s = new MutableSettings(msg => throw new IllegalArgumentException(msg))
      val (ok, residual) = s.processArguments(args.toList, processAll = true)
      assert(residual.isEmpty)
      s.inline // among -optimize
    }
    assertTrue(check("-optimise").value)
    assertFalse(check("-optimise", "-Yinline:false").value)
    assertFalse(check("-Yinline:false", "-optimise").value)
  }

  @Test def userSettingsHavePredecenceOverLint() {
    def check(args: String*): MutableSettings#BooleanSetting = {
      val s = new MutableSettings(msg => throw new IllegalArgumentException(msg))
      val (ok, residual) = s.processArguments(args.toList, processAll = true)
      assert(residual.isEmpty)
      s.warnAdaptedArgs // among Xlint
    }
    assertTrue(check("-Xlint").value)
    assertFalse(check("-Xlint", "-Ywarn-adapted-args:false").value)
    assertFalse(check("-Ywarn-adapted-args:false", "-Xlint").value)
  }
}
