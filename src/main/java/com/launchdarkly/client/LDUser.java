package com.launchdarkly.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  private String ip;
  private String country;
  private Map<String, JsonElement> custom;

  public LDUser() {

  }

  public LDUser(Builder builder) {
    this.key = builder.key;
    this.ip = builder.ip;
    this.country = builder.country;
    this.custom = new HashMap<String, JsonElement>(builder.custom);
  }

  /**
   * Create a user with the given key
   * @param key a {@code String} that uniquely identifies a user
   */
  public LDUser(String key) {
    this.key = key;
  }

  /**
   * Fetch the key for the user
   * @return the user's unique key
   */
  String getKey() {
    return key;
  }

  /**
   * A <a href="http://en.wikipedia.org/wiki/Builder_pattern">builder</a> that helps construct {@link com.launchdarkly.client.LDUser} objects. Builder
   * calls can be chained, enabling the following pattern:
   * <p>
   * <pre>
   * LDUser user = new LDUser.Builder("key)
   *      .country("US")
   *      .ip("192.168.0.1")
   *      .build()
   * </pre>
   * </p>
   *
   */
  public static class Builder {
    private String key;
    private String ip;
    private String country;
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

    /**
     * Set the country for a user
     * @param s the country for the user
     * @return the builder
     */
    public Builder country(String s) {
      this.country = s;
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
