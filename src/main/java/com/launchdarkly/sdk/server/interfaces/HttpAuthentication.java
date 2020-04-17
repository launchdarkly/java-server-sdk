package com.launchdarkly.sdk.server.interfaces;

/**
 * Represents a supported method of HTTP authentication, including proxy authentication.
 * 
 * @since 4.13.0
 */
public interface HttpAuthentication {
  /**
   * Computes the {@code Authorization} or {@code Proxy-Authorization} header for an authentication challenge.
   * 
   * @param challenges the authentication challenges provided by the server, if any (may be empty if this is
   *   pre-emptive authentication) 
   * @return the value for the authorization request header
   */
  String provideAuthorization(Iterable<Challenge> challenges);
  
  /**
   * Properties of an HTTP authentication challenge.
   */
  public static class Challenge {
    private final String scheme;
    private final String realm;
    
    /**
     * Constructs an instance.
     * 
     * @param scheme the authentication scheme
     * @param realm the authentication realm or null
     */
    public Challenge(String scheme, String realm) {
      this.scheme = scheme;
      this.realm = realm;
    }
    
    /**
     * The authentication scheme, such as "basic".
     * @return the authentication scheme
     */
    public String getScheme() {  
      return scheme;
    }
    
    /**
     * The authentication realm, if any.
     * @return the authentication realm or null
     */
    public String getRealm() {
      return realm;
    }
  }
}
