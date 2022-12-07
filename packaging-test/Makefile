.PHONY: all clean test-default-jar test-thin-jar test-default-jar-classes test-thin-jar-classes

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

export TEMP_DIR=$(BASE_DIR)/temp

JAR=$(if $(shell which jar),jar,$(JAVA_HOME)/bin/jar)

LOCAL_VERSION=99.99.99-SNAPSHOT
MAVEN_LOCAL_REPO=$(HOME)/.m2/repository
TEMP_MAVEN_OUTPUT_DIR=$(MAVEN_LOCAL_REPO)/com/launchdarkly/launchdarkly-java-server-sdk/$(LOCAL_VERSION)
SDK_DEFAULT_JAR=$(TEMP_MAVEN_OUTPUT_DIR)/launchdarkly-java-server-sdk-$(LOCAL_VERSION).jar
SDK_ALL_JAR=$(TEMP_MAVEN_OUTPUT_DIR)/launchdarkly-java-server-sdk-$(LOCAL_VERSION)-all.jar
SDK_THIN_JAR=$(TEMP_MAVEN_OUTPUT_DIR)/launchdarkly-java-server-sdk-$(LOCAL_VERSION)-thin.jar
POM_XML=$(TEMP_MAVEN_OUTPUT_DIR)/launchdarkly-java-server-sdk-$(LOCAL_VERSION).pom

export TEMP_OUTPUT=$(TEMP_DIR)/test.out

# Build product of the project in ./test-app; can be run as either a regular app or an OSGi bundle
TEST_APP_JAR=$(TEMP_DIR)/test-app.jar
TEST_APP_SOURCES=$(shell find $(BASE_DIR)/test-app -name *.java) $(BASE_DIR)/test-app/build.gradle

# Felix OSGi container
export FELIX_DIR=$(TEMP_DIR)/felix
export FELIX_JAR=$(FELIX_DIR)/lib/felix.jar
export FELIX_BASE_BUNDLE_DIR=$(FELIX_DIR)/base-bundles
export TEMP_BUNDLE_DIR=$(FELIX_DIR)/app-bundles

