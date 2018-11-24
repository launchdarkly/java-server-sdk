package testapp;

import com.launchdarkly.client.*;

public class TestApp {
  public static void main(String[] args) throws Exception {
    LDConfig config = new LDConfig.Builder()
      .offline(true)
      .build();
    LDClient client = new LDClient("fake-sdk-key", config);
    System.out.println("@@@ successfully created LD client @@@");
  }
}