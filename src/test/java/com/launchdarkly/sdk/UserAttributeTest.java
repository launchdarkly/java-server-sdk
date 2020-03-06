package com.launchdarkly.sdk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class UserAttributeTest {
  @Test
  public void keyAttribute() {
    assertEquals("key", UserAttribute.KEY.getName());
    assertTrue(UserAttribute.KEY.isBuiltIn());
  }

  @Test
  public void secondaryKeyAttribute() {
    assertEquals("secondary", UserAttribute.SECONDARY_KEY.getName());
    assertTrue(UserAttribute.SECONDARY_KEY.isBuiltIn());
  }
  
  @Test
  public void ipAttribute() {
    assertEquals("ip", UserAttribute.IP.getName());
    assertTrue(UserAttribute.IP.isBuiltIn());
  }
  
  @Test
  public void emailAttribute() {
    assertEquals("email", UserAttribute.EMAIL.getName());
    assertTrue(UserAttribute.EMAIL.isBuiltIn());
  }
  
  @Test
  public void nameAttribute() {
    assertEquals("name", UserAttribute.NAME.getName());
    assertTrue(UserAttribute.NAME.isBuiltIn());
  }
  
  @Test
  public void avatarAttribute() {
    assertEquals("avatar", UserAttribute.AVATAR.getName());
    assertTrue(UserAttribute.AVATAR.isBuiltIn());
  }
  
  @Test
  public void firstNameAttribute() {
    assertEquals("firstName", UserAttribute.FIRST_NAME.getName());
    assertTrue(UserAttribute.FIRST_NAME.isBuiltIn());
  }
  
  @Test
  public void lastNameAttribute() {
    assertEquals("lastName", UserAttribute.LAST_NAME.getName());
    assertTrue(UserAttribute.LAST_NAME.isBuiltIn());
  }
  
  @Test
  public void anonymousAttribute() {
    assertEquals("anonymous", UserAttribute.ANONYMOUS.getName());
    assertTrue(UserAttribute.ANONYMOUS.isBuiltIn());
  }
  
  @Test
  public void customAttribute() {
    assertEquals("things", UserAttribute.forName("things").getName());
    assertFalse(UserAttribute.forName("things").isBuiltIn());
  }
}
