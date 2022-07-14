package com.launchdarkly.sdk.server.integrations;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.launchdarkly.sdk.ArrayBuilder;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceFactory;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdates;
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
public final class TestData implements DataSourceFactory {
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
  
  /**
   * Called internally by the SDK to associate this test data source with an {@code LDClient} instance.
   * You do not need to call this method.
   */
  @Override
  public DataSource createDataSource(ClientContext context, DataSourceUpdates dataSourceUpdates) {
    DataSourceImpl instance = new DataSourceImpl(dataSourceUpdates);
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
    Map<Integer, ImmutableSet<String>> targets;
    List<FlagRuleBuilder> rules;
    
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
      this.targets = from.targets == null ? null : new HashMap<>(from.targets); 
      this.rules = from.rules == null ? null : new ArrayList<>(from.rules);
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
     * that is returned if targeting is on and the user was not matched by a more specific
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
     * that is returned if targeting is on and the user was not matched by a more specific
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
     * Sets the flag to always return the specified boolean variation for all users.
     * <p>
     * VariationForAllUsers sets the flag to return the specified boolean variation by default for all users.
     * <p>
     * Targeting is switched on, any existing targets or rules are removed, and the flag's variations are
     * set to true and false. The fallthrough variation is set to the specified value. The off variation is
     * left unchanged.
     *
     * @param variation the desired true/false variation to be returned for all users
     * @return the builder
     */
    public FlagBuilder variationForAllUsers(boolean variation) {
      return booleanFlag().variationForAllUsers(variationForBoolean(variation));
    }

    /**
     * Sets the flag to always return the specified variation for all users.
     * <p>
     * The variation is specified by number, out of whatever variation values have already been
     * defined. Targeting is switched on, and any existing targets or rules are removed. The fallthrough
     * variation is set to the specified value. The off variation is left unchanged.
     * 
     * @param variationIndex the desired variation: 0 for the first, 1 for the second, etc.
     * @return the builder
     */
    public FlagBuilder variationForAllUsers(int variationIndex) {
      return on(true).clearRules().clearUserTargets().fallthroughVariation(variationIndex);
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
     */
    public FlagBuilder valueForAllUsers(LDValue value) {
      variations.clear();
      variations.add(value);
      return variationForAllUsers(0);
    }
    
    /**
     * Sets the flag to return the specified boolean variation for a specific user key when
     * targeting is on.
     * <p>
     * This has no effect when targeting is turned off for the flag.
     * <p>
     * If the flag was not already a boolean flag, this also changes it to a boolean flag.
     * 
     * @param userKey a user key
     * @param variation the desired true/false variation to be returned for this user when
     *   targeting is on
     * @return the builder
     */
    public FlagBuilder variationForUser(String userKey, boolean variation) {
      return booleanFlag().variationForUser(userKey, variationForBoolean(variation));
    }
    
    /**
     * Sets the flag to return the specified variation for a specific user key when targeting
     * is on.
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
     */
    public FlagBuilder variationForUser(String userKey, int variationIndex) {
      if (targets == null) {
        targets = new TreeMap<>(); // TreeMap keeps variations in order for test determinacy
      }
      for (int i = 0; i < variations.size(); i++) {
        ImmutableSet<String> keys = targets.get(i);
        if (i == variationIndex) {
          if (keys == null) {
            targets.put(i, ImmutableSortedSet.of(userKey));
          } else if (!keys.contains(userKey)) {
            targets.put(i, ImmutableSortedSet.<String>naturalOrder().addAll(keys).add(userKey).build());
          }
        } else {
          if (keys != null && keys.contains(userKey)) {
            targets.put(i, ImmutableSortedSet.copyOf(Iterables.filter(keys, k -> !k.equals(userKey))));
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
     * Starts defining a flag rule, using the "is one of" operator.
     * <p>
     * For example, this creates a rule that returns {@code true} if the name is "Patsy" or "Edina":
     * 
     * <pre><code>
     *     testData.flag("flag")
     *         .ifMatch(UserAttribute.NAME, LDValue.of("Patsy"), LDValue.of("Edina"))
     *         .thenReturn(true));
     * </code></pre>
     * 
     * @param attribute the user attribute to match against
     * @param values values to compare to
     * @return a {@link FlagRuleBuilder}; call {@link FlagRuleBuilder#thenReturn(boolean)} or
     *   {@link FlagRuleBuilder#thenReturn(int)} to finish the rule, or add more tests with another
     *   method like {@link FlagRuleBuilder#andMatch(UserAttribute, LDValue...)}
     */
    public FlagRuleBuilder ifMatch(UserAttribute attribute, LDValue... values) {
      return new FlagRuleBuilder().andMatch(attribute, values);
    }
    
    /**
     * Starts defining a flag rule, using the "is not one of" operator.
     * <p>
     * For example, this creates a rule that returns {@code true} if the name is neither "Saffron" nor "Bubble":
     * 
     * <pre><code>
     *     testData.flag("flag")
     *         .ifNotMatch(UserAttribute.NAME, LDValue.of("Saffron"), LDValue.of("Bubble"))
     *         .thenReturn(true));
     * </code></pre>

     * @param attribute the user attribute to match against
     * @param values values to compare to
     * @return a {@link FlagRuleBuilder}; call {@link FlagRuleBuilder#thenReturn(boolean)} or
     *   {@link FlagRuleBuilder#thenReturn(int)} to finish the rule, or add more tests with another
     *   method like {@link FlagRuleBuilder#andMatch(UserAttribute, LDValue...)}
     */
    public FlagRuleBuilder ifNotMatch(UserAttribute attribute, LDValue... values) {
      return new FlagRuleBuilder().andNotMatch(attribute, values);
    }
    
    /**
     * Removes any existing rules from the flag. This undoes the effect of methods like
     * {@link #ifMatch(UserAttribute, LDValue...)}.
     * 
     * @return the same builder
     */
    public FlagBuilder clearRules() {
      rules = null;
      return this;
    }

    /**
     * Removes any existing user targets from the flag. This undoes the effect of methods like
     * {@link #variationForUser(String, boolean)}.
     * 
     * @return the same builder
     */
    public FlagBuilder clearUserTargets() {
      targets = null;
      return this;
    }
    
    ItemDescriptor createFlag(int version) {
      ObjectBuilder builder = LDValue.buildObject()
          .put("key", key)
          .put("version", version)
          .put("on", on)
          .put("offVariation", offVariation)
          .put("fallthrough", LDValue.buildObject().put("variation", fallthroughVariation).build());
      ArrayBuilder jsonVariations = LDValue.buildArray();
      for (LDValue v: variations) {
        jsonVariations.add(v);
      }
      builder.put("variations", jsonVariations.build());
      
      if (targets != null) {
        ArrayBuilder jsonTargets = LDValue.buildArray();
        for (Map.Entry<Integer, ImmutableSet<String>> e: targets.entrySet()) {
          jsonTargets.add(LDValue.buildObject()
              .put("variation", e.getKey().intValue())
              .put("values", LDValue.Convert.String.arrayFrom(e.getValue()))
              .build());
        }
        builder.put("targets", jsonTargets.build());
      }
      
      if (rules != null) {
        ArrayBuilder jsonRules = LDValue.buildArray();
        int ri = 0;
        for (FlagRuleBuilder r: rules) {
          ArrayBuilder jsonClauses = LDValue.buildArray();
          for (Clause c: r.clauses) {
            ArrayBuilder jsonValues = LDValue.buildArray();
            for (LDValue v: c.values) {
              jsonValues.add(v);
            }
            jsonClauses.add(LDValue.buildObject()
                .put("attribute", c.attribute.getName())
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
        builder.put("rules", jsonRules.build());
      }
      
      String json = builder.build().toJsonString();
      return DataModel.FEATURES.deserialize(json);
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
     * {@link FlagBuilder#ifMatch(UserAttribute, LDValue...)}. This defines the first clause for the rule.
     * Optionally, you may add more clauses with the rule builder's methods such as
     * {@link #andMatch(UserAttribute, LDValue...)}. Finally, call {@link #thenReturn(boolean)} or
     * {@link #thenReturn(int)} to finish defining the rule.
     */
    public final class FlagRuleBuilder {
      final List<Clause> clauses = new ArrayList<>();
      int variation;
      
      /**
       * Adds another clause, using the "is one of" operator.
       * <p>
       * For example, this creates a rule that returns {@code true} if the name is "Patsy" and the
       * country is "gb":
       * 
       * <pre><code>
       *     testData.flag("flag")
       *         .ifMatch(UserAttribute.NAME, LDValue.of("Patsy"))
       *         .andMatch(UserAttribute.COUNTRY, LDValue.of("gb"))
       *         .thenReturn(true));
       * </code></pre>
       * 
       * @param attribute the user attribute to match against
       * @param values values to compare to
       * @return the rule builder
       */
      public FlagRuleBuilder andMatch(UserAttribute attribute, LDValue... values) {
        clauses.add(new Clause(attribute, "in", values, false));
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
       *         .ifMatch(UserAttribute.NAME, LDValue.of("Patsy"))
       *         .andNotMatch(UserAttribute.COUNTRY, LDValue.of("gb"))
       *         .thenReturn(true));
       * </code></pre>
       * 
       * @param attribute the user attribute to match against
       * @param values values to compare to
       * @return the rule builder
       */
      public FlagRuleBuilder andNotMatch(UserAttribute attribute, LDValue... values) {
        clauses.add(new Clause(attribute, "in", values, true));
        return this;
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
        if (FlagBuilder.this.rules == null) {
          FlagBuilder.this.rules = new ArrayList<>();
        }
        FlagBuilder.this.rules.add(this);
        return FlagBuilder.this;
      }
    }
    
    private static final class Clause {
      final UserAttribute attribute;
      final String operator;
      final LDValue[] values;
      final boolean negate;
      
      Clause(UserAttribute attribute, String operator, LDValue[] values, boolean negate) {
        this.attribute = attribute;
        this.operator = operator;
        this.values = values;
        this.negate = negate;
      }
    }
  }
  
  private final class DataSourceImpl implements DataSource {
    final DataSourceUpdates updates;
    
    DataSourceImpl(DataSourceUpdates updates) {
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
