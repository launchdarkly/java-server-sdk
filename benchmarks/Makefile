.PHONY: benchmark clean sdk

BASE_DIR:=$(shell pwd)
PROJECT_DIR=$(shell cd .. && pwd)
SDK_VERSION=$(shell grep "version=" $(PROJECT_DIR)/gradle.properties | cut -d '=' -f 2)

BENCHMARK_SDK_JAR=lib/launchdarkly-java-server-sdk.jar
BENCHMARK_TEST_JAR=lib/launchdarkly-java-server-sdk-test.jar
SDK_JARS_DIR=$(PROJECT_DIR)/build/libs
SDK_JAR=$(SDK_JARS_DIR)/launchdarkly-java-server-sdk-$(SDK_VERSION).jar
SDK_TEST_JAR=$(SDK_JARS_DIR)/launchdarkly-java-server-sdk-$(SDK_VERSION)-test.jar

benchmark: $(BENCHMARK_SDK_JAR) $(BENCHMARK_TEST_JAR)
	rm -rf build/tmp
	../gradlew jmh
	cat build/reports/jmh/human.txt
	../gradlew jmhReport

clean:
	rm -rf build lib

sdk: $(BENCHMARK_ALL_JAR) $(BENCHMARK_TEST_JAR)

$(BENCHMARK_SDK_JAR): $(SDK_JAR)
	mkdir -p lib
	cp $< $@

$(BENCHMARK_TEST_JAR): $(SDK_TEST_JAR)
	mkdir -p lib
	cp $< $@

$(SDK_JAR):
	cd .. && ./gradlew shadowJar

$(SDK_TEST_JAR):
	cd .. && ./gradlew testJar
