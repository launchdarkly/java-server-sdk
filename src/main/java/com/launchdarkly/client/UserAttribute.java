package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

enum UserAttribute {
  key {
    LDValue get(LDUser user) {
      return user.getKey();
    }
  },
  secondary {
    LDValue get(LDUser user) {
      return null; //Not used for evaluation.
    }
  },
  ip {
    LDValue get(LDUser user) {
      return user.getIp();
    }
  },
  email {
    LDValue get(LDUser user) {
      return user.getEmail();
    }
  },
  avatar {
    LDValue get(LDUser user) {
      return user.getAvatar();
    }
  },
  firstName {
    LDValue get(LDUser user) {
      return user.getFirstName();
    }
  },
  lastName {
    LDValue get(LDUser user) {
      return user.getLastName();
    }
  },
  name {
    LDValue get(LDUser user) {
      return user.getName();
    }
  },
  country {
    LDValue get(LDUser user) {
      return user.getCountry();
    }
  },
  anonymous {
    LDValue get(LDUser user) {
      return user.getAnonymous();
    }
  };

  /**
   * Gets value for Rule evaluation for a user.
   *
   * @param user
   * @return
   */
  abstract LDValue get(LDUser user);
}
