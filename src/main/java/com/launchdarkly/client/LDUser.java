package com.launchdarkly.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A {@code LDUser} object contains specific attributes of a user browsing your site. The only mandatory property property is the {@code key},
 * which must uniquely identify each user. For authenticated users, this may be a username or e-mail address. For anonymous users,
 * this could be an IP address or session ID.
 *
 * Besides the mandatory {@code key}, {@code LDUser} supports two kinds of optional attributes: interpreted attributes (e.g. {@code ip} and {@code country})
 * and custom attributes.  LaunchDarkly can parse interpreted attributes and attach meaning to them. For example, from an {@code ip} address, LaunchDarkly can
 * do a geo IP lookup and determine the user's country.
 *
 * Custom attributes are not parsed by LaunchDarkly. They can be used in custom rules-- for example, a custom attribute such as "customer_ranking" can be used to
 * launch a feature to the top 10% of users on a site.
 */
public class LDUser {
  private String key;
  private String secondary;
  private String ip;
  private String email;
  private String name;
  private String avatar;
  private String firstName;
  private String lastName;
  private Boolean anonymous;

  private LDCountryCode country;
  private Map<String, JsonElement> custom;
  private static final Logger logger = LoggerFactory.getLogger(LDUser.class);


  LDUser() {

  }

  protected LDUser(Builder builder) {
    this.key = builder.key;
    this.ip = builder.ip;
    this.country = builder.country;
    this.secondary = builder.secondary;
    this.firstName = builder.firstName;
    this.lastName = builder.lastName;
    this.email = builder.email;
    this.name = builder.name;
    this.avatar = builder.avatar;
    this.anonymous = builder.anonymous;
    this.custom = new HashMap<String, JsonElement>(builder.custom);
  }

  /**
   * Create a user with the given key
   * @param key a {@code String} that uniquely identifies a user
   */
  public LDUser(String key) {
    this.key = key;
    this.custom = new HashMap<String, JsonElement>();
  }

  String getKey() {
    return key;
  }

  String getIp() { return ip; }

  LDCountryCode getCountry() { return country; }

  String getSecondary() { return secondary; }

  String getName() { return name; }

  String getFirstName() { return firstName; }

  String getLastName() { return lastName; }

  String getEmail() { return email; }

  String getAvatar() { return avatar; }

  Boolean getAnonymous() { return anonymous; }

  JsonElement getCustom(String key) {
    return custom.get(key);
  }

  /**
   * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> that helps construct {@link com.launchdarkly.client.LDUser} objects. Builder
   * calls can be chained, enabling the following pattern:
   * 
   * <pre>
   * LDUser user = new LDUser.Builder("key")
   *      .country("US")
   *      .ip("192.168.0.1")
   *      .build()
   * </pre>
   *
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
    private LDCountryCode country;
    private Map<String, JsonElement> custom;

    /**
     * Create a builder with the specified key
     * @param key the unique key for this user
     */
    public Builder(String key) {
      this.key = key;
      this.custom = new HashMap<String, JsonElement>();
    }

    /**
     * Set the IP for a user
     * @param s the IP address for the user
     * @return the builder
     */
    public Builder ip(String s) {
      this.ip = s;
      return this;
    }

    public Builder secondary(String s) {
      this.secondary = s;
      return this;
    }

    /**
     * Set the country for a user. The country should be a valid <a href="http://en.wikipedia.org/wiki/ISO_3166-1">ISO 3166-1</a>
     * alpha-2 or alpha-3 code. If it is not a valid ISO-3166-1 code, an attempt will be made to look up the country by its name.
     * If that fails, a warning will be logged, and the country will not be set.
     * @param s the country for the user
     * @return the builder
     */
    public Builder country(String s) {
      country = LDCountryCode.getByCode(s, false);

      if (country == null) {
        List<LDCountryCode> codes = LDCountryCode.findByName("^" + Pattern.quote(s) + ".*");

        if (codes.isEmpty()) {
          logger.warn("Invalid country. Expected valid ISO-3166-1 code: " + s);
        }
        else if (codes.size() > 1) {
          // See if any of the codes is an exact match
          for (LDCountryCode c : codes) {
            if (c.getName().equals(s)) {
              country = c;
              return this;
            }
          }
          logger.warn("Ambiguous country. Provided code matches multiple countries: " + s);
          country = codes.get(0);
        }
        else {
          country = codes.get(0);
        }

      }
      return this;
    }

    /**
     * Set the country for a user.
     *
     * @param country the country for the user
     * @return the builder
     */
    public Builder country(LDCountryCode country) {
      this.country = country;
      return this;
    }

    /**
     * Sets the user's first name
     * @param firstName the user's first name
     * @return the builder
     */
    public Builder firstName(String firstName) {
      this.firstName = firstName;
      return this;
    }

    /**
     * Sets whether this user is anonymous
     * @param anonymous whether the user is anonymous
     * @return the builder
     */
    public Builder anonymous(boolean anonymous) {
      this.anonymous = anonymous;
      return this;
    }

    /**
     * Sets the user's last name
     * @param lastName the user's last name
     * @return the builder
     */
    public Builder lastName(String lastName) {
      this.lastName = lastName;
      return this;
    }

    /**
     * Sets the user's full name
     * @param name the user's full name
     * @return the builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the user's avatar
     * @param avatar the user's avatar
     * @return the builder
     */
    public Builder avatar(String avatar) {
      this.avatar = avatar;
      return this;
    }

    /**
     * Sets the user's e-mail address
     * @param email the e-mail address
     * @return the builder
     */
    public Builder email(String email) {
      this.email = email;
      return this;
    }

    /**
     * Add a {@link java.lang.String}-valued custom attribute
     * @param k the key for the custom attribute
     * @param v the value for the custom attribute
     * @return the builder
     */
    public Builder custom(String k, String v) {
      custom.put(k, new JsonPrimitive(v));
      return this;
    }

    /**
     * Add a {@link java.lang.Number}-valued custom attribute
     * @param k the key for the custom attribute
     * @param n the value for the custom attribute
     * @return the builder
     */
    public Builder custom(String k, Number n) {
      custom.put(k, new JsonPrimitive(n));
      return this;
    }

    /**
     * Add a {@link java.lang.Boolean}-valued custom attribute
     * @param k the key for the custom attribute
     * @param b the value for the custom attribute
     * @return the builder
     */
    public Builder custom(String k, Boolean b) {
      custom.put(k, new JsonPrimitive(b));
      return this;
    }

    /**
     * Add a list of {@link java.lang.String}-valued custom attributes
     * @param k the key for the list
     * @param vs the values for the attribute
     * @return the builder
     */
    public Builder custom(String k, List<String> vs) {
      JsonArray array = new JsonArray();
      for (String v : vs) {
        array.add(new JsonPrimitive(v));
      }
      custom.put(k, array);
      return this;
    }

    /**
     * Build the configured {@link com.launchdarkly.client.LDUser} object
     * @return the {@link com.launchdarkly.client.LDUser} configured by this builder
     */
    public LDUser build() {
      return new LDUser(this);
    }
  }
}
