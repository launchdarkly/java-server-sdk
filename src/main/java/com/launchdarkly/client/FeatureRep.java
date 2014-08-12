package com.launchdarkly.client;


import java.util.List;

public class FeatureRep<E> {
  String name;
  String key;
  String kind;
  Boolean on;
  List<Variation<E>> variations;
}
