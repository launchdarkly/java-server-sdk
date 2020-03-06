package com.launchdarkly.sdk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A {@code LDUser} object contains specific attributes of a user browsing your site.
 * <p>
 * The only mandatory property is the {@code key}, which must uniquely identify each user; this could be a username
 * or email address for authenticated users, or a session ID for anonymous users. All other built-in properties are
 * optional. You may also define custom properties with arbitrary names and values.
 * <p>
 * For a fuller description of user attributes and how they can be referenced in feature flag rules, see the reference
 * guides on <a href="https://docs.launchdarkly.com/home/managing-users/user-attributes">Setting user attributes</a>
 * and <a href="https://docs.launchdarkly.com/home/managing-flags/targeting-users">Targeting users</a>.
 * <p> 
 * If you want to pass an LDUser object to the front end to be used with the JavaScript SDK, cal
 */
public class LDUser {
  private static final Logger logger = LoggerFactory.getLogger(LDUser.class);
  private static final Gson defaultGson = new Gson();

  // Note that these fields are all stored internally as LDValue rather than String so that
  // we don't waste time repeatedly converting them to LDValue in the rule evaluation logic.
  final LDValue key;
  final LDValue secondary;
  final LDValue ip;
  final LDValue email;
  final LDValue name;
  final LDValue avatar;
  final LDValue firstName;
  final LDValue lastName;
  final LDValue anonymous;
  final LDValue country;
  final Map<UserAttribute, LDValue> custom;
  Set<UserAttribute> privateAttributeNames;

  protected LDUser(Builder builder) {
    if (builder.key == null || builder.key.equals("")) {
      logger.warn("User was created with null/empty key");
    }
    this.key = LDValue.of(builder.key);
    this.ip = LDValue.of(builder.ip);
    this.country = LDValue.of(builder.country);
    this.secondary = LDValue.of(builder.secondary);
    this.firstName = LDValue.of(builder.firstName);
    this.lastName = LDValue.of(builder.lastName);
    this.email = LDValue.of(builder.email);
    this.name = LDValue.of(builder.name);
    this.avatar = LDValue.of(builder.avatar);
    this.anonymous = builder.anonymous == null ? LDValue.ofNull() : LDValue.of(builder.anonymous);
    this.custom = builder.custom == null ? null : ImmutableMap.copyOf(builder.custom);
    this.privateAttributeNames = builder.privateAttributes == null ? null : ImmutableSet.copyOf(builder.privateAttributes);
  }

  /**
   * Create a user with the given key
   *
   * @param key a {@code String} that uniquely identifies a user
   */
  public LDUser(String key) {
    this.key = LDValue.of(key);
    this.secondary = this.ip = this.email = this.name = this.avatar = this.firstName = this.lastName = this.anonymous = this.country =
        LDValue.ofNull();
    this.custom = null;
    this.privateAttributeNames = null;
  }

  /**
   * Returns the user's unique key.
   * 
   * @return the user key as a string
   */
  public String getKey() {
    return key.stringValue();
  }
  
  /**
   * Returns the value of the secondary key property for the user, if set.
   * 
   * @return a string or null
   */
  public String getSecondary() {
    return secondary.stringValue();
  }

  /**
   * Returns the value of the IP property for the user, if set.
   * 
   * @return a string or null
   */
  public String getIp() {
    return ip.stringValue();
  }

  /**
   * Returns the value of the country property for the user, if set.
   * 
   * @return a string or null
   */
  public String getCountry() {
    return country.stringValue();
  }

  /**
   * Returns the value of the full name property for the user, if set.
   * 
   * @return a string or null
   */
  public String getName() {
    return name.stringValue();
  }

  /**
   * Returns the value of the first name property for the user, if set.
   * 
   * @return a string or null
   */
  public String getFirstName() {
    return firstName.stringValue();
  }

  /**
   * Returns the value of the last name property for the user, if set.
   * 
   * @return a string or null
   */
  public String getLastName() {
    return lastName.stringValue();
  }

  /**
   * Returns the value of the email property for the user, if set.
   * 
   * @return a string or null
   */
  public String getEmail() {
    return email.stringValue();
  }

  /**
   * Returns the value of the avatar property for the user, if set.
   * 
   * @return a string or null
   */
  public String getAvatar() {
    return avatar.stringValue();
  }

  /**
   * Returns true if this user was marked anonymous.
   * 
   * @return true for an anonymous user
   */
  public boolean isAnonymous() {
    return anonymous.booleanValue();
  }
  
