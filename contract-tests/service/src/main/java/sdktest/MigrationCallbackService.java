package sdktest;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.net.URL;

public class MigrationCallbackService {
  private final URL serviceUrl;

  public MigrationCallbackService(URL serviceUrl) {
    this.serviceUrl = serviceUrl;
  }

  public String post(String payload) {
    try {
      RequestBody body = RequestBody.create(
        payload != null ? payload : "",
        MediaType.parse("application/text"));
      Request request = new Request.Builder().url(serviceUrl).
        method("POST", body).build();
      Response response = TestService.client.newCall(request).execute();
      if(!response.isSuccessful()) {
        throw new RuntimeException("Non success status code.");
      }
      return response.body().string();
    } catch (Exception e) {
      throw new RuntimeException(e); // all errors are unexpected here
    }
  }
}
