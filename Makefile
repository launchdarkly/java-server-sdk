
build:
	./gradlew jar

clean:
	./gradlew clean

test:
	./gradlew test

TEMP_TEST_OUTPUT=/tmp/sdk-test-service.log

# Temporary contract test skips - these non-U2C tests won't work with the v1 test harness.
# All of the event tests currently fail because we have already migrated the event code to
# use the U2C schema, and some of the evaluation tests describe evaluation conditions that
# aren't applicable to contexts. When we migrate to using the v2 U2C-aware contract tests,
# we'll remove these skips.
TEST_HARNESS_PARAMS= \
    -skip evaluation/parameterized/secondary \
    -skip evaluation/parameterized/key/empty \
    -skip evaluation/bucketing/secondary/secondary/experiment \
    -skip evaluation/bucketing/non-key/experiments \
    -skip events

build-contract-tests:
	@cd contract-tests && ../gradlew installDist

start-contract-test-service:
	@contract-tests/service/build/install/service/bin/service

start-contract-test-service-bg:
	@echo "Test service output will be captured in $(TEMP_TEST_OUTPUT)"
	@make start-contract-test-service >$(TEMP_TEST_OUTPUT) 2>&1 &

run-contract-tests:
	@curl -s https://raw.githubusercontent.com/launchdarkly/sdk-test-harness/v1.0.0/downloader/run.sh \
      | VERSION=v1 PARAMS="-url http://localhost:8000 -debug -stop-service-at-end $(TEST_HARNESS_PARAMS)" sh

contract-tests: build-contract-tests start-contract-test-service-bg run-contract-tests

.PHONY: build-contract-tests start-contract-test-service start-contract-test-service-bg run-contract-tests contract-tests