  /**
   * Gets the value of a user attribute, if present.
   * <p>
   * This can be either a built-in attribute or a custom one. It returns the value using the {@link LDValue}
   * type, which can have any type that is supported in JSON. If the attribute does not exist, it returns
   * {@link LDValue#ofNull()}.
   * 
   * @param attribute the attribute to get
   * @return the attribute value or {@link LDValue#ofNull()}; will never be an actual null reference
   */
  public LDValue getAttribute(UserAttribute attribute) {
    if (attribute.isBuiltIn()) {
      return attribute.builtInGetter.apply(this);
    } else {
      return custom == null ? LDValue.ofNull() : LDValue.normalize(custom.get(attribute));
    }
  }

  /**
   * Returns an enumeration of all custom attribute names that were set for this user.
   * 
   * @return the custom attribute names
   */
  public Iterable<UserAttribute> getCustomAttributes() {
    return custom == null ? ImmutableList.of() : custom.keySet();
  }
  
  /**
   * Returns an enumeration of all attributes that were marked private for this user.
   * <p>
   * This does not include any attributes that were globally marked private in your SDK configuration.
   * 
   * @return the names of private attributes for this user
   */
  public Iterable<UserAttribute> getPrivateAttributes() {
    return privateAttributeNames == null ? ImmutableList.of() : privateAttributeNames;
  }
  
  /**
   * Tests whether an attribute has been marked private for this user.
   * 
   * @param attribute a built-in or custom attribute
   * @return true if the attribute was marked private on a per-user level
   */
  public boolean isAttributePrivate(UserAttribute attribute) {
    return privateAttributeNames != null && privateAttributeNames.contains(attribute);
  }

