.PHONY: all clean test-all-jar test-default-jar test-thin-jar test-all-jar-classes test-default-jar-classes test-thin-jar-classes

# Running "make all" will verify the following things about the SDK jar distributions:
# - Each jar contains the expected set of classes, broadly speaking.
# - A simple Java application is able to instantiate an SDK client, when the SDK jar and any
#   non-bundled dependencies are on the classpath.
# - An OSGi bundle is able to instantiate an SDK client, when the SDK and any non-bundled
#   dependencies are also installed as OSGi bundles; i.e., the correct packages are being
#   exported and imported. This is our only test of this configuration, since our integration
#   testing for Java does not use OSGi.

BASE_DIR:=$(shell pwd)
PROJECT_DIR=$(shell cd .. && pwd)
SDK_VERSION=$(shell grep "version=" $(PROJECT_DIR)/gradle.properties | cut -d '=' -f 2)

SDK_JARS_DIR=$(PROJECT_DIR)/build/libs
SDK_DEFAULT_JAR=$(SDK_JARS_DIR)/launchdarkly-java-server-sdk-$(SDK_VERSION).jar
SDK_ALL_JAR=$(SDK_JARS_DIR)/launchdarkly-java-server-sdk-$(SDK_VERSION)-all.jar
SDK_THIN_JAR=$(SDK_JARS_DIR)/launchdarkly-java-server-sdk-$(SDK_VERSION)-thin.jar

TEMP_DIR=$(BASE_DIR)/temp
TEMP_OUTPUT=$(TEMP_DIR)/test.out

# Build product of the project in ./test-app; can be run as either a regular app or an OSGi bundle
TEST_APP_JAR=$(TEMP_DIR)/test-app.jar

# SLF4j implementation - we need to download this separately because it's not in the SDK dependencies
SLF4J_SIMPLE_JAR=$(TEMP_DIR)/test-slf4j-simple.jar
SLF4J_SIMPLE_JAR_URL=https://oss.sonatype.org/content/groups/public/org/slf4j/slf4j-simple/1.7.21/slf4j-simple-1.7.21.jar

# Felix OSGi container
FELIX_ARCHIVE=org.apache.felix.main.distribution-6.0.3.tar.gz
FELIX_ARCHIVE_URL=http://mirrors.ibiblio.org/apache//felix/$(FELIX_ARCHIVE)
FELIX_DIR=$(TEMP_DIR)/felix
FELIX_JAR=$(FELIX_DIR)/bin/felix.jar
TEMP_BUNDLE_DIR=$(TEMP_DIR)/bundles

# Lists of jars to use as a classpath (for the non-OSGi runtime test) or to install as bundles (for
# the OSGi test). Note that we're assuming that all of the SDK's dependencies have built-in support
# for OSGi, which is currently true; if that weren't true, we would have to do something different
# to put them on the system classpath in the OSGi test.
RUN_JARS_test-all-jar=$(TEST_APP_JAR) $(SDK_ALL_JAR) \
	$(SLF4J_SIMPLE_JAR)
RUN_JARS_test-default-jar=$(TEST_APP_JAR) $(SDK_DEFAULT_JAR) \
	$(shell ls $(TEMP_DIR)/dependencies-external/*.jar) \
	$(SLF4J_SIMPLE_JAR)
RUN_JARS_test-thin-jar=$(TEST_APP_JAR) $(SDK_THIN_JAR) \
	$(shell ls $(TEMP_DIR)/dependencies-internal/*.jar) \
	$(shell ls $(TEMP_DIR)/dependencies-external/*.jar) \
	$(SLF4J_SIMPLE_JAR)

# The test-app displays this message on success
SUCCESS_MESSAGE="@@@ successfully created LD client @@@"

classes_prepare=echo "  checking $(1)..." && jar tf $(1) | grep '\.class$$' >$(TEMP_OUTPUT)
classes_should_contain=echo "    should contain $(2)" && grep $(1) $(TEMP_OUTPUT) >/dev/null
classes_should_not_contain=echo "    should not contain $(2)" && ! grep $(1) $(TEMP_OUTPUT) >/dev/null

verify_sdk_classes= \
	$(call classes_should_contain,'^com/launchdarkly/client/[^/]*$$',com.launchdarkly.client) && \
	$(foreach subpkg,$(sdk_subpackage_names), \
		$(call classes_should_contain,'^com/launchdarkly/client/$(subpkg)/',com.launchdarkly.client.$(subpkg)) && ) true
sdk_subpackage_names= \
	$(shell ls -d $(PROJECT_DIR)/src/main/java/com/launchdarkly/client/*/ | sed -e 's@^.*/\([^/]*\)/@\1@')

caption=echo "" && echo "$(1)"

all: test-all-jar test-default-jar test-thin-jar

