package com.launchdarkly.client;

/**
 * A {@code User} object contains specific attributes of a user browsing your site. The primary property is their {@code key},
 * which must uniquely identify each user. For authenticated users, this may be a username or e-mail address. For anonymous users,
 * this could be an IP address or session ID.
 */
public class User {
  private String key;

  public User() {

  }

  /**
   * Create a user with the given key
   * @param key a {@code String} that uniquely identifies a user
   */
  public User(String key) {
    this.key = key;
  }

  /**
   * Fetch the key for the user
   * @return the user's unique key
   */
  String getKey() {
    return key;
  }
}
