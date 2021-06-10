MAKEFILE_DIR := $(dir $(lastword $(MAKEFILE_LIST)))
include $(MAKEFILE_DIR)/standard_defs.mk

export TEST_SPEC ?= --exclude ConfigManagementServiceIT:CMSetAndGetTimeModel

$(MARKERS): test-dars

test-dars:
	mkdir -p test-dars

build: $(MARKERS)/build_mvn $(MARKERS)/build_ledgertest

$(MARKERS)/build_ledgertest: $(MARKERS)
	docker build -f docker/ledger-api-testtool.docker -t ledger-api-testtool:$(ISOLATION_ID) . ;
	docker run --rm -v `pwd`/test-dars:/out \
		ledger-api-testtool:$(ISOLATION_ID) bash \
		-c "java -jar ledger-api-test-tool.jar -x && cp *.dar /out"
	touch $@

package: $(MARKERS)/package_mvn $(MARKERS)/package_docker

$(MARKERS)/package_docker:
	docker-compose -f docker-compose-installed.yaml build
	touch $@

test: test_mvn test_integration

.PHONY: test_mvn
test_mvn:
	$(DOCKER_MVN) test

.PHONY: test_integration
test_integration: package
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test.yaml down \
		-v || true
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test.yaml up \
		--exit-code-from ledger-api-testtool || true
	docker logs $(ISOLATION_ID)_ledger-api-testtool_1 > build/results.txt 2>&1
	./run_tests ./build/results.txt PUBLIC > build/daml-test.results
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test.yaml down \
	 || true

.PHONY: clean_test_integration
clean_test_integration:
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test.yaml \
		rm -f || true
	docker-compose -p $(ISOLATION_ID) -f docker/daml-test.yaml down \
		-v || true

analyze: analyze_sonar_mvn

clean: clean_mvn clean_containers clean_test_integration

.PHONY: clean_containers
clean_containers:
	docker-compose -f docker/docker-compose-build.yaml rm -f || true
	docker-compose -f docker/docker-compose-build.yaml down -v || true

distclean: clean_docker

.PHONY: clean_docker
clean_docker:
	docker-compose -f docker/docker-compose-build.yaml rm -f || true
	docker-compose -f docker/docker-compose-build.yaml down -v --rmi all|| true

publish: $(MARKERS)/publish_mvn
