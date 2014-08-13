package com.launchdarkly.client;


import java.util.List;

class Variation<E> {
  E value;
  int weight;
  List<String> matches;


  public Variation() {

  }

  public boolean matchSegment(LDUser user) {
    for (String match : matches) {
      if (user.getKey().equals(match)) {
        return true;
      }
    }
    return false;
  }
}
