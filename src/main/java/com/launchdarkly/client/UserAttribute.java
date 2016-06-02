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
      return null; //Not used for evaluation.
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
  name {
    JsonElement get(LDUser user) {
      return user.getName();
    }
  },
  country {
    JsonElement get(LDUser user) {
      return user.getCountry();
    }
  },
  anonymous {
    JsonElement get(LDUser user) {
      return user.getAnonymous();
    }
  };

  /**
   * Gets value for Rule evaluation for a user.
   *
   * @param user
   * @return
   */
  abstract JsonElement get(LDUser user);
}
