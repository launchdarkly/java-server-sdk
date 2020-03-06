package com.launchdarkly.sdk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents a built-in or custom attribute name supported by {@link LDUser}.
 * <p>
 * This abstraction helps to distinguish attribute names from other {@link String} values, and also
 * improves efficiency in feature flag data structures and evaluations because built-in attributes
 * always reuse the same instances. 
 * 
 * @since 5.0.0
 */
@JsonAdapter(UserAttribute.UserAttributeTypeAdapter.class)
public final class UserAttribute {
  /**
   * Represents the user key attribute.
   */
  public static final UserAttribute KEY = new UserAttribute("key", u -> u.key);
  /**
   * Represents the secondary key attribute.
   */
  public static final UserAttribute SECONDARY_KEY = new UserAttribute("secondary", u -> u.secondary);
  /**
   * Represents the IP address attribute.
   */
  public static final UserAttribute IP = new UserAttribute("ip", u -> u.ip);
  /**
   * Represents the user key attribute.
   */
  public static final UserAttribute EMAIL = new UserAttribute("email", u -> u.email);
  /**
   * Represents the full name attribute.
   */
  public static final UserAttribute NAME = new UserAttribute("name", u -> u.name);
  /**
   * Represents the avatar URL attribute.
   */
  public static final UserAttribute AVATAR = new UserAttribute("avatar", u -> u.avatar);
  /**
   * Represents the first name attribute.
   */
  public static final UserAttribute FIRST_NAME = new UserAttribute("firstName", u -> u.firstName);
  /**
   * Represents the last name attribute.
   */
  public static final UserAttribute LAST_NAME = new UserAttribute("lastName", u -> u.lastName);
  /**
   * Represents the country attribute.
   */
  public static final UserAttribute COUNTRY = new UserAttribute("country", u -> u.country);
  /**
   * Represents the anonymous attribute.
   */
  public static final UserAttribute ANONYMOUS = new UserAttribute("anonymous", u -> u.anonymous);
  
  private static final Map<String, UserAttribute> BUILTINS = Maps.uniqueIndex(
      ImmutableList.of(KEY, SECONDARY_KEY, IP, EMAIL, NAME, AVATAR, FIRST_NAME, LAST_NAME, COUNTRY, ANONYMOUS),
      a -> a.getName());
  
  private final String name;
  final Function<LDUser, LDValue> builtInGetter;
  
  private UserAttribute(String name, Function<LDUser, LDValue> builtInGetter) {
    this.name = name;
    this.builtInGetter = builtInGetter;
  }
  
  /**
   * Returns a UserAttribute instance for the specified attribute name.
   * <p>
   * For built-in attributes, the same instances are always reused and {@link #isBuiltIn()} will
   * return true. For custom attributes, a new instance is created and {@link #isBuiltIn()} will
   * return false.
   * 
   * @param name the attribute name
   * @return a {@link UserAttribute}
   */
  public static UserAttribute forName(String name) {
    UserAttribute a = BUILTINS.get(name);
    return a != null ? a : new UserAttribute(name, null);
  }
  
  /**
   * Returns the case-sensitive attribute name.
   * 
   * @return the attribute name
   */
  public String getName() {
    return name;
  }
  
  /**
   * Returns true for a built-in attribute or false for a custom attribute.
   * 
   * @return true if it is a built-in attribute
   */
  public boolean isBuiltIn() {
    return builtInGetter != null;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof UserAttribute) {
      UserAttribute o = (UserAttribute)other;
      if (isBuiltIn() || o.isBuiltIn()) {
        return this == o; // faster comparison since built-in instances are interned
      }
      return name.equals(o.name);
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return isBuiltIn() ? super.hashCode() : name.hashCode();
  }
  
  @Override
  public String toString() {
    return name;
  }
  
  static final class UserAttributeTypeAdapter extends TypeAdapter<UserAttribute>{    
    @Override
    public UserAttribute read(JsonReader reader) throws IOException {
      return UserAttribute.forName(reader.nextString());
    }
  
    @Override
    public void write(JsonWriter writer, UserAttribute value) throws IOException {
      writer.value(value.getName());
    }
  }
}
