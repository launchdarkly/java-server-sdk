.PHONY: all test-all-jar test-default-jar test-thin-jar

BASE_DIR:=$(shell pwd)
PROJECT_DIR=$(shell cd .. && pwd)
SDK_VERSION=$(shell grep "version=" $(PROJECT_DIR)/gradle.properties | cut -d '=' -f 2)

TEMP_DIR=$(BASE_DIR)/temp
SDK_JARS_DIR=$(PROJECT_DIR)/build/libs
SDK_DEFAULT_JAR=$(SDK_JARS_DIR)/launchdarkly-client-$(SDK_VERSION).jar
SDK_ALL_JAR=$(SDK_JARS_DIR)/launchdarkly-client-$(SDK_VERSION)-all.jar
SDK_THIN_JAR=$(SDK_JARS_DIR)/launchdarkly-client-$(SDK_VERSION)-thin.jar

TEMP_CLASSES=$(TEMP_DIR)/temp-classes
classes_prepare=echo "checking $(1)..." && jar tf $(1) | grep '\.class$$' >$(TEMP_CLASSES)
classes_should_contain=echo "  should contain $(2)" && grep $(1) $(TEMP_CLASSES) >/dev/null
classes_should_not_contain=echo "  should not contain $(2)" && ! grep $(1) $(TEMP_CLASSES) >/dev/null

verify_sdk_classes= \
	$(call classes_should_contain,'^com/launchdarkly/client/[^/]*$$',com.launchdarkly.client) && \
	$(foreach subpkg,$(sdk_subpackage_names), \
		$(call classes_should_contain,'^com/launchdarkly/client/$(subpkg)/',com.launchdarkly.client.$(subpkg)) && ) true
sdk_subpackage_names= \
	$(shell ls -d $(PROJECT_DIR)/src/main/java/com/launchdarkly/client/*/ | sed -e 's@^.*/\([^/]*\)/@\1@')

all: test-all-jar-classes test-default-jar-classes test-thin-jar-classes

test-all-jar-classes: $(SDK_ALL_JAR) $(TEMP_DIR)
	@$(call classes_prepare,$<)
	@$(call verify_sdk_classes)
	@$(call classes_should_contain,'^com/google/gson/',Gson (unshaded))
	@$(call classes_should_contain,'^org/slf4j/',SLF4j (unshaded))
	@$(call classes_should_contain,'^com/launchdarkly/shaded/',shaded dependency jars)
	@$(call classes_should_not_contain,'^com/launchdarkly/shaded/com/launchdarkly/client',shaded SDK classes)
	@$(call classes_should_not_contain,'^com/launchdarkly/shaded/com/google/gson',shaded Gson)
	@$(call classes_should_not_contain,'^com/launchdarkly/shaded/org/slf4j',shaded SLF4j)

test-default-jar-classes: $(SDK_DEFAULT_JAR) $(TEMP_DIR)
	@$(call classes_prepare,$<)
	@$(call verify_sdk_classes)
	@$(call classes_should_contain,'^com/launchdarkly/shaded/',shaded dependency jars)
	@$(call classes_should_not_contain,'^com/launchdarkly/shaded/com/launchdarkly/client',shaded SDK classes)
	@$(call classes_should_not_contain,'com/google/gson/',Gson (shaded or unshaded))
	@$(call classes_should_not_contain,'org/slf4j/',SLF4j (shaded or unshaded))

test-thin-jar-classes: $(SDK_THIN_JAR) $(TEMP_DIR)
	@$(call classes_prepare,$<)
	@$(call verify_sdk_classes)
	@$(call classes_should_not_contain,-v '^com/launchdarkly/client/',anything other than SDK classes)

$(SDK_DEFAULT_JAR):
	cd .. && ./gradlew shadowJar

$(SDK_ALL_JAR):
	cd .. && ./gradlew shadowJarAll

$(SDK_THIN_JAR):
	cd .. && ./gradlew jar

$(TEMP_DIR):
	[ -d $@ ] || mkdir -p $@