# Lists of jars to use as a classpath (for the non-OSGi runtime test) or to install as bundles (for
# the OSGi test). Note that we're assuming that all of the SDK's dependencies have built-in support
# for OSGi, which is currently true; if that weren't true, we would have to do something different
# to put them on the system classpath in the OSGi test.
RUN_JARS_test-default-jar=$(TEST_APP_JAR) $(SDK_DEFAULT_JAR) \
	$(shell ls $(TEMP_DIR)/dependencies-external/*.jar 2>/dev/null)
RUN_JARS_test-thin-jar=$(TEST_APP_JAR) $(SDK_THIN_JAR) \
	$(shell ls $(TEMP_DIR)/dependencies-internal/*.jar 2>/dev/null) \
	$(shell ls $(TEMP_DIR)/dependencies-external/*.jar 2>/dev/null)

# grep variants with lookahead aren't universally available
PGREP=$(if $(shell echo a | grep -P . 2>/dev/null),grep -P,$(if $(shell which ggrep),ggrep -P,echo This makefile requires grep -P or ggrep -P && exit 1;))
classes_prepare=echo "  checking $(1)..." && $(JAR) tf $(1) | grep '\.class$$' >$(TEMP_OUTPUT)
classes_should_contain=echo "    should contain $(2)" && $(PGREP) "^$(1)/.*\.class$$" $(TEMP_OUTPUT) >/dev/null
classes_should_not_contain=echo "    should not contain $(2)" && ! $(PGREP) "^$(1)/.*\.class$$" $(TEMP_OUTPUT)
should_not_have_module_info=echo "    should not have module-info.class" && ! grep "module-info\.class$$" $(TEMP_OUTPUT)

verify_sdk_classes= \
	$(call classes_should_contain,com/launchdarkly/sdk,com.launchdarkly.sdk) && \
	$(call classes_should_contain,com/launchdarkly/sdk/json,com.launchdarkly.sdk.json) && \
	$(foreach subpkg,$(sdk_subpackage_names), \
		$(call classes_should_contain,com/launchdarkly/sdk/$(subpkg),com.launchdarkly.sdk.$(subst /,.,$(subpkg))) && ) true
sdk_subpackage_names= \
	$(shell cd $(PROJECT_DIR)/src/main/java/com/launchdarkly/sdk && find . ! -path . -type d | sed -e 's@^\./@@')

manifest_should_not_have_classpath= \
	echo "    should not have Class-Path in manifest" && \
	! (unzip -q -c $(1) META-INF/MANIFEST.MF | grep 'Class-Path')

caption=echo "" && echo "$(1)"

all: test-default-jar test-thin-jar test-pom

clean:
	rm -rf $(TEMP_DIR)/*
	rm -rf test-app/build

# SECONDEXPANSION is needed so we can use "$@" inside a variable in the prerequisite list of the test targets
.SECONDEXPANSION:

test-default-jar test-thin-jar: $$@-classes $(TEST_APP_JAR) get-sdk-dependencies $$(RUN_JARS_$$@) $(FELIX_DIR)
	@$(call caption,$@)
	@./run-non-osgi-test.sh $(RUN_JARS_$@)
	@./run-osgi-test.sh $(RUN_JARS_$@)

test-default-jar-classes: $(SDK_DEFAULT_JAR) $(TEMP_DIR)
	@$(call caption,$@)
	@$(call classes_prepare,$<)
	@$(call verify_sdk_classes)
	@$(call classes_should_contain,com/launchdarkly/logging,unshaded com.launchdarkly.logging classes)
	@$(call classes_should_not_contain,com/launchdarkly/shaded/com/launchdarkly/sdk(?!/internal),shaded non-internal SDK classes)
	@$(call classes_should_contain,com/launchdarkly/shaded/com/google/gson,shaded Gson)
	@$(call classes_should_not_contain,com/google/gson,unshaded Gson)
	@$(call classes_should_not_contain,org/slf4j,unshaded SLF4j)
	@$(call classes_should_not_contain,com/launchdarkly/shaded/org/slf4j,shaded SLF4j)
	@$(call classes_should_not_contain,com/fasterxml/jackson,unshaded Jackson)
	@$(call classes_should_not_contain,com/launchdarkly/shaded/com/fasterxml/jackson,shaded Jackson)
	@$(call should_not_have_module_info)
	@$(call manifest_should_not_have_classpath,$<)

test-thin-jar-classes: $(SDK_THIN_JAR) $(TEMP_DIR)
	@$(call caption,$@)
	@$(call classes_prepare,$<)
	@$(call verify_sdk_classes)
	@echo "    should not contain anything other than SDK classes"
	@! grep -v "^com/launchdarkly/sdk" $(TEMP_OUTPUT)
	@$(call should_not_have_module_info)
	@$(call manifest_should_not_have_classpath,$<)

test-pom: $(POM_XML)
	@$(call caption,$@)
	@echo "=== contents of $<"
	@cat $<
	@echo "==="
	@echo "  should not have any dependencies"
	@! grep '<dependency>' $< || (echo "    FAILED" && exit 1)

$(SDK_DEFAULT_JAR) $(SDK_ALL_JAR) $(SDK_THIN_JAR) $(POM_XML):
	cd .. && ./gradlew publishToMavenLocal -P version=$(LOCAL_VERSION) -P LD_SKIP_SIGNING=1
	@# publishToMavenLocal creates not only the jars but also the pom

$(TEST_APP_JAR): $(SDK_THIN_JAR) $(TEST_APP_SOURCES) | $(TEMP_DIR)
	@mkdir -p $(TEMP_DIR)/dependencies-app
	@cd test-app && ../../gradlew jar
	@cp $(BASE_DIR)/test-app/build/libs/test-app-*.jar $@

get-sdk-dependencies: $(TEMP_DIR)/dependencies-all $(TEMP_DIR)/dependencies-external $(TEMP_DIR)/dependencies-internal

$(TEMP_DIR)/dependencies-all: | $(TEMP_DIR)
	@[ -d $@ ] || mkdir -p $@
	@cd .. && ./gradlew exportDependencies
	@cp $(TEMP_DIR)/dependencies-app/*.jar $@

$(TEMP_DIR)/dependencies-external: $(TEMP_DIR)/dependencies-all
	@[ -d $@ ] || mkdir -p $@
	@cp $(TEMP_DIR)/dependencies-all/slf4j*.jar $@
	@cp $(TEMP_DIR)/dependencies-all/gson*.jar $@
	@cp $(TEMP_DIR)/dependencies-all/jackson*.jar $@

$(TEMP_DIR)/dependencies-internal: $(TEMP_DIR)/dependencies-all
	[ -d $@ ] || mkdir -p $@
	cp $(TEMP_DIR)/dependencies-all/*.jar $@
	rm $@/slf4j*.jar
	rm $@/jackson*.jar

$(FELIX_JAR): $(FELIX_DIR)

$(FELIX_DIR):
	mkdir -p $(FELIX_DIR)
	mkdir -p $(FELIX_DIR)/lib
	mkdir -p $(FELIX_BASE_BUNDLE_DIR)
	cd test-app && ../../gradlew createOsgi
	@# createOsgi is a target provided by the osgi-run Gradle plugin; it downloads the Felix container and
	@# puts it in build/osgi along with related bundles and a config file.
	cp -r test-app/build/osgi/conf $(FELIX_DIR)
	echo "felix.shutdown.hook=false" >>$(FELIX_DIR)/conf/config.properties
	@# setting felix.shutdown.hook to false allows our test app to use System.exit()
	cp test-app/build/osgi/system-libs/org.apache.felix.main-*.jar $(FELIX_JAR)
	cp test-app/build/osgi/bundle/* $(FELIX_BASE_BUNDLE_DIR)
	cd $(FELIX_BASE_BUNDLE_DIR) && rm -f launchdarkly-*.jar gson-*.jar

$(TEMP_DIR):
	[ -d $@ ] || mkdir -p $@