clean:
	rm -rf $(TEMP_DIR)/*

# SECONDEXPANSION is needed so we can use "$@" inside a variable in the prerequisite list of the test targets
.SECONDEXPANSION:

test-all-jar test-default-jar test-thin-jar: $$(RUN_JARS_$$@) $(TEST_APP_JAR) $(FELIX_JAR) get-sdk-dependencies $$@-classes
	@$(call caption,$@)
	@echo "  non-OSGi runtime test"
	@java -classpath $(shell echo "$(RUN_JARS_$@)" | sed -e 's/ /:/g') testapp.TestApp | tee $(TEMP_OUTPUT)
	@grep $(SUCCESS_MESSAGE) $(TEMP_OUTPUT) >/dev/null
# Can't currently run the OSGi test for the thin jar, because some of our dependencies aren't available as OSGi bundles.
	@if [ "$@" != "test-thin-jar" ]; then \
	  echo ""; \
	  echo "  OSGi runtime test"; \
	  rm -rf $(TEMP_BUNDLE_DIR); \
	  mkdir -p $(TEMP_BUNDLE_DIR); \
	  cp $(RUN_JARS_$@) $(FELIX_DIR)/bundle/*.jar $(TEMP_BUNDLE_DIR); \
	  rm -rf $(FELIX_DIR)/felix-cache; \
	  cd $(FELIX_DIR) && echo "sleep 3;exit 0" | java -jar $(FELIX_JAR) -b $(TEMP_BUNDLE_DIR) | tee $(TEMP_OUTPUT); \
	  grep $(SUCCESS_MESSAGE) $(TEMP_OUTPUT) >/dev/null; \
	fi

test-all-jar-classes: $(SDK_ALL_JAR) $(TEMP_DIR)
	@$(call caption,$@)
	@$(call classes_prepare,$<)
	@$(call verify_sdk_classes)
	@$(call classes_should_contain,'^com/google/gson/',Gson (unshaded))
	@$(call classes_should_contain,'^org/slf4j/',SLF4j (unshaded))
	@$(call classes_should_contain,'^com/launchdarkly/shaded/',shaded dependency jars)
	@$(call classes_should_not_contain,'^com/launchdarkly/shaded/com/launchdarkly/client',shaded SDK classes)
	@$(call classes_should_not_contain,'^com/launchdarkly/shaded/com/google/gson',shaded Gson)
	@$(call classes_should_not_contain,'^com/launchdarkly/shaded/org/slf4j',shaded SLF4j)

test-default-jar-classes: $(SDK_DEFAULT_JAR) $(TEMP_DIR)
	@$(call caption,$@)
	@$(call classes_prepare,$<)
	@$(call verify_sdk_classes)
	@$(call classes_should_contain,'^com/launchdarkly/shaded/',shaded dependency jars)
	@$(call classes_should_not_contain,'^com/launchdarkly/shaded/com/launchdarkly/client',shaded SDK classes)
	@$(call classes_should_not_contain,'com/google/gson/',Gson (shaded or unshaded))
	@$(call classes_should_not_contain,'org/slf4j/',SLF4j (shaded or unshaded))

test-thin-jar-classes: $(SDK_THIN_JAR) $(TEMP_DIR)
	@$(call caption,$@)
	@$(call classes_prepare,$<)
	@$(call verify_sdk_classes)
	@$(call classes_should_not_contain,-v '^com/launchdarkly/client/',anything other than SDK classes)

$(SDK_DEFAULT_JAR):
	cd .. && ./gradlew shadowJar

$(SDK_ALL_JAR):
	cd .. && ./gradlew shadowJarAll

$(SDK_THIN_JAR):
	cd .. && ./gradlew jar

$(TEST_APP_JAR): $(SDK_THIN_JAR) | $(TEMP_DIR)
	cd test-app && ../../gradlew jar
	cp $(BASE_DIR)/test-app/build/libs/test-app-*.jar $@

get-sdk-dependencies: $(TEMP_DIR)/dependencies-all $(TEMP_DIR)/dependencies-external $(TEMP_DIR)/dependencies-internal

$(TEMP_DIR)/dependencies-all: | $(TEMP_DIR)
	[ -d $@ ] || mkdir -p $@
	cd .. && ./gradlew exportDependencies

$(TEMP_DIR)/dependencies-external: $(TEMP_DIR)/dependencies-all
	[ -d $@ ] || mkdir -p $@
	cp $(TEMP_DIR)/dependencies-all/gson*.jar $(TEMP_DIR)/dependencies-all/slf4j*.jar $@

$(TEMP_DIR)/dependencies-internal: $(TEMP_DIR)/dependencies-all
	[ -d $@ ] || mkdir -p $@
	cp $(TEMP_DIR)/dependencies-all/*.jar $@
	rm $@/gson*.jar $@/slf4j*.jar

$(SLF4J_SIMPLE_JAR): | $(TEMP_DIR)
	curl -f -L $(SLF4J_SIMPLE_JAR_URL) >$@

$(FELIX_JAR): | $(TEMP_DIR)
	curl -f -L $(FELIX_ARCHIVE_URL) >$(TEMP_DIR)/$(FELIX_ARCHIVE)
	cd $(TEMP_DIR) && tar xfz $(FELIX_ARCHIVE) && rm $(FELIX_ARCHIVE)
	cd $(TEMP_DIR) && mv `ls -d felix*` felix

$(TEMP_DIR):
	[ -d $@ ] || mkdir -p $@
