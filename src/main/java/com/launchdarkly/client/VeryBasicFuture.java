package com.launchdarkly.client;

import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;

import java.util.concurrent.Future;

/**
 * Very Basic {@link Future} implementation extending {@link BasicFuture} with no callback or return value.
 */
public class VeryBasicFuture extends BasicFuture<Void> {

  public VeryBasicFuture() {
    super(new NoOpFutureCallback());
  }

  static class NoOpFutureCallback implements FutureCallback<Void> {
    @Override
    public void completed(Void result) {
    }

    @Override
    public void failed(Exception ex) {
    }

    @Override
    public void cancelled() {
    }
  }
}
