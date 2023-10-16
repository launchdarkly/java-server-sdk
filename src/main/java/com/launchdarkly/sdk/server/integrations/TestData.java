package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.ArrayBuilder;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.KeyedItems;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * A mechanism for providing dynamically updatable feature flag state in a simplified form to an SDK
 * client in test scenarios.
 * <p>
 * Unlike {@link FileData}, this mechanism does not use any external resources. It provides only
 * the data that the application has put into it using the {@link #update(FlagBuilder)} method.
 * 
 * <pre><code>
 *     TestData td = TestData.dataSource();
 *     td.update(testData.flag("flag-key-1").booleanFlag().variationForAllUsers(true));
 *     
 *     LDConfig config = new LDConfig.Builder()
 *         .dataSource(td)
 *         .build();
 *     LDClient client = new LDClient(sdkKey, config);
 *     
 *     // flags can be updated at any time:
 *     td.update(testData.flag("flag-key-2")
 *         .variationForUser("some-user-key", true)
 *         .fallthroughVariation(false));
 * </code></pre> 
 * 
 * The above example uses a simple boolean flag, but more complex configurations are possible using
 * the methods of the {@link FlagBuilder} that is returned by {@link #flag(String)}. {@link FlagBuilder}
 * supports many of the ways a flag can be configured on the LaunchDarkly dashboard, but does not
 * currently support 1. rule operators other than "in" and "not in", or 2. percentage rollouts.  
 * <p>
 * If the same {@code TestData} instance is used to configure multiple {@code LDClient} instances,
 * any changes made to the data will propagate to all of the {@code LDClient}s.
 * 
 * @since 5.1.0
 * @see FileData
 */
public final class TestData implements ComponentConfigurer<DataSource> {
  private final Object lock = new Object();
  private final Map<String, ItemDescriptor> currentFlags = new HashMap<>();
  private final Map<String, FlagBuilder> currentBuilders = new HashMap<>();
  private final List<DataSourceImpl> instances = new CopyOnWriteArrayList<>();
  
  /**
   * Creates a new instance of the test data source.
   * <p>
   * See {@link TestData} for details.
   * 
   * @return a new configurable test data source
   */
  public static TestData dataSource() {
    return new TestData();
  }

  private TestData() {}
  
  /**
   * Creates or copies a {@link FlagBuilder} for building a test flag configuration.
   * <p>
   * If this flag key has already been defined in this {@code TestData} instance, then the builder
   * starts with the same configuration that was last provided for this flag.
   * <p>
   * Otherwise, it starts with a new default configuration in which the flag has {@code true} and
   * {@code false} variations, is {@code true} for all users when targeting is turned on and
   * {@code false} otherwise, and currently has targeting turned on. You can change any of those
   * properties, and provide more complex behavior, using the {@link FlagBuilder} methods.
   * <p>
   * Once you have set the desired configuration, pass the builder to {@link #update(FlagBuilder)}.
   * 
   * @param key the flag key
   * @return a flag configuration builder
   * @see #update(FlagBuilder)
   */
  public FlagBuilder flag(String key) {
    FlagBuilder existingBuilder;
    synchronized (lock) {
      existingBuilder = currentBuilders.get(key);
    }
    if (existingBuilder != null) {
      return new FlagBuilder(existingBuilder);
    }
    return new FlagBuilder(key).booleanFlag();
  }
  
  /**
   * Updates the test data with the specified flag configuration.
   * <p>
   * This has the same effect as if a flag were added or modified on the LaunchDarkly dashboard.
   * It immediately propagates the flag change to any {@code LDClient} instance(s) that you have
   * already configured to use this {@code TestData}. If no {@code LDClient} has been started yet,
   * it simply adds this flag to the test data which will be provided to any {@code LDClient} that
   * you subsequently configure.
   * <p>
   * Any subsequent changes to this {@link FlagBuilder} instance do not affect the test data,
   * unless you call {@link #update(FlagBuilder)} again.
   * 
   * @param flagBuilder a flag configuration builder
   * @return the same {@code TestData} instance
   * @see #flag(String)
   */
  public TestData update(FlagBuilder flagBuilder) {
    String key = flagBuilder.key;
    FlagBuilder clonedBuilder = new FlagBuilder(flagBuilder);
    ItemDescriptor newItem = null;
    
    synchronized (lock) {
      ItemDescriptor oldItem = currentFlags.get(key);
      int oldVersion = oldItem == null ? 0 : oldItem.getVersion();
      newItem = flagBuilder.createFlag(oldVersion + 1);
      currentFlags.put(key, newItem);
      currentBuilders.put(key, clonedBuilder);
    }
    
    for (DataSourceImpl instance: instances) {
      instance.updates.upsert(DataModel.FEATURES, key, newItem);
    }
    
    return this;
  }
  
  /**
   * Simulates a change in the data source status.
   * <p>
   * Use this if you want to test the behavior of application code that uses
   * {@link com.launchdarkly.sdk.server.LDClient#getDataSourceStatusProvider()} to track whether the data
   * source is having problems (for example, a network failure interruptsingthe streaming connection). It
   * does not actually stop the {@code TestData} data source from working, so even if you have simulated
   * an outage, calling {@link #update(FlagBuilder)} will still send updates.
   *
   * @param newState one of the constants defined by {@link com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State}
   * @param newError an {@link com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo} instance,
   *   or null
   * @return the same {@code TestData} instance
   */
  public TestData updateStatus(DataSourceStatusProvider.State newState, DataSourceStatusProvider.ErrorInfo newError) {
    for (DataSourceImpl instance: instances) {
      instance.updates.updateStatus(newState, newError);
    }
    return this;
  }
  
  @Override
  public DataSource build(ClientContext context) {
    DataSourceImpl instance = new DataSourceImpl(context.getDataSourceUpdateSink());
    synchronized (lock) {
      instances.add(instance);
    }
    return instance;
  }
  
  private FullDataSet<ItemDescriptor> makeInitData() {
    ImmutableMap<String, ItemDescriptor> copiedData;
    synchronized (lock) {
      copiedData = ImmutableMap.copyOf(currentFlags);
    }
    return new FullDataSet<>(ImmutableMap.of(DataModel.FEATURES, new KeyedItems<>(copiedData.entrySet())).entrySet());
  }
  
  private void closedInstance(DataSourceImpl instance) {
    synchronized (lock) {
      instances.remove(instance);
    }
  }
  
  /**
   * A builder for feature flag configurations to be used with {@link TestData}.
   * 
   * @see TestData#flag(String)
   * @see TestData#update(FlagBuilder)
   */
  public static final class FlagBuilder {
    private static final int TRUE_VARIATION_FOR_BOOLEAN = 0;
    private static final int FALSE_VARIATION_FOR_BOOLEAN = 1;
    
    final String key;
    int offVariation;
    boolean on;
    int fallthroughVariation;
    CopyOnWriteArrayList<LDValue> variations;
    private Long samplingRatio;
    private Long migrationCheckRatio;

    final Map<ContextKind, Map<Integer, ImmutableSet<String>>> targets = new TreeMap<>(); // TreeMap enforces ordering for test determinacy
    final List<FlagRuleBuilder> rules = new ArrayList<>();
    
    private FlagBuilder(String key) {
      this.key = key;
      this.on = true;
      this.variations = new CopyOnWriteArrayList<>();
    }
    
    private FlagBuilder(FlagBuilder from) {
      this.key = from.key;
      this.offVariation = from.offVariation;
      this.on = from.on;
      this.fallthroughVariation = from.fallthroughVariation;
      this.variations = new CopyOnWriteArrayList<>(from.variations);
      for (ContextKind contextKind: from.targets.keySet()) {
        this.targets.put(contextKind, new TreeMap<>(from.targets.get(contextKind)));
      }
      this.rules.addAll(from.rules);
    }
    
    private boolean isBooleanFlag() {
      return variations.size() == 2 &&
          variations.get(TRUE_VARIATION_FOR_BOOLEAN).equals(LDValue.of(true)) &&
          variations.get(FALSE_VARIATION_FOR_BOOLEAN).equals(LDValue.of(false));
    }
    
    /**
     * A shortcut for setting the flag to use the standard boolean configuration.
     * <p>
     * This is the default for all new flags created with {@link TestData#flag(String)}. The flag
     * will have two variations, {@code true} and {@code false} (in that order); it will return
     * {@code false} whenever targeting is off, and {@code true} when targeting is on if no other
     * settings specify otherwise.
     * 
     * @return the builder
     */
    public FlagBuilder booleanFlag() {
      if (isBooleanFlag()) {
        return this;
      }
      return variations(LDValue.of(true), LDValue.of(false))
          .fallthroughVariation(TRUE_VARIATION_FOR_BOOLEAN)
          .offVariation(FALSE_VARIATION_FOR_BOOLEAN);
    }
    
    /**
     * Sets targeting to be on or off for this flag.
     * <p>
     * The effect of this depends on the rest of the flag configuration, just as it does on the
     * real LaunchDarkly dashboard. In the default configuration that you get from calling
     * {@link TestData#flag(String)} with a new flag key, the flag will return {@code false}
     * whenever targeting is off, and {@code true} when targeting is on.
     * 
     * @param on true if targeting should be on
     * @return the builder
     */
    public FlagBuilder on(boolean on) {
      this.on = on;
      return this;
    }
    
    /**
     * Specifies the fallthrough variation for a boolean flag. The fallthrough is the value
     * that is returned if targeting is on and the context was not matched by a more specific
     * target or rule.
     * <p>
     * If the flag was previously configured with other variations, this also changes it to a
     * boolean flag.
     * 
     * @param value true if the flag should return true by default when targeting is on
     * @return the builder
     */
    public FlagBuilder fallthroughVariation(boolean value) {
      return this.booleanFlag().fallthroughVariation(variationForBoolean(value));
    }

    /**
     * Specifies the index of the fallthrough variation. The fallthrough is the variation
     * that is returned if targeting is on and the context was not matched by a more specific
     * target or rule.
     * 
     * @param variationIndex the desired fallthrough variation: 0 for the first, 1 for the second, etc.
     * @return the builder
     */
    public FlagBuilder fallthroughVariation(int variationIndex) {
      this.fallthroughVariation = variationIndex;
      return this;
    }

    /**
     * Specifies the off variation for a boolean flag. This is the variation that is returned
     * whenever targeting is off.
     * 
     * @param value true if the flag should return true when targeting is off
     * @return the builder
     */
    public FlagBuilder offVariation(boolean value) {
      return this.booleanFlag().offVariation(variationForBoolean(value));
    }

    /**
     * Specifies the index of the off variation. This is the variation that is returned
     * whenever targeting is off.
     * 
     * @param variationIndex the desired off variation: 0 for the first, 1 for the second, etc.
     * @return the builder
     */
    public FlagBuilder offVariation(int variationIndex) {
      this.offVariation = variationIndex;
      return this;
    }

    /**
     * Sets the flag to always return the specified boolean variation for all contexts.
     * <p>
     * Targeting is switched on, any existing targets or rules are removed, and the flag's variations are
     * set to true and false. The fallthrough variation is set to the specified value. The off variation is
     * left unchanged.
     *
     * @param variation the desired true/false variation to be returned for all contexts
     * @return the builder
     * @see #variationForAll(int)
     * @see #valueForAll(LDValue)
     * @since 5.10.0
     */
    public FlagBuilder variationForAll(boolean variation) {
      return booleanFlag().variationForAll(variationForBoolean(variation));
    }

    /**
     * Sets the flag to always return the specified variation for all contexts.
     * <p>
     * The variation is specified by number, out of whatever variation values have already been
     * defined. Targeting is switched on, and any existing targets or rules are removed. The fallthrough
     * variation is set to the specified value. The off variation is left unchanged.
     * 
     * @param variationIndex the desired variation: 0 for the first, 1 for the second, etc.
     * @return the builder
     * @see #variationForAll(boolean)
     * @see #valueForAll(LDValue)
     */
    public FlagBuilder variationForAll(int variationIndex) {
      return on(true).clearRules().clearTargets().fallthroughVariation(variationIndex);
    }

    /**
     * Sets the flag to always return the specified variation value for all users.
     * <p>
     * The value may be of any JSON type, as defined by {@link LDValue}. This method changes the
     * flag to have only a single variation, which is this value, and to return the same
     * variation regardless of whether targeting is on or off. Any existing targets or rules
     * are removed.
     * 
     * @param value the desired value to be returned for all users
     * @return the builder
     * @see #variationForAll(boolean)
     * @see #variationForAll(int)
     */
    public FlagBuilder valueForAll(LDValue value) {
      variations.clear();
      variations.add(value);
      return variationForAll(0);
    }
    
    /**
     * Sets the flag to return the specified boolean variation for a specific user key (that is,
     * for a context with that key whose context kind is "user") when targeting is on.
     * <p>
     * This has no effect when targeting is turned off for the flag.
     * <p>
     * If the flag was not already a boolean flag, this also changes it to a boolean flag.
     * 
     * @param userKey a user key
     * @param variation the desired true/false variation to be returned for this user when
     *   targeting is on
     * @return the builder
     * @see #variationForUser(String, int)
     * @see #variationForKey(ContextKind, String, boolean)
     */
    public FlagBuilder variationForUser(String userKey, boolean variation) {
      return variationForKey(ContextKind.DEFAULT, userKey, variation);
    }

    /**
     * Sets the flag to return the specified boolean variation for a specific context, identified
     * by context kind and key, when targeting is on.
     * <p>
     * This has no effect when targeting is turned off for the flag.
     * <p>
     * If the flag was not already a boolean flag, this also changes it to a boolean flag.
     * 
     * @param contextKind the context kind
     * @param key the context key
     * @param variation the desired true/false variation to be returned for this context when
     *   targeting is on
     * @return the builder
     * @see #variationForKey(ContextKind, String, int)
     * @see #variationForUser(String, boolean)
     * @since 6.0.0
     */
    public FlagBuilder variationForKey(ContextKind contextKind, String key, boolean variation) {
      return booleanFlag().variationForKey(contextKind, key, variationForBoolean(variation));
    }
    
    /**
     * Sets the flag to return the specified variation for a specific user key (that is,
     * for a context with that key whose context kind is "user") when targeting is on.
     * <p>
     * This has no effect when targeting is turned off for the flag.
     * <p>
     * The variation is specified by number, out of whatever variation values have already been
     * defined.
     * 
     * @param userKey a user key
     * @param variationIndex the desired variation to be returned for this user when targeting is on:
     *   0 for the first, 1 for the second, etc.
     * @return the builder
     * @see #variationForKey(ContextKind, String, int)
     * @see #variationForUser(String, boolean)
     */
    public FlagBuilder variationForUser(String userKey, int variationIndex) {
      return variationForKey(ContextKind.DEFAULT, userKey, variationIndex);
    }

    /**
     * Sets the flag to return the specified boolean variation for a specific context, identified
     * by context kind and key, when targeting is on.
     * <p>
     * This has no effect when targeting is turned off for the flag.
     * <p>
     * If the flag was not already a boolean flag, this also changes it to a boolean flag.
     * 
     * @param contextKind the context kind
     * @param key the context key
     * @param variationIndex the desired variation to be returned for this context when targeting is on:
     *   0 for the first, 1 for the second, etc.
     * @return the builder
     * @see #variationForKey(ContextKind, String, boolean)
     * @see #variationForUser(String, int)
     * @since 6.0.0
     */
    public FlagBuilder variationForKey(ContextKind contextKind, String key, int variationIndex) {
      if (contextKind == null) {
        contextKind = ContextKind.DEFAULT;
      }
      Map<Integer, ImmutableSet<String>> keysByVariation = targets.get(contextKind);
      if (keysByVariation == null) {
        keysByVariation = new TreeMap<>(); // TreeMap keeps variations in order for test determinacy
        targets.put(contextKind, keysByVariation);
      }
      for (int i = 0; i < variations.size(); i++) {
        ImmutableSet<String> keys = keysByVariation.get(i);
        if (i == variationIndex) {
          if (keys == null) {
            keysByVariation.put(i, ImmutableSortedSet.of(key));
          } else if (!keys.contains(key)) {
            keysByVariation.put(i, ImmutableSortedSet.<String>naturalOrder().addAll(keys).add(key).build());
          }
        } else {
          if (keys != null && keys.contains(key)) {
            keysByVariation.put(i, ImmutableSortedSet.copyOf(Iterables.filter(keys, k -> !k.equals(key))));
          }
        }
      }
      // Note, we use ImmutableSortedSet just to make the output determinate for our own testing
      return this;
    }

    /**
     * Changes the allowable variation values for the flag.
     * <p>
     * The value may be of any JSON type, as defined by {@link LDValue}. For instance, a boolean flag
     * normally has {@code LDValue.of(true), LDValue.of(false)}; a string-valued flag might have
     * {@code LDValue.of("red"), LDValue.of("green")}; etc.
     * 
     * @param values the desired variations
     * @return the builder
     */
    public FlagBuilder variations(LDValue... values) {
      variations.clear();
      for (LDValue v: values) {
        variations.add(v);        
      }
      return this;
    }

    /**
     * Starts defining a flag rule, using the "is one of" operator. This matching expression only
     * applies to contexts of a specific kind.
     * <p>
     * For example, this creates a rule that returns {@code true} if the name attribute for the
     * "company" context is "Ella" or "Monsoon":
     * 
     * <pre><code>
     *     testData.flag("flag")
     *         .ifMatch(ContextKind.of("company"), "name",
     *             LDValue.of("Ella"), LDValue.of("Monsoon"))
     *         .thenReturn(true));
     * </code></pre>
     * 
     * @param contextKind the context kind to match
     * @param attribute the attribute to match against
     * @param values values to compare to
     * @return a {@link FlagRuleBuilder}; call {@link FlagRuleBuilder#thenReturn(boolean)} or
     *   {@link FlagRuleBuilder#thenReturn(int)} to finish the rule, or add more tests with another
     *   method like {@link FlagRuleBuilder#andMatch(ContextKind, String, LDValue...)}
     * @see #ifMatch(String, LDValue...)
     * @see #ifNotMatch(ContextKind, String, LDValue...)
     * @since 6.0.0
     */
    public FlagRuleBuilder ifMatch(ContextKind contextKind, String attribute, LDValue... values) {
      return new FlagRuleBuilder().andMatch(contextKind, attribute, values);
    }
    
    /**
     * Starts defining a flag rule, using the "is one of" operator. This is a shortcut for calling
     * {@link #ifMatch(ContextKind, String, LDValue...)} with {@link ContextKind#DEFAULT} as the
     * context kind.
     * <p>
     * For example, this creates a rule that returns {@code true} if the name is "Patsy" or "Edina":
     * 
     * <pre><code>
     *     testData.flag("flag")
     *         .ifMatch("name", LDValue.of("Patsy"), LDValue.of("Edina"))
     *         .thenReturn(true));
     * </code></pre>
     * 
     * @param attribute the user attribute to match against
     * @param values values to compare to
     * @return a {@link FlagRuleBuilder}; call {@link FlagRuleBuilder#thenReturn(boolean)} or
     *   {@link FlagRuleBuilder#thenReturn(int)} to finish the rule, or add more tests with another
     *   method like {@link FlagRuleBuilder#andMatch(String, LDValue...)}
     * @see #ifMatch(ContextKind, String, LDValue...)
     * @see #ifNotMatch(String, LDValue...)
     */
    public FlagRuleBuilder ifMatch(String attribute, LDValue... values) {
      return ifMatch(ContextKind.DEFAULT, attribute, values);
    }

    /**
     * Starts defining a flag rule, using the "is not one of" operator. This matching expression only
     * applies to contexts of a specific kind.
     * <p>
     * For example, this creates a rule that returns {@code true} if the name attribute for the
     * "company" context is neither "Pendant" nor "Sterling Cooper":
     * 
     * <pre><code>
     *     testData.flag("flag")
     *         .ifNotMatch(ContextKind.of("company"), "name",
     *             LDValue.of("Pendant"), LDValue.of("Sterling Cooper"))
     *         .thenReturn(true));
     * </code></pre>
     *
     * @param contextKind the context kind to match
     * @param attribute the attribute to match against
     * @param values values to compare to
     * @return a {@link FlagRuleBuilder}; call {@link FlagRuleBuilder#thenReturn(boolean)} or
     *   {@link FlagRuleBuilder#thenReturn(int)} to finish the rule, or add more tests with another
     *   method like {@link FlagRuleBuilder#andMatch(ContextKind, String, LDValue...)}
     * @see #ifMatch(ContextKind, String, LDValue...)
     * @see #ifNotMatch(String, LDValue...)
     * @since 6.0.0
     */
    public FlagRuleBuilder ifNotMatch(ContextKind contextKind, String attribute, LDValue... values) {
      return new FlagRuleBuilder().andNotMatch(contextKind, attribute, values);
    }
    
    /**
     * Starts defining a flag rule, using the "is not one of" operator. This is a shortcut for calling
     * {@link #ifNotMatch(ContextKind, String, LDValue...)} with {@link ContextKind#DEFAULT} as the
     * context kind.
     * <p>
     * For example, this creates a rule that returns {@code true} if the name is neither "Saffron" nor "Bubble":
     * 
     * <pre><code>
     *     testData.flag("flag")
     *         .ifNotMatch("name", LDValue.of("Saffron"), LDValue.of("Bubble"))
     *         .thenReturn(true));
     * </code></pre>

     * @param attribute the user attribute to match against
     * @param values values to compare to
     * @return a {@link FlagRuleBuilder}; call {@link FlagRuleBuilder#thenReturn(boolean)} or
     *   {@link FlagRuleBuilder#thenReturn(int)} to finish the rule, or add more tests with another
     *   method like {@link FlagRuleBuilder#andMatch(String, LDValue...)}
     * @see #ifNotMatch(ContextKind, String, LDValue...)
     * @see #ifMatch(String, LDValue...)
     */
    public FlagRuleBuilder ifNotMatch(String attribute, LDValue... values) {
      return ifNotMatch(ContextKind.DEFAULT, attribute, values);
    }

    /**
     * Removes any existing rules from the flag. This undoes the effect of methods like
     * {@link #ifMatch(String, LDValue...)}.
     * 
     * @return the same builder
     */
    public FlagBuilder clearRules() {
      rules.clear();
      return this;
    }

    /**
     * Removes any existing user/context targets from the flag. This undoes the effect of methods like
     * {@link #variationForUser(String, boolean)}.
     * 
     * @return the same builder
     */
    public FlagBuilder clearTargets() {
      targets.clear();
      return this;
    }
    
    ItemDescriptor createFlag(int version) {
      ObjectBuilder builder = LDValue.buildObject()
          .put("key", key)
          .put("version", version)
          .put("on", on)
          .put("offVariation", offVariation)
          .put("fallthrough", LDValue.buildObject().put("variation", fallthroughVariation).build());

      if(samplingRatio != null) {
        builder.put("samplingRatio", samplingRatio);
      }

      if(migrationCheckRatio != null) {
        builder.put("migration", LDValue.buildObject()
            .put("checkRatio", migrationCheckRatio)
            .build());
      }
      
      // The following properties shouldn't actually be used in evaluations of this flag, but
      // adding them makes the JSON output more predictable for tests
      builder.put("prerequisites", LDValue.arrayOf())
        .put("salt", "");
      
      ArrayBuilder jsonVariations = LDValue.buildArray();
      for (LDValue v: variations) {
        jsonVariations.add(v);
      }
      builder.put("variations", jsonVariations.build());
      
      ArrayBuilder jsonTargets = LDValue.buildArray();
      ArrayBuilder jsonContextTargets = LDValue.buildArray();
      if (!targets.isEmpty()) {
        if (targets.get(ContextKind.DEFAULT) != null) {
          for (Map.Entry<Integer, ImmutableSet<String>> e: targets.get(ContextKind.DEFAULT).entrySet()) {
            if (!e.getValue().isEmpty()) {
              jsonTargets.add(LDValue.buildObject()
                  .put("variation", e.getKey().intValue())
                  .put("values", LDValue.Convert.String.arrayFrom(e.getValue()))
                  .build());
            }
          }
        }
        for (ContextKind contextKind: targets.keySet()) {
          for (Map.Entry<Integer, ImmutableSet<String>> e: targets.get(contextKind).entrySet()) {
            if (!e.getValue().isEmpty()) {
              jsonContextTargets.add(LDValue.buildObject()
                  .put("contextKind", contextKind.toString())
                  .put("variation", e.getKey().intValue())
                  .put("values", contextKind.isDefault() ? LDValue.arrayOf() :
                      LDValue.Convert.String.arrayFrom(e.getValue()))
                  .build());
            }
          }
        }
      }
      builder.put("targets", jsonTargets.build());
      builder.put("contextTargets", jsonContextTargets.build());
      
      ArrayBuilder jsonRules = LDValue.buildArray();
      if (!rules.isEmpty()) {
        int ri = 0;
        for (FlagRuleBuilder r: rules) {
          ArrayBuilder jsonClauses = LDValue.buildArray();
          for (Clause c: r.clauses) {
            ArrayBuilder jsonValues = LDValue.buildArray();
            for (LDValue v: c.values) {
              jsonValues.add(v);
            }
            jsonClauses.add(LDValue.buildObject()
                .put("contextKind", c.contextKind == null ? null : c.contextKind.toString())
                .put("attribute", c.attribute.toString())
                .put("op", c.operator)
                .put("values", jsonValues.build())
                .put("negate",  c.negate)
                .build());
          }
          jsonRules.add(LDValue.buildObject()
              .put("id", "rule" + ri)
              .put("variation", r.variation)
              .put("clauses", jsonClauses.build())
              .build());
          ri++;
        }
      }
      builder.put("rules", jsonRules.build());
      
      String json = builder.build().toJsonString();
      return DataModel.FEATURES.deserialize(json);
    }

    /**
     * Set the samplingRatio, used for event generation, for this flag.
     *
     * @param samplingRatio the event sampling ratio
     * @return a reference to this builder
     */
    public FlagBuilder samplingRatio(long samplingRatio) {
      this.samplingRatio = samplingRatio;
      return this;
    }

    /**
     * Turn this flag into a migration flag and set the check ratio.
     *
     * @param checkRatio the check ratio
     * @return a reference to this builder
     */
    public FlagBuilder migrationCheckRatio(long checkRatio) {
      migrationCheckRatio = checkRatio;
      return this;
    }
    
    private static int variationForBoolean(boolean value) {
      return value ? TRUE_VARIATION_FOR_BOOLEAN : FALSE_VARIATION_FOR_BOOLEAN;
    }
    
    /**
     * A builder for feature flag rules to be used with {@link FlagBuilder}.
     * <p>
     * In the LaunchDarkly model, a flag can have any number of rules, and a rule can have any number of
     * clauses. A clause is an individual test such as "name is 'X'". A rule matches a user if all of the
     * rule's clauses match the user.
     * <p>
     * To start defining a rule, use one of the flag builder's matching methods such as
     * {@link FlagBuilder#ifMatch(String, LDValue...)}. This defines the first clause for the rule.
     * Optionally, you may add more clauses with the rule builder's methods such as
     * {@link #andMatch(String, LDValue...)}. Finally, call {@link #thenReturn(boolean)} or
     * {@link #thenReturn(int)} to finish defining the rule.
     */
    public final class FlagRuleBuilder {
      final List<Clause> clauses = new ArrayList<>();
      int variation;

      /**
       * Adds another clause, using the "is one of" operator. This matching expression only
       * applies to contexts of a specific kind.
       * <p>
       * For example, this creates a rule that returns {@code true} if the name attribute for the
       * "company" context is "Ella" and the country is "gb":
       * 
       * <pre><code>
       *     testData.flag("flag")
       *         .ifMatch(ContextKind.of("company"), "name", LDValue.of("Ella"))
       *         .andMatch(ContextKind.of("company"), "country", LDValue.of("gb"))
       *         .thenReturn(true));
       * </code></pre>
       * 
       * @param contextKind the context kind to match
       * @param attribute the attribute to match against
       * @param values values to compare to
       * @return the rule builder
       * @see #andNotMatch(ContextKind, String, LDValue...)
       * @see #andMatch(String, LDValue...)
       * @since 6.0.0
       */
      public FlagRuleBuilder andMatch(ContextKind contextKind, String attribute, LDValue... values) {
        if (attribute != null) {
          clauses.add(new Clause(contextKind, AttributeRef.fromPath(attribute), "in", values, false));
        }
        return this;
      }

      /**
       * Adds another clause, using the "is one of" operator. This is a shortcut for calling
       * {@link #andMatch(ContextKind, String, LDValue...)} with {@link ContextKind#DEFAULT} as the context kind.
       * <p>
       * For example, this creates a rule that returns {@code true} if the name is "Patsy" and the
       * country is "gb":
       * 
       * <pre><code>
       *     testData.flag("flag")
       *         .ifMatch("name", LDValue.of("Patsy"))
       *         .andMatch("country", LDValue.of("gb"))
       *         .thenReturn(true));
       * </code></pre>
       * 
       * @param attribute the user attribute to match against
       * @param values values to compare to
       * @return the rule builder
       * @see #andNotMatch(String, LDValue...)
       * @see #andMatch(ContextKind, String, LDValue...)
       */
      public FlagRuleBuilder andMatch(String attribute, LDValue... values) {
        return andMatch(ContextKind.DEFAULT, attribute, values);
      }

      /**
       * Adds another clause, using the "is not one of" operator. This matching expression only
       * applies to contexts of a specific kind.
       * <p>
       * For example, this creates a rule that returns {@code true} if the name attribute for the
       * "company" context is "Ella" and the country is not "gb":
       * 
       * <pre><code>
       *     testData.flag("flag")
       *         .ifMatch(ContextKind.of("company"), "name", LDValue.of("Ella"))
       *         .andNotMatch(ContextKind.of("company"), "country", LDValue.of("gb"))
       *         .thenReturn(true));
       * </code></pre>
       * 
       * @param contextKind the context kind to match
       * @param attribute the user attribute to match against
       * @param values values to compare to
       * @return the rule builder
       * @see #andMatch(ContextKind, String, LDValue...)
       * @see #andNotMatch(String, LDValue...)
       * @since 6.0.0
       */
      public FlagRuleBuilder andNotMatch(ContextKind contextKind, String attribute, LDValue... values) {
        if (attribute != null) {
          clauses.add(new Clause(contextKind, AttributeRef.fromPath(attribute), "in", values, true));
        }
        return this;
      }
      
      /**
       * Adds another clause, using the "is not one of" operator.
       * <p>
       * For example, this creates a rule that returns {@code true} if the name is "Patsy" and the
       * country is not "gb":
       * 
       * <pre><code>
       *     testData.flag("flag")
       *         .ifMatch("name", LDValue.of("Patsy"))
       *         .andNotMatch("country", LDValue.of("gb"))
       *         .thenReturn(true));
       * </code></pre>
       * 
       * @param attribute the user attribute to match against
       * @param values values to compare to
       * @return the rule builder
       * @see #andMatch(String, LDValue...)
       * @see #andNotMatch(ContextKind, String, LDValue...)
       */
      public FlagRuleBuilder andNotMatch(String attribute, LDValue... values) {
        return andNotMatch(ContextKind.DEFAULT, attribute, values);
      }

      /**
       * Finishes defining the rule, specifying the result value as a boolean.
       * 
       * @param variation the value to return if the rule matches the user
       * @return the flag builder
       */
      public FlagBuilder thenReturn(boolean variation) {
        FlagBuilder.this.booleanFlag();
        return thenReturn(variationForBoolean(variation));
      }
      
      /**
       * Finishes defining the rule, specifying the result as a variation index.
       * 
       * @param variationIndex the variation to return if the rule matches the user: 0 for the first, 1
       *   for the second, etc.
       * @return the flag builder
       */
      public FlagBuilder thenReturn(int variationIndex) {
        this.variation = variationIndex;
        FlagBuilder.this.rules.add(this);
        return FlagBuilder.this;
      }
    }
    
    private static final class Clause {
      final ContextKind contextKind;
      final AttributeRef attribute;
      final String operator;
      final LDValue[] values;
      final boolean negate;
      
      Clause(ContextKind contextKind, AttributeRef attribute, String operator, LDValue[] values, boolean negate) {
        this.contextKind = contextKind;
        this.attribute = attribute;
        this.operator = operator;
        this.values = values;
        this.negate = negate;
      }
    }
  }
  
  private final class DataSourceImpl implements DataSource {
    final DataSourceUpdateSink updates;
    
    DataSourceImpl(DataSourceUpdateSink updates) {
      this.updates = updates;
    }
    
    @Override
    public Future<Void> start() {
      updates.init(makeInitData());
      updates.updateStatus(State.VALID, null);
      return completedFuture(null);
    }

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public void close() throws IOException {
      closedInstance(this);
    }
  }
}
