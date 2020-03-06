package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class LDUserTest {
  private static enum OptionalStringAttributes {
    secondary(LDUser::getSecondary, LDUser.Builder::secondary, LDUser.Builder::privateSecondary),
    ip(LDUser::getIp, LDUser.Builder::ip, LDUser.Builder::privateIp),
    firstName(LDUser::getFirstName, LDUser.Builder::firstName, LDUser.Builder::privateFirstName),
    lastName(LDUser::getLastName, LDUser.Builder::lastName, LDUser.Builder::privateLastName),
    email(LDUser::getEmail, LDUser.Builder::email, LDUser.Builder::privateEmail),
    name(LDUser::getName, LDUser.Builder::name, LDUser.Builder::privateName),
    avatar(LDUser::getAvatar, LDUser.Builder::avatar, LDUser.Builder::privateAvatar),
    country(LDUser::getCountry, LDUser.Builder::country, LDUser.Builder::privateCountry);
    
    final UserAttribute attribute;
    final Function<LDUser, String> getter;
    final BiFunction<LDUser.Builder, String, LDUser.Builder> setter;
    final BiFunction<LDUser.Builder, String, LDUser.Builder> privateSetter;
    
    OptionalStringAttributes(
        Function<LDUser, String> getter,
        BiFunction<LDUser.Builder, String, LDUser.Builder> setter,
        BiFunction<LDUser.Builder, String, LDUser.Builder> privateSetter
      ) {
      this.attribute = UserAttribute.forName(this.name());
      this.getter = getter;
      this.setter = setter;
      this.privateSetter = privateSetter;
    }
  };
  
  @Test
  public void simpleConstructorSetsKey() {
    LDUser user = new LDUser("key");
    assertEquals("key", user.getKey());
    assertEquals(LDValue.of("key"), user.getAttribute(UserAttribute.KEY));
    for (OptionalStringAttributes a: OptionalStringAttributes.values()) {
      assertNull(a.toString(), a.getter.apply(user));
      assertEquals(a.toString(), LDValue.ofNull(), user.getAttribute(a.attribute));
    }
    assertThat(user.isAnonymous(), is(false));
    assertThat(user.getAttribute(UserAttribute.ANONYMOUS), equalTo(LDValue.ofNull()));
    assertThat(user.getAttribute(UserAttribute.forName("custom-attr")), equalTo(LDValue.ofNull()));
    assertThat(user.getCustomAttributes(), emptyIterable());
    assertThat(user.getPrivateAttributes(), emptyIterable());
  }
  
  @Test
  public void builderSetsOptionalStringAttribute() {
    for (OptionalStringAttributes a: OptionalStringAttributes.values()) {
      String value = "value-of-" + a.name();
      LDUser.Builder builder = new LDUser.Builder("key");
      a.setter.apply(builder, value);
      LDUser user = builder.build();
      for (OptionalStringAttributes a1: OptionalStringAttributes.values()) {
        if (a1 == a) {
          assertEquals(a.toString(), value, a1.getter.apply(user));
          assertEquals(a.toString(), LDValue.of(value), user.getAttribute(a1.attribute));
        } else {
          assertNull(a.toString(), a1.getter.apply(user));
          assertEquals(a.toString(), LDValue.ofNull(), user.getAttribute(a1.attribute)); 
        }
      }
      assertThat(user.isAnonymous(), is(false));
      assertThat(user.getAttribute(UserAttribute.ANONYMOUS), equalTo(LDValue.ofNull()));
      assertThat(user.getAttribute(UserAttribute.forName("custom-attr")), equalTo(LDValue.ofNull()));
      assertThat(user.getCustomAttributes(), emptyIterable());
      assertThat(user.getPrivateAttributes(), emptyIterable());
      assertFalse(user.isAttributePrivate(a.attribute));
    }
  }

  @Test
  public void builderSetsPrivateOptionalStringAttribute() {
    for (OptionalStringAttributes a: OptionalStringAttributes.values()) {
      String value = "value-of-" + a.name();
      LDUser.Builder builder = new LDUser.Builder("key");
      a.privateSetter.apply(builder, value);
      LDUser user = builder.build();
      for (OptionalStringAttributes a1: OptionalStringAttributes.values()) {
        if (a1 == a) {
          assertEquals(a.toString(), value, a1.getter.apply(user));
          assertEquals(a.toString(), LDValue.of(value), user.getAttribute(a1.attribute));
        } else {
          assertNull(a.toString(), a1.getter.apply(user));
          assertEquals(a.toString(), LDValue.ofNull(), user.getAttribute(a1.attribute)); 
        }
      }
      assertThat(user.isAnonymous(), is(false));
      assertThat(user.getAttribute(UserAttribute.ANONYMOUS), equalTo(LDValue.ofNull()));
      assertThat(user.getAttribute(UserAttribute.forName("custom-attr")), equalTo(LDValue.ofNull()));
      assertThat(user.getCustomAttributes(), emptyIterable());
      assertThat(user.getPrivateAttributes(), contains(a.attribute));
      assertTrue(user.isAttributePrivate(a.attribute));
    }
  }
  
  @Test
  public void builderSetsCustomAttributes() {
    LDValue boolValue = LDValue.of(true),
        intValue = LDValue.of(2),
        floatValue = LDValue.of(2.5),
        stringValue = LDValue.of("x"),
        jsonValue = LDValue.buildArray().build();
    LDUser user = new LDUser.Builder("key")
        .custom("custom-bool", boolValue.booleanValue())
        .custom("custom-int", intValue.intValue())
        .custom("custom-float", floatValue.floatValue())
        .custom("custom-double", floatValue.doubleValue())
        .custom("custom-string", stringValue.stringValue())
        .custom("custom-json", jsonValue)
        .build();
    Iterable<String> names = ImmutableList.of("custom-bool", "custom-int", "custom-float", "custom-double", "custom-string", "custom-json");
    assertThat(user.getAttribute(UserAttribute.forName("custom-bool")), equalTo(boolValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-int")), equalTo(intValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-float")), equalTo(floatValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-double")), equalTo(floatValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-string")), equalTo(stringValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-json")), equalTo(jsonValue));
    assertThat(ImmutableSet.copyOf(user.getCustomAttributes()),
        equalTo(ImmutableSet.copyOf(Iterables.transform(names, UserAttribute::forName))));
    assertThat(user.getPrivateAttributes(), emptyIterable());
    for (String name: names) {
      assertThat(name, user.isAttributePrivate(UserAttribute.forName(name)), is(false));
    }
  }

  @Test
  public void builderSetsPrivateCustomAttributes() {
    LDValue boolValue = LDValue.of(true),
        intValue = LDValue.of(2),
        floatValue = LDValue.of(2.5),
        stringValue = LDValue.of("x"),
        jsonValue = LDValue.buildArray().build();
    LDUser user = new LDUser.Builder("key")
        .privateCustom("custom-bool", boolValue.booleanValue())
        .privateCustom("custom-int", intValue.intValue())
        .privateCustom("custom-float", floatValue.floatValue())
        .privateCustom("custom-double", floatValue.doubleValue())
        .privateCustom("custom-string", stringValue.stringValue())
        .privateCustom("custom-json", jsonValue)
        .build();
    Iterable<String> names = ImmutableList.of("custom-bool", "custom-int", "custom-float", "custom-double", "custom-string", "custom-json");
    assertThat(user.getAttribute(UserAttribute.forName("custom-bool")), equalTo(boolValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-int")), equalTo(intValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-float")), equalTo(floatValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-double")), equalTo(floatValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-string")), equalTo(stringValue));
    assertThat(user.getAttribute(UserAttribute.forName("custom-json")), equalTo(jsonValue));
    assertThat(ImmutableSet.copyOf(user.getCustomAttributes()),
        equalTo(ImmutableSet.copyOf(Iterables.transform(names, UserAttribute::forName))));
    assertThat(ImmutableSet.copyOf(user.getPrivateAttributes()), equalTo(ImmutableSet.copyOf(user.getCustomAttributes())));
    for (String name: names) {
      assertThat(name, user.isAttributePrivate(UserAttribute.forName(name)), is(true));
    }
  }

  @Test
  public void canCopyUserWithBuilder() {
    LDUser user = new LDUser.Builder("key")
    .secondary("secondary")
    .ip("127.0.0.1")
    .firstName("Bob")
    .lastName("Loblaw")
    .email("bob@example.com")
    .name("Bob Loblaw")
    .avatar("image")
    .anonymous(false)
    .country("US")
    .custom("org", "LaunchDarkly")
    .build();
    
    assert(user.equals(new LDUser.Builder(user).build()));
  }

  @Test
  public void canSetAnonymous() {
    LDUser user1 = new LDUser.Builder("key").anonymous(true).build();
    assertThat(user1.isAnonymous(), is(true));
    assertThat(user1.getAttribute(UserAttribute.ANONYMOUS), equalTo(LDValue.of(true)));
    
    LDUser user2 = new LDUser.Builder("key").anonymous(false).build();
    assertThat(user2.isAnonymous(), is(false));
    assertThat(user2.getAttribute(UserAttribute.ANONYMOUS), equalTo(LDValue.of(false)));
  }

  @Test
  public void getAttributeGetsBuiltInAttributeEvenIfCustomAttrHasSameName() {
    LDUser user = new LDUser.Builder("key")
        .name("Jane")
        .custom("name", "Joan")
        .build();
    assertEquals(LDValue.of("Jane"), user.getAttribute(UserAttribute.forName("name")));
  }
  
  @Test
  public void testMinimalJsonEncoding() {
    LDUser user = new LDUser("userkey");
    String json = user.toJsonString();
    assertThat(json, equalTo("{\"key\":\"userkey\"}"));
  }

  @Test
  public void testDefaultJsonEncodingWithoutPrivateAttributes() {
    LDUser user = new LDUser.Builder("userkey")
        .secondary("s")
        .ip("i")
        .email("e")
        .name("n")
        .avatar("a")
        .firstName("f")
        .lastName("l")
        .country("c")
        .anonymous(true)
        .custom("c1", "v1")
        .build();
    LDValue json = LDValue.parse(user.toJsonString());
    assertThat(json, equalTo(
        LDValue.buildObject()
          .put("key", "userkey")
          .put("secondary", "s")
          .put("ip", "i")
          .put("email", "e")
          .put("name", "n")
          .put("avatar", "a")
          .put("firstName", "f")
          .put("lastName", "l")
          .put("country", "c")
          .put("anonymous", true)
          .put("custom", LDValue.buildObject().put("c1", "v1").build())
          .build()
          ));
  }

  @Test
  public void testDefaultJsonEncodingWithPrivateAttributes() {
    LDUser user = new LDUser.Builder("userkey")
        .email("e")
        .privateName("n")
        .build();
    LDValue json = LDValue.parse(user.toJsonString());
    assertThat(json, equalTo(
        LDValue.buildObject()
          .put("key", "userkey")
          .put("email", "e")
          .put("name", "n")
          .put("privateAttributeNames", LDValue.buildArray().add("name").build())
          .build()
          ));
  }
}
