package com.launchdarkly.sdk.server.migrations;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Results of a method associated with a migration origin.
 * <p>
 * A result may either be a success, which will include a result type, or a failure.
 * <p>
 * The static methods are intended to be used to create results in a migration method.
 * <p>
 * An exception thrown from a migration method will be equivalent to using the
 * {@link MigrationMethodResult#Failure(Exception)} method.
 *
 * <pre><code>
 *   .read((payload) -&gt; {
 *       return MigrationMethodResult.Success("My Result!");
 *   })
 * </code></pre>
 * <pre><code>
 *   .read((payload) -&gt; {
 *       return MigrationMethodResult.Failure();
 *   })
 * </code></pre>
 *
 * @param <T> the type of the result
 */
public final class MigrationMethodResult<T> {
  private MigrationMethodResult(
      boolean success,
      @Nullable T result,
      @Nullable Exception exception) {
    this.success = success;
    this.result = result;
    this.exception = exception;
  }

  private final boolean success;
  private final T result;

  private final Exception exception;

  /**
   * Construct a method result representing a failure.
   * <p>
   * This method doesn't provide any information about the cause of the failure. It is recommended
   * to throw an exception or use {@link MigrationMethodResult#Failure(Exception)}.
   *
   * @return a method result
   * @param <U> the type of the method result
   */
  public static <U> MigrationMethodResult<U> Failure() {
    return new MigrationMethodResult<>(false, null, null);
  }

  /**
   * Construct a method result representing a failure based on an Exception.
   *
   * @param err the exception which caused the failure
   * @return a method result
   * @param <U> the type of the method result
   */
  public static <U> MigrationMethodResult<U> Failure(Exception err) {
    return new MigrationMethodResult<>(false, null, err);
  }

  /**
   * Create a successful method result.
   *
   * @param result the result of the method
   * @return a method result
   * @param <U> the type of the method result
   */
  public static <U> MigrationMethodResult<U> Success(U result) {
    return new MigrationMethodResult<>(true, result, null);
  }

  /**
   * Returns true if the method was successful.
   *
   * @return true if the method was successful
   */
  public boolean isSuccess() {
    return success;
  }

  /**
   * Get the result of the method.
   *
   * @return the result, or an empty optional if no result was produced
   */
  public Optional<T> getResult() {
    return Optional.ofNullable(result);
  }

  /**
   * Get the exception associated with the method or an empty optional if there
   * was no exception.
   *
   * @return the exception, or an empty optional if no result was produced
   */
  public Optional<Exception> getException() {
    return Optional.ofNullable(exception);
  }
}
