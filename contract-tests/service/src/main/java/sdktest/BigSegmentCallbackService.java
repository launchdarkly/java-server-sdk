package sdktest;

import java.net.URI;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BigSegmentCallbackService {
  private final URI baseUri;
  
  public BigSegmentCallbackService(URI baseUri) {
    this.baseUri = baseUri;
  }
  
  public void close() {
    try {
      Request request = new Request.Builder().url(baseUri.toURL()).method("DELETE", null).build();
      Response response = TestService.client.newCall(request).execute();
      assertOk(response, "");
    } catch (Exception e) {
      throw new RuntimeException(e); // all errors are unexpected here
    }
  }

  public <T> T post(String path, Object params, Class<T> responseClass) {
    try {
      String uri = baseUri.toString() + path;
      RequestBody body = RequestBody.create(
          TestService.gson.toJson(params == null ? "{}" : params),
          MediaType.parse("application/json"));
      Request request = new Request.Builder().url(uri).
            method("POST", body).build();
      Response response = TestService.client.newCall(request).execute();
      assertOk(response, path);
      if (responseClass == null) {
        return null;
      }
      return TestService.gson.fromJson(response.body().string(), responseClass);
    } catch (Exception e) {
      throw new RuntimeException(e); // all errors are unexpected here
    }
  }
  
  private void assertOk(Response response, String path) {
    if (!response.isSuccessful()) {
      String body = "";
      if (response.body() != null) {
        try {
          body = ": " + response.body().string();
        } catch (Exception e) {}
      }
      throw new RuntimeException("HTTP error " + response.code() + " from callback to " + baseUri + path + body);
    }
  }
}
