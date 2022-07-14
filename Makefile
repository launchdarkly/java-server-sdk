
build:
	./gradlew jar

clean:
	./gradlew clean

test:
	./gradlew test

TEMP_TEST_OUTPUT=/tmp/sdk-test-service.log

# Add any extra sdk-test-harness parameters here, such as -skip for tests that are
# temporary not working. Current known test issues are:
# - evaluation doesn't report an error for a malformed attribute reference
# - we should allow an empty key when deserializing old user JSON
# - we should not allow kind: "" in context JSON
TEST_HARNESS_PARAMS= \
    -skip 'evaluation/parameterized/bad attribute reference errors' \
    -skip 'context type/convert'

build-contract-tests:
	@cd contract-tests && ../gradlew installDist

start-contract-test-service:
	@contract-tests/service/build/install/service/bin/service

start-contract-test-service-bg:
	@echo "Test service output will be captured in $(TEMP_TEST_OUTPUT)"
	@make start-contract-test-service >$(TEMP_TEST_OUTPUT) 2>&1 &

run-contract-tests:
	@curl -s https://raw.githubusercontent.com/launchdarkly/sdk-test-harness/v2/downloader/run.sh \
      | VERSION=v2 PARAMS="-url http://localhost:8000 -debug -stop-service-at-end $(TEST_HARNESS_PARAMS)" sh

contract-tests: build-contract-tests start-contract-test-service-bg run-contract-tests

.PHONY: build-contract-tests start-contract-test-service start-contract-test-service-bg run-contract-tests contract-tests
