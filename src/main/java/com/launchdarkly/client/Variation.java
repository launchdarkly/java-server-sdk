package com.launchdarkly.client;


import java.util.List;

class Variation<E> {
  E value;
  int weight;
  List<String> matches;


  public Variation() {

  }

  public boolean matchSegment(User user) {
    for (String match : matches) {
      if (user.getKey().equals(match)) {
        return true;
      }
    }
    return false;
  }
}
