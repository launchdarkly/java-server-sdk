package sdktest;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.net.URI;

public class HookCallbackService {
  private final URI serviceUri;

  public HookCallbackService(URI serviceUri) {
    this.serviceUri = serviceUri;
  }

  public void post(Object params) {
    try {
      RequestBody body = RequestBody.create(
          TestService.gson.toJson(params == null ? "{}" : params),
          MediaType.parse("application/json"));
      Request request = new Request.Builder().url(serviceUri.toString()).
          method("POST", body).build();
      Response response = TestService.client.newCall(request).execute();
      assertOk(response);
    } catch (Exception e) {
      throw new RuntimeException(e); // all errors are unexpected here
    }
  }

  private void assertOk(Response response) {
    if (!response.isSuccessful()) {
      String body = "";
      if (response.body() != null) {
        try {
          body = ": " + response.body().string();
        } catch (Exception e) {}
      }
      throw new RuntimeException("HTTP error " + response.code() + " from callback to " + serviceUri + body);
    }
  }
}
