package sdktest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.testhelpers.httptest.Handlers;
import com.launchdarkly.testhelpers.httptest.HttpServer;
import com.launchdarkly.testhelpers.httptest.RequestContext;
import com.launchdarkly.testhelpers.httptest.SimpleRouter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;

import sdktest.Representations.CommandParams;
import sdktest.Representations.CreateInstanceParams;
import sdktest.Representations.Status;

public class TestService {
  private static final int PORT = 8000;
  private static final String[] CAPABILITIES = new String[]{
    "server-side",
    "strongly-typed",
    "all-flags-client-side-only",
    "all-flags-details-only-for-tracked-flags",
    "all-flags-with-reasons",
    "big-segments",
    "context-type",
    "service-endpoints",
    "tags",
    "filtering",
    "migrations",
    "event-sampling",
    "inline-context",
    "anonymous-redaction",
    "evaluation-hooks"
  };

  static final Gson gson = new GsonBuilder().serializeNulls().create();

  static final OkHttpClient client = new OkHttpClient();

  private final Map<String, SdkClientEntity> clients = new ConcurrentHashMap<String, SdkClientEntity>();
  private final AtomicInteger clientCounter = new AtomicInteger(0);
  private final String clientVersion;

  private TestService() {
    LDClient dummyClient = new LDClient("", new LDConfig.Builder().offline(true).build());
    clientVersion = dummyClient.version();
    try {
      dummyClient.close();
    } catch (Exception e) {}
  }

  @SuppressWarnings("serial")
  public static class BadRequestException extends Exception {
    public BadRequestException(String message) {
      super(message);
    }
  }

  public static void main(String[] args) throws Exception {
    TestService service = new TestService();

    SimpleRouter router = new SimpleRouter()
        .add("GET", "/", ctx -> service.writeJson(diableKeepAlive(ctx), service.getStatus()))
        .add("DELETE", "/", ctx -> service.forceQuit())
        .add("POST", "/", ctx -> service.postCreateClient(diableKeepAlive(ctx)))
        .addRegex("POST", Pattern.compile("/clients/(.*)"), ctx -> service.postClientCommand(diableKeepAlive(ctx)))
        .addRegex("DELETE", Pattern.compile("/clients/(.*)"), ctx -> service.deleteClient(diableKeepAlive(ctx)));

    HttpServer server = HttpServer.start(PORT, router);
    server.getRecorder().setEnabled(false); // don't accumulate a request log

    System.out.println("Listening on port " + PORT);

    // need to explicitly sleep because HttpServer now starts as a daemon thread
    while (true) {
      Thread.sleep(1000);
    }
  }

  private static RequestContext diableKeepAlive(RequestContext ctx) {
      ctx.addHeader("Connection", "close");
      return ctx;
  }

  private Status getStatus() {
    Status rep = new Status();
    rep.capabilities = CAPABILITIES;
    rep.clientVersion = clientVersion;
    return rep;
  }

  private void forceQuit() {
    System.out.println("Test harness has told us to quit");
    System.exit(0);
  }

  private void postCreateClient(RequestContext ctx) {
    CreateInstanceParams params = readJson(ctx, CreateInstanceParams.class);

    String clientId = String.valueOf(clientCounter.incrementAndGet());
    SdkClientEntity client = new SdkClientEntity(this, params);

    clients.put(clientId, client);

    ctx.addHeader("Location", "/clients/" + clientId);
  }

  private void postClientCommand(RequestContext ctx) {
    CommandParams params = readJson(ctx, CommandParams.class);

    String clientId = ctx.getPathParam(0);
    SdkClientEntity client = clients.get(clientId);
    if (client == null) {
      ctx.setStatus(404);
    } else {
      try {
        Object resp = client.doCommand(params);
        ctx.setStatus(202);
        if (resp != null) {
          String json = gson.toJson(resp);
          client.logger.info("Sending response: {}", json);
          writeJson(ctx, resp);
        }
      } catch (BadRequestException e) {
        ctx.setStatus(400);
      } catch (Exception e) {
        client.logger.error("Unexpected exception: {}", e);
        ctx.setStatus(500);
      }
    }
  }

  private void deleteClient(RequestContext ctx) {
    String clientId = ctx.getPathParam(0);
    SdkClientEntity client = clients.get(clientId);
    if (client == null) {
      ctx.setStatus(404);
    } else {
      client.close();
    }
  }

  private <T> T readJson(RequestContext ctx, Class<T> paramsClass) {
    return gson.fromJson(ctx.getRequest().getBody(), paramsClass);
  }

  private void writeJson(RequestContext ctx, Object data) {
    String json = gson.toJson(data);
    Handlers.bodyJson(json).apply(ctx);
  }
}
