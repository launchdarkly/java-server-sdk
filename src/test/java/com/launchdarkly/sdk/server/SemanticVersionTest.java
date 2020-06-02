package com.launchdarkly.sdk.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.launchdarkly.sdk.server.SemanticVersion;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class SemanticVersionTest {
  @Test
  public void canParseSimpleCompleteVersion() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2.3.4");
    assertEquals(2, sv.getMajor());
    assertEquals(3, sv.getMinor());
    assertEquals(4, sv.getPatch());
    assertNull(sv.getPrerelease());
    assertNull(sv.getBuild());
  }
  
  @Test
  public void canParseVersionWithPrerelease() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2.3.4-beta1.rc2");
    assertEquals(2, sv.getMajor());
    assertEquals(3, sv.getMinor());
    assertEquals(4, sv.getPatch());
    assertEquals("beta1.rc2", sv.getPrerelease());
    assertNull(sv.getBuild());
  }
  
  @Test
  public void canParseVersionWithBuild() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2.3.4+build2.4");
    assertEquals(2, sv.getMajor());
    assertEquals(3, sv.getMinor());
    assertEquals(4, sv.getPatch());
    assertNull(sv.getPrerelease());
    assertEquals("build2.4", sv.getBuild());
  }
  
  @Test
  public void canParseVersionWithPrereleaseAndBuild() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2.3.4-beta1.rc2+build2.4");
    assertEquals(2, sv.getMajor());
    assertEquals(3, sv.getMinor());
    assertEquals(4, sv.getPatch());
    assertEquals("beta1.rc2", sv.getPrerelease());
    assertEquals("build2.4", sv.getBuild());    
  }

  @Test(expected = SemanticVersion.InvalidVersionException.class)
  public void leadingZeroNotAllowedInMajor() throws Exception {
    SemanticVersion.parse("02.3.4");
  }

  @Test(expected = SemanticVersion.InvalidVersionException.class)
  public void leadingZeroNotAllowedInMinor() throws Exception {
    SemanticVersion.parse("2.03.4");
  }

  @Test(expected = SemanticVersion.InvalidVersionException.class)
  public void leadingZeroNotAllowedInPatch() throws Exception {
    SemanticVersion.parse("2.3.04");
  }

  @Test
  public void zeroByItselfIsAllowed() throws Exception {
    assertEquals(0, SemanticVersion.parse("0.3.4").getMajor());
    assertEquals(0, SemanticVersion.parse("2.0.4").getMinor());
    assertEquals(0, SemanticVersion.parse("2.3.0").getPatch());
  }
  
  @Test
  public void canParseVersionWithMajorOnly() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2", true);
    assertEquals(2, sv.getMajor());
    assertEquals(0, sv.getMinor());
    assertEquals(0, sv.getPatch());
    assertNull(sv.getPrerelease());
    assertNull(sv.getBuild());
  }
  
  @Test(expected=SemanticVersion.InvalidVersionException.class)
  public void cannotParseVersionWithMajorOnlyIfFlagNotSet() throws Exception {
    SemanticVersion.parse("2");
  }

  @Test
  public void canParseVersionWithMajorAndMinorOnly() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2.3", true);
    assertEquals(2, sv.getMajor());
    assertEquals(3, sv.getMinor());
    assertEquals(0, sv.getPatch());
    assertNull(sv.getPrerelease());
    assertNull(sv.getBuild());
  }

  @Test(expected=SemanticVersion.InvalidVersionException.class)
  public void cannotParseVersionWithMajorAndMinorOnlyIfFlagNotSet() throws Exception {
    SemanticVersion.parse("2.3");
  }

  @Test
  public void canParseVersionWithMajorAndPrereleaseOnly() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2-beta1", true);
    assertEquals(2, sv.getMajor());
    assertEquals(0, sv.getMinor());
    assertEquals(0, sv.getPatch());
    assertEquals("beta1", sv.getPrerelease());
    assertNull(sv.getBuild());
  }

  @Test
  public void canParseVersionWithMajorMinorAndPrereleaseOnly() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2.3-beta1", true);
    assertEquals(2, sv.getMajor());
    assertEquals(3, sv.getMinor());
    assertEquals(0, sv.getPatch());
    assertEquals("beta1", sv.getPrerelease());
    assertNull(sv.getBuild());
  }

  @Test
  public void canParseVersionWithMajorAndBuildOnly() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2+build1", true);
    assertEquals(2, sv.getMajor());
    assertEquals(0, sv.getMinor());
    assertEquals(0, sv.getPatch());
    assertNull(sv.getPrerelease());
    assertEquals("build1", sv.getBuild());
  }

  @Test
  public void canParseVersionWithMajorMinorAndBuildOnly() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("2.3+build1", true);
    assertEquals(2, sv.getMajor());
    assertEquals(3, sv.getMinor());
    assertEquals(0, sv.getPatch());
    assertNull(sv.getPrerelease());
    assertEquals("build1", sv.getBuild());
  }
  
  @Test(expected=SemanticVersion.InvalidVersionException.class)
  public void majorVersionMustBeNumeric() throws Exception {
    SemanticVersion.parse("x.0.0");
  }

  @Test(expected=SemanticVersion.InvalidVersionException.class)
  public void minorVersionMustBeNumeric() throws Exception {
    SemanticVersion.parse("0.x.0");
  }

  @Test(expected=SemanticVersion.InvalidVersionException.class)
  public void patchVersionMustBeNumeric() throws Exception {
    SemanticVersion.parse("0.0.x");
  }
  
  @Test
  public void equalVersionsHaveEqualPrecedence() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("2.3.4-beta1");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4-beta1");
    assertEquals(0, sv1.comparePrecedence(sv2));

    SemanticVersion sv3 = SemanticVersion.parse("2.3.4");
    SemanticVersion sv4 = SemanticVersion.parse("2.3.4");
    assertEquals(0, sv3.comparePrecedence(sv4));
  }

  @Test
  public void lowerMajorVersionHasLowerPrecedence() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("1.3.4-beta1");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4-beta1");
    assertEquals(-1, sv1.comparePrecedence(sv2));
    assertEquals(1, sv2.comparePrecedence(sv1));
  }

  @Test
  public void lowerMinorVersionHasLowerPrecedence() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("2.2.4-beta1");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4-beta1");
    assertEquals(-1, sv1.comparePrecedence(sv2));
    assertEquals(1, sv2.comparePrecedence(sv1));
  }

  @Test
  public void lowerPatchVersionHasLowerPrecedence() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("2.3.3-beta1");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4-beta1");
    assertEquals(-1, sv1.comparePrecedence(sv2));
    assertEquals(1, sv2.comparePrecedence(sv1));
  }

  @Test
  public void prereleaseVersionHasLowerPrecedenceThanRelease() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("2.3.4-beta1");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4");
    assertEquals(-1, sv1.comparePrecedence(sv2));
    assertEquals(1, sv2.comparePrecedence(sv1));
  }

  @Test
  public void shorterSubsetOfPrereleaseIdentifiersHasLowerPrecedence() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("2.3.4-beta1");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4-beta1.rc1");
    assertEquals(-1, sv1.comparePrecedence(sv2));
    assertEquals(1, sv2.comparePrecedence(sv1));
  }

  @Test
  public void numericPrereleaseIdentifiersAreSortedNumerically() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("2.3.4-beta1.3");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4-beta1.23");
    assertEquals(-1, sv1.comparePrecedence(sv2));
    assertEquals(1, sv2.comparePrecedence(sv1));
  }

  @Test
  public void nonNumericPrereleaseIdentifiersAreSortedAsStrings() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("2.3.4-beta1.x3");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4-beta1.x23");
    assertEquals(1, sv1.comparePrecedence(sv2));
    assertEquals(-1, sv2.comparePrecedence(sv1));
  }

  @Test
  public void numericPrereleaseIdentifiersAreLowerThanStrings() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("2.3.4-beta1.x.100");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4-beta1.3.100");
    assertEquals(1, sv1.comparePrecedence(sv2));
    assertEquals(-1, sv2.comparePrecedence(sv1));
  }

  @Test
  public void buildIdentifierDoesNotAffectPrecedence() throws Exception {
    SemanticVersion sv1 = SemanticVersion.parse("2.3.4-beta1+build1");
    SemanticVersion sv2 = SemanticVersion.parse("2.3.4-beta1+build2");
    assertEquals(0, sv1.comparePrecedence(sv2));
    assertEquals(0, sv2.comparePrecedence(sv1));
  }
  
  @Test
  public void anyVersionIsGreaterThanNull() throws Exception {
    SemanticVersion sv = SemanticVersion.parse("0.0.0");
    assertEquals(1, sv.comparePrecedence(null));
    assertEquals(1, sv.compareTo(null));
  }
}
