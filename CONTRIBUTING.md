Contributing to the LaunchDarkly SDK for Java
================================================

We encourage pull-requests and other contributions from the community. We've also published an [SDK contributor's guide](http://docs.launchdarkly.com/v1.0/docs/sdk-contributors-guide) that provides a detailed explanation of how our SDKs work.


Testing Proxy Settings
==================
Installation is your own journey, but your squid.conf file should have auth/access sections that look something like this:

```
auth_param basic program /usr/local/Cellar/squid/3.5.6/libexec/basic_ncsa_auth <SQUID_DIR>/passwords
auth_param basic realm proxy
acl authenticated proxy_auth REQUIRED
http_access allow authenticated
# And finally deny all other access to this proxy
http_access deny all
```

The contents of the passwords file is:
```
user:$apr1$sBfNiLFJ$7h3S84EgJhlbWM3v.90v61
```

The username/password is: user/password
