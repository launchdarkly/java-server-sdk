package com.launchdarkly.client;

import com.google.gson.JsonElement;

enum UserAttribute {
  key {
    JsonElement get(LDUser user) {
      return user.getKey();
    }
  },
  secondary {
    JsonElement get(LDUser user) {
      return user.getSecondary();
    }
  },
  ip {
    JsonElement get(LDUser user) {
      return user.getIp();
    }
  },
  email {
    JsonElement get(LDUser user) {
      return user.getEmail();
    }
  },
  name {
    JsonElement get(LDUser user) {
      return user.getName();
    }
  },
  avatar {
    JsonElement get(LDUser user) {
      return user.getAvatar();
    }
  },
  firstName {
    JsonElement get(LDUser user) {
      return user.getFirstName();
    }
  },
  lastName {
    JsonElement get(LDUser user) {
      return user.getLastName();
    }
  },
  anonymous {
    JsonElement get(LDUser user) {
      return user.getAnonymous();
    }
  },
  country {
    JsonElement get(LDUser user) {
      return user.getCountry();
    }
  };

  abstract JsonElement get(LDUser user);
}
