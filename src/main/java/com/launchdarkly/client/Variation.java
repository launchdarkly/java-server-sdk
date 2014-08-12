package com.launchdarkly.client;


import java.util.List;

public class Variation<E> {
  E value;
  int weight;
  List<String> matches;
}
