package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorKind;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.Status;
import com.launchdarkly.testhelpers.TypeBehavior;

import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

@SuppressWarnings("javadoc")
public class DataSourceStatusProviderTypesTest {
  @Test
  public void statusProperties() {
    Instant time = Instant.ofEpochMilli(10000);
    ErrorInfo e = ErrorInfo.fromHttpError(401);
    Status s = new Status(State.VALID, time, e);
    assertThat(s.getState(), equalTo(State.VALID));
    assertThat(s.getStateSince(), equalTo(time));
    assertThat(s.getLastError(), sameInstance(e));
  }
  
  @Test
  public void statusEquality() {
    List<TypeBehavior.ValueFactory<Status>> allPermutations = new ArrayList<>();
    for (State state: State.values()) {
      for (Instant time: new Instant[] { Instant.ofEpochMilli(1000), Instant.ofEpochMilli(2000) }) {
        for (ErrorInfo e: new ErrorInfo[] { null, ErrorInfo.fromHttpError(400), ErrorInfo.fromHttpError(401) }) {
          allPermutations.add(() -> new Status(state, time, e));
        }
      }
    }
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
  
  @Test
  public void statusStringRepresentation() {
    Status s1 = new Status(State.VALID, Instant.now(), null);
    assertThat(s1.toString(), equalTo("Status(VALID," + s1.getStateSince() + ",null)"));

    Status s2 = new Status(State.VALID, Instant.now(), ErrorInfo.fromHttpError(401));
    assertThat(s2.toString(), equalTo("Status(VALID," + s2.getStateSince() + "," + s2.getLastError() + ")"));
  }
  
  @Test
  public void errorInfoProperties() {
    Instant time = Instant.ofEpochMilli(10000);
    ErrorInfo e1 = new ErrorInfo(ErrorKind.ERROR_RESPONSE, 401, "nope", time);
    assertThat(e1.getKind(), equalTo(ErrorKind.ERROR_RESPONSE));
    assertThat(e1.getStatusCode(), equalTo(401));
    assertThat(e1.getMessage(), equalTo("nope"));
    assertThat(e1.getTime(), equalTo(time));
    
    ErrorInfo e2 = ErrorInfo.fromHttpError(401);
    assertThat(e2.getKind(), equalTo(ErrorKind.ERROR_RESPONSE));
    assertThat(e2.getStatusCode(), equalTo(401));
    assertThat(e2.getMessage(), nullValue());
    assertThat(e2.getTime(), not(nullValue()));
    
    Exception ex = new Exception("sorry");
    ErrorInfo e3 = ErrorInfo.fromException(ErrorKind.UNKNOWN, ex);
    assertThat(e3.getKind(), equalTo(ErrorKind.UNKNOWN));
    assertThat(e3.getStatusCode(), equalTo(0));
    assertThat(e3.getMessage(), equalTo(ex.toString()));
    assertThat(e3.getTime(), not(nullValue()));
  }
  
  @Test
  public void errorInfoEquality() {
    List<TypeBehavior.ValueFactory<ErrorInfo>> allPermutations = new ArrayList<>();
    for (ErrorKind kind: ErrorKind.values()) {
      for (int statusCode: new int[] { 0, 1 }) {
        for (String message: new String[] { null, "a", "b" }) {
          for (Instant time: new Instant[] { Instant.ofEpochMilli(1000), Instant.ofEpochMilli(2000) }) {
            allPermutations.add(() -> new ErrorInfo(kind, statusCode, message, time));
          }
        }
      }
    }
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
  
  @Test
  public void errorStringRepresentation() {
    ErrorInfo e1 = new ErrorInfo(ErrorKind.ERROR_RESPONSE, 401, null, Instant.now());
    assertThat(e1.toString(), equalTo("ERROR_RESPONSE(401)@" + e1.getTime()));

    ErrorInfo e2 = new ErrorInfo(ErrorKind.ERROR_RESPONSE, 401, "nope", Instant.now());
    assertThat(e2.toString(), equalTo("ERROR_RESPONSE(401,nope)@" + e2.getTime()));

    ErrorInfo e3 = new ErrorInfo(ErrorKind.NETWORK_ERROR, 0, "hello", Instant.now());
    assertThat(e3.toString(), equalTo("NETWORK_ERROR(hello)@" + e3.getTime()));

    ErrorInfo e4 = new ErrorInfo(ErrorKind.STORE_ERROR, 0, null, Instant.now());
    assertThat(e4.toString(), equalTo("STORE_ERROR@" + e4.getTime()));

    ErrorInfo e5 = new ErrorInfo(ErrorKind.UNKNOWN, 0, null, null);
    assertThat(e5.toString(), equalTo("UNKNOWN"));
  }
}