  /**
   * Converts the user data to its standard JSON representation.
   * <p>
   * This is the same format that the LaunchDarkly JavaScript browser SDK uses to represent users, so
   * it is the simplest way to pass user data to front-end code.
   * <p>
   * Do not pass the {@link LDUser} object to a reflection-based JSON encoder such as Gson. Although the
   * SDK uses Gson internally, it uses shading so that the Gson types are not exposed, so an external
   * instance of Gson will not recognize the type adapters that provide the correct format.
   * 
   * @return a JSON representation of the user
   */
  public String toJsonString() {
    return defaultGson.toJson(this);
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LDUser ldUser = (LDUser) o;

    return Objects.equals(key, ldUser.key) &&
        Objects.equals(secondary, ldUser.secondary) &&
        Objects.equals(ip, ldUser.ip) &&
        Objects.equals(email, ldUser.email) &&
        Objects.equals(name, ldUser.name) &&
        Objects.equals(avatar, ldUser.avatar) &&
        Objects.equals(firstName, ldUser.firstName) &&
        Objects.equals(lastName, ldUser.lastName) &&
        Objects.equals(anonymous, ldUser.anonymous) &&
        Objects.equals(country, ldUser.country) &&
        Objects.equals(custom, ldUser.custom) &&
        Objects.equals(privateAttributeNames, ldUser.privateAttributeNames);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, secondary, ip, email, name, avatar, firstName, lastName, anonymous, country, custom, privateAttributeNames);
  }

  /**
   * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> that helps construct {@link LDUser} objects. Builder
   * calls can be chained, enabling the following pattern:
   * <pre>
   * LDUser user = new LDUser.Builder("key")
   *      .country("US")
   *      .ip("192.168.0.1")
   *      .build()
   * </pre>
   */
  public static class Builder {
    private String key;
    private String secondary;
    private String ip;
    private String firstName;
    private String lastName;
    private String email;
    private String name;
    private String avatar;
    private Boolean anonymous;
    private String country;
    private Map<UserAttribute, LDValue> custom;
    private Set<UserAttribute> privateAttributes;

    /**
     * Creates a builder with the specified key.
     *
     * @param key the unique key for this user
     */
    public Builder(String key) {
      this.key = key;
    }

    /**
    * Creates a builder based on an existing user.
    *
    * @param user an existing {@code LDUser}
    */
    public Builder(LDUser user) {
      this.key = user.key.stringValue();
      this.secondary = user.secondary.stringValue();
      this.ip = user.ip.stringValue();
      this.firstName = user.firstName.stringValue();
      this.lastName = user.lastName.stringValue();
      this.email = user.email.stringValue();
      this.name = user.name.stringValue();
      this.avatar = user.avatar.stringValue();
      this.anonymous = user.anonymous.isNull() ? null : user.anonymous.booleanValue();
      this.country = user.country.stringValue();
      this.custom = user.custom == null ? null : new HashMap<>(user.custom);
      this.privateAttributes = user.privateAttributeNames == null ? null : new HashSet<>(user.privateAttributeNames);
    }
    
    /**
     * Sets the IP for a user.
     *
     * @param s the IP address for the user
     * @return the builder
     */
    public Builder ip(String s) {
      this.ip = s;
      return this;
    }

    /**
     * Sets the IP for a user, and ensures that the IP attribute is not sent back to LaunchDarkly.
     *
     * @param s the IP address for the user
     * @return the builder
     */
    public Builder privateIp(String s) {
      addPrivate(UserAttribute.IP);
      return ip(s);
    }

    /**
     * Sets the secondary key for a user. This affects
     * <a href="https://docs.launchdarkly.com/docs/targeting-users#section-targeting-rules-based-on-user-attributes">feature flag targeting</a>
     * as follows: if you have chosen to bucket users by a specific attribute, the secondary key (if set)
     * is used to further distinguish between users who are otherwise identical according to that attribute.
     * @param s the secondary key for the user
     * @return the builder
     */
    public Builder secondary(String s) {
      this.secondary = s;
      return this;
    }

    /**
     * Sets the secondary key for a user, and ensures that the secondary key attribute is not sent back to
     * LaunchDarkly.
     * @param s the secondary key for the user
     * @return the builder
     */
    public Builder privateSecondary(String s) {
      addPrivate(UserAttribute.SECONDARY_KEY);
      return secondary(s);
    }

    /**
     * Set the country for a user. Before version 5.0.0, this field was validated and normalized by the SDK
     * as an ISO-3166-1 country code before assignment. This behavior has been removed so that the SDK can
     * treat this field as a normal string, leaving the meaning of this field up to the application.
     *
     * @param s the country for the user
     * @return the builder
     */
    public Builder country(String s) {
      this.country = s;
      return this;
    }

    /**
     * Set the country for a user, and ensures that the country attribute will not be sent back to LaunchDarkly.
     * Before version 5.0.0, this field was validated and normalized by the SDK as an ISO-3166-1 country code
     * before assignment. This behavior has been removed so that the SDK can treat this field as a normal string,
     * leaving the meaning of this field up to the application.
     *
     * @param s the country for the user
     * @return the builder
     */
    public Builder privateCountry(String s) {
      addPrivate(UserAttribute.COUNTRY);
      return country(s);
    }

    /**
     * Sets the user's first name
     *
     * @param firstName the user's first name
     * @return the builder
     */
    public Builder firstName(String firstName) {
      this.firstName = firstName;
      return this;
    }


    /**
     * Sets the user's first name, and ensures that the first name attribute will not be sent back to LaunchDarkly.
     *
     * @param firstName the user's first name
     * @return the builder
     */
    public Builder privateFirstName(String firstName) {
      addPrivate(UserAttribute.FIRST_NAME);
      return firstName(firstName);
    }


    /**
     * Sets whether this user is anonymous.
     *
     * @param anonymous whether the user is anonymous
     * @return the builder
     */
    public Builder anonymous(boolean anonymous) {
      this.anonymous = anonymous;
      return this;
    }

    /**
     * Sets the user's last name.
     *
     * @param lastName the user's last name
     * @return the builder
     */
    public Builder lastName(String lastName) {
      this.lastName = lastName;
      return this;
    }

    /**
     * Sets the user's last name, and ensures that the last name attribute will not be sent back to LaunchDarkly.
     *
     * @param lastName the user's last name
     * @return the builder
     */
    public Builder privateLastName(String lastName) {
      addPrivate(UserAttribute.LAST_NAME);
      return lastName(lastName);
    }


    /**
     * Sets the user's full name.
     *
     * @param name the user's full name
     * @return the builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the user's full name, and ensures that the name attribute will not be sent back to LaunchDarkly.
     *
     * @param name the user's full name
     * @return the builder
     */
    public Builder privateName(String name) {
      addPrivate(UserAttribute.NAME);
      return name(name);
    }

    /**
     * Sets the user's avatar.
     *
     * @param avatar the user's avatar
     * @return the builder
     */
    public Builder avatar(String avatar) {
      this.avatar = avatar;
      return this;
    }

    /**
     * Sets the user's avatar, and ensures that the avatar attribute will not be sent back to LaunchDarkly.
     *
     * @param avatar the user's avatar
     * @return the builder
     */
    public Builder privateAvatar(String avatar) {
      addPrivate(UserAttribute.AVATAR);
      return avatar(avatar);
    }


    /**
     * Sets the user's e-mail address.
     *
     * @param email the e-mail address
     * @return the builder
     */
    public Builder email(String email) {
      this.email = email;
      return this;
    }

    /**
     * Sets the user's e-mail address, and ensures that the e-mail address attribute will not be sent back to LaunchDarkly.
     *
     * @param email the e-mail address
     * @return the builder
     */
    public Builder privateEmail(String email) {
      addPrivate(UserAttribute.EMAIL);
      return email(email);
    }

    /**
     * Adds a {@link java.lang.String}-valued custom attribute. When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param v the value for the custom attribute
     * @return the builder
     */
    public Builder custom(String k, String v) {
      return custom(k, LDValue.of(v));
    }

    /**
     * Adds an integer-valued custom attribute. When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param n the value for the custom attribute
     * @return the builder
     */
    public Builder custom(String k, int n) {
      return custom(k, LDValue.of(n));
    }

    /**
     * Adds a double-precision numeric custom attribute. When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param n the value for the custom attribute
     * @return the builder
     */
    public Builder custom(String k, double n) {
      return custom(k, LDValue.of(n));
    }

    /**
     * Add a boolean-valued custom attribute. When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param b the value for the custom attribute
     * @return the builder
     */
    public Builder custom(String k, boolean b) {
      return custom(k, LDValue.of(b));
    }

    /**
     * Add a custom attribute whose value can be any JSON type, using {@link LDValue}. When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param v the value for the custom attribute
     * @return the builder
     * @since 4.8.0
     */
    public Builder custom(String k, LDValue v) {
      if (k != null) {
        return customInternal(UserAttribute.forName(k), v);
      }
      return this;
    }
    
    private Builder customInternal(UserAttribute a, LDValue v) {
      if (a.isBuiltIn()) {
        logger.warn("Built-in attribute key: " + a.getName() + " added as custom attribute! This custom attribute will be ignored during feature flag evaluation");
      }
      if (custom == null) {
        custom = new HashMap<>();
      }
      custom.put(a, LDValue.normalize(v));
      return this;
    }
    
    /**
     * Add a {@link java.lang.String}-valued custom attribute that will not be sent back to LaunchDarkly.
     * When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param v the value for the custom attribute
     * @return the builder
     */
    public Builder privateCustom(String k, String v) {
      return privateCustom(k, LDValue.of(v));
    }

    /**
     * Add an int-valued custom attribute that will not be sent back to LaunchDarkly.
     * When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param n the value for the custom attribute
     * @return the builder
     */
    public Builder privateCustom(String k, int n) {
      return privateCustom(k, LDValue.of(n));
    }

    /**
     * Add a double-precision numeric custom attribute that will not be sent back to LaunchDarkly.
     * When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param n the value for the custom attribute
     * @return the builder
     */
    public Builder privateCustom(String k, double n) {
      return privateCustom(k, LDValue.of(n));
    }

    /**
     * Add a boolean-valued custom attribute that will not be sent back to LaunchDarkly.
     * When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param b the value for the custom attribute
     * @return the builder
     */
    public Builder privateCustom(String k, boolean b) {
      return privateCustom(k, LDValue.of(b));
    }

    /**
     * Add a custom attribute of any JSON type, that will not be sent back to LaunchDarkly.
     * When set to one of the
     * <a href="http://docs.launchdarkly.com/docs/targeting-users#targeting-based-on-user-attributes">built-in
     * user attribute keys</a>, this custom attribute will be ignored.
     *
     * @param k the key for the custom attribute
     * @param v the value for the custom attribute
     * @return the builder
     * @since 4.8.0
     */
    public Builder privateCustom(String k, LDValue v) {
      if (k != null) {
        UserAttribute a = UserAttribute.forName(k);
        addPrivate(a);
        return customInternal(a, v);
      }
      return this;
    }

    private void addPrivate(UserAttribute attribute) {
      if (privateAttributes == null) {
        privateAttributes = new HashSet<>();
      }
      privateAttributes.add(attribute);
    }
    
    /**
     * Builds the configured {@link LDUser} object.
     *
     * @return the {@link LDUser} configured by this builder
     */
    public LDUser build() {
      return new LDUser(this);
    }
  }
}
