export ISOLATION_ID ?= local
PWD = $(shell pwd)

export MAKE_BIN ?= $(PWD)/bin

ORGANIZATION ?= $(shell git remote show -n origin | grep Fetch | \
	awk '{print $$NF}' | sed -e 's/git@github.com://' | \
	sed -e 's@https://github.com/@@' | awk -F'[/.]' '{print $$1}' )
REPO ?= $(shell git remote show -n origin | grep Fetch | awk '{print $$NF}' | \
	sed -e 's/git@github.com://' | sed -e 's@https://github.com/@@' | \
	awk -F'[/.]' '{print $$2}' )
BRANCH_NAME ?= $(shell git symbolic-ref -q HEAD |sed -e 's@refs/heads/@@')
SAFE_BRANCH_NAME ?= $(shell if [ -n "$$BRANCH_NAME" ]; then echo $$BRANCH_NAME;\
	else git symbolic-ref -q HEAD|sed -e 's@refs/heads/@@'|sed -e 's@/@_@g'; fi)
PR_KEY=$(shell echo $(BRANCH_NAME) | cut -c4-)
VERSION ?= $(shell git describe | cut -c2-  )
LONG_VERSION ?= $(shell git describe --long --dirty |cut -c2- )
UID := $(shell id -u)
GID := $(shell id -g)

RELEASABLE != if [ "$(LONG_VERSION)" = "$(VERSION)" ] || \
	(echo "$(LONG_VERSION)" | grep -q dirty); then \
	  echo "no"; else echo "yes"; fi

MARKERS = markers

##
# SonarQube0
##
SONAR_HOST_URL ?= https://sonarcloud.io
SONAR_AUTH_TOKEN ?=

##
# Kubernetes Settings
##
KUBE_CONFIG ?= $(PWD)/dummy-kubeconfig.yaml

##
# Maven related settings
##
MAVEN_SETTINGS ?= $(shell if [ -r $(HOME)/.m2/settings.xml ]; then \
	echo $(HOME)/.m2/settings.xml; else echo ""; fi)

MAVEN_REVISION != if [ "$(LONG_VERSION)" = "$(VERSION)" ] || \
	      (echo "$(LONG_VERSION)" | grep -q dirty); then \
	    bump_ver=$(VERSION); \
	    if [ -x bin/semver ]; then \
	      bump_ver=$$(bin/semver bump patch $(VERSION))-SNAPSHOT; \
	    elif command -v semver >/dev/null; then \
	      bump_ver=$$(command semver bump patch $(VERSION))-SNAPSHOT; \
	    fi; \
	    echo $$bump_ver ; \
	  else \
	    echo $(VERSION); \
	  fi

MAVEN_REPO_BASE ?= https://dev.catenasys.com/repository/catenasys-maven
MAVEN_REPO_TARGET != if [ "$(LONG_VERSION)" = "$(VERSION)" ] || \
	  (echo "$(LONG_VERSION)" | grep -q dirty); then echo snapshots; \
	else \
	  echo releases;\
	fi
MAVEN_UPDATE_RELEASE_INFO != if [ "$(LONG_VERSION)" = "$(VERSION)" ] || \
	(echo "$(LONG_VERSION)" | grep -q dirty); then \
	  echo false; \
	else \
	  echo true; \
	fi
MAVEN_DEPLOY_TARGET = \
	$(MAVEN_REPO_TARGET)::default::$(MAVEN_REPO_BASE)-$(MAVEN_REPO_TARGET)

##
# Docker based toolchain
##
DOCKER_RUN = docker run --rm
DOCKER_RUN_USER = $(DOCKER_RUN) --user $(UID):$(GID)
DOCKER_RUN_ROOT = $(DOCKER_RUN) --user 0:0
DOCKER_RUN_DEFAULT = $(DOCKER_RUN)
DOCKER_SOCK ?= /var/run/docker.sock

#TOOLCHAIN_IMAGE = toolchain:$(ISOLATION_ID)
TOOLCHAIN_IMAGE := blockchaintp/toolchain:latest
TOOLCHAIN_HOME := /home/toolchain

MAVEN_SETTINGS_VOL = $(shell if [ -n "$(MAVEN_SETTINGS)" ] && [ -r "$(MAVEN_SETTINGS)" ]; then echo -v \
	$(MAVEN_SETTINGS):$(TOOLCHAIN_HOME)/.m2/settings.xml; fi)

KUBE_CONFIG_VOL = $(shell if [ -n "$(KUBE_CONFIG)" ] && [ -r "$(KUBE_CONFIG)" ]; then echo -v \
	$(KUBE_CONFIG):$(TOOLCHAIN_HOME)/.kube/config; fi)
KUBE_CONFIG_ENV = $(shell if [ -n "$(KUBE_CONFIG)" ] && [ -r "$(KUBE_CONFIG)" ]; then echo "-e \
	KUBECONFIG=$(TOOLCHAIN_HOME)/.kube/config"; fi)

MINIKUBE_HOME ?= $(HOME)/.minikube
MINIKUBE_VOL = $(shell if [ -n "$(MINIKUBE_HOME)" ] && [ -d "$(MINIKUBE_HOME)" ]; then echo -v \
	$(MINIKUBE_HOME):$(MINIKUBE_HOME); fi)

TOOL_VOLS = -v toolchain-home-$(ISOLATION_ID):/home/toolchain \
	$(MAVEN_SETTINGS_VOL) $(KUBE_CONFIG_VOL) \
	-v $(PWD):/project $(MINIKUBE_VOL)

TOOL_ENVIRONMENT = -e GITHUB_TOKEN -e MAVEN_HOME=/home/toolchain/.m2 \
	-e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e FOSSA_API_KEY \
	$(KUBE_CONFIG_ENV)

TOOL_NOWORKDIR = $(DOCKER_RUN_USER) $(TOOL_ENVIRONMENT) $(TOOL_VOLS)
TOOL_DEFAULT_NOWORKDIR = $(DOCKER_RUN_DEFAULT) $(TOOL_ENVIRONMENT) $(TOOL_VOLS)
TOOL = $(TOOL_NOWORKDIR) -w $${WORKDIR:-/project}
TOOL_DEFAULT = $(TOOL_DEFAULT_NOWRKDIR) -w $${WORKDIR:-/project}

TOOLCHAIN := $(TOOL) $(TOOLCHAIN_IMAGE)
DOCKER_MVN := $(TOOLCHAIN) mvn -Drevision=$(MAVEN_REVISION) -B
BUSYBOX := $(DOCKER_RUN_USER) $(TOOL_VOLS) busybox:latest
BUSYBOX_ROOT := $(DOCKER_RUN_ROOT) $(TOOL_VOLS) busybox:latest

DIVE_ANALYZE = $(TOOL) -v $(DOCKER_SOCK):/var/run/docker.sock \
	--user toolchain:$(shell getent group docker|awk -F: '{print $$3}') \
	$(TOOLCHAIN_IMAGE) dive --ci

NPM := $(TOOLCHAIN) npm

##
# FOSSA parameters
###
FOSSA_TIMEOUT ?= 1200

CLEAN_DIRS = build markers target
##
# BEGIN Standardized directives
##

##
# Standard targets
##

# A complete build sequence
.PHONY: all
.DEFAULT: all
all: build test package analyze archive

# Clean everything but the docker images
.PHONY: clean
clean: clean_dirs

# Clean everything and the docker image, leaving the repo pristine
.PHONY: distclean
distclean: clean
	rm -rf $(CLEAN_DIRS)

# All compilation tasks
.PHONY: build
build: $(MARKERS)/build_dirs

# All tasks which produce a test result
.PHONY: test
test: build

# Produce packages
.PHONY: package
package: build

# Linting, Static Analysis, Test Analysis and reporting
.PHONY: analyze
analyze:

# Archive the source code
.PHONY: archive
archive: $(MARKERS) archive_git

# Any publishing to external systems other than docker registries, e.g. Maven
.PHONY: publish
publish: package

# Run up a useful local environment of this build
.PHONY: run
run: package

.PHONY: acquire_project
acquire_project:
	mkdir -p project
	if [ -n "$(PROJECT_GIT_URL)" ]; then \
	  git clone $(PROJECT_GIT_URL) project ; \
	  cd project; \
	  git checkout $(PROJECT_BRANCH) ; \
	fi

.PHONY: clean_project
clean_project:
	rm -rf project

.PHONY: project_%
project_%:
	cd project; $(MAKE) -f ../$(firstword $(MAKEFILE_LIST)) $(shell echo $@ | cut -c9-)

.PHONY: analyze_fossa
analyze_fossa:
	if [ -z "$(CHANGE_BRANCH)" ]; then \
	  $(TOOLCHAIN) fossa analyze --verbose \
	    --no-ansi -b ${BRANCH_NAME}; \
	  $(TOOLCHAIN) fossa test --verbose \
	    --no-ansi -b ${BRANCH_NAME} \
	    --timeout $(FOSSA_TIMEOUT) ; \
	else \
	  $(TOOLCHAIN) fossa analyze --verbose \
	    --no-ansi -b ${CHANGE_BRANCH}; \
	  $(TOOLCHAIN) fossa test --verbose \
	    --no-ansi -b ${CHANGE_BRANCH} \
	    --timeout $(FOSSA_TIMEOUT) ; \
	fi

.PHONY: analyze_go
analyze_go: $(MARKERS)/build_toolchain_docker
	$(TOOLCHAIN) bash -c "go vet ./..."

# Maven Version of Sonar Analysis
.PHONY: analyze_sonar_mvn
analyze_sonar_mvn: $(MARKERS)/build_toolchain_docker
	[ -z "$(SONAR_AUTH_TOKEN)" ] || \
	  if [ -z "$(CHANGE_BRANCH)" ]; then \
	    $(DOCKER_MVN) verify sonar:sonar \
	        -Dsonar.organization=$(ORGANIZATION) \
	        -Dsonar.projectKey=$(ORGANIZATION)_$(REPO) \
	        -Dsonar.projectName="$(ORGANIZATION)/$(REPO)" \
	        -Dsonar.branch.name=$(BRANCH_NAME) \
	        -Dsonar.projectVersion=$(VERSION) \
	        -Dsonar.host.url=$(SONAR_HOST_URL) \
	        -Dsonar.junit.reportPaths=target/surefire-reports,**/target/surefire-reports \
	        -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml,**/target/site/jacoco/jacoco.xml \
	        -Dsonar.login=$(SONAR_AUTH_TOKEN) ; \
	  else \
	    $(DOCKER_MVN) verify sonar:sonar \
	        -Dsonar.organization=$(ORGANIZATION) \
	        -Dsonar.projectKey=$(ORGANIZATION)_$(REPO) \
	        -Dsonar.projectName="$(ORGANIZATION)/$(REPO)" \
	        -Dsonar.pullrequest.key=$(PR_KEY) \
	        -Dsonar.pullrequest.branch=$(CHANGE_BRANCH) \
	        -Dsonar.pullrequest.base=$(CHANGE_TARGET) \
	        -Dsonar.projectVersion=$(VERSION) \
	        -Dsonar.host.url=$(SONAR_HOST_URL) \
	        -Dsonar.junit.reportPaths=target/surefire-reports,**/target/surefire-reports \
	        -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml,**/target/site/jacoco/jacoco.xml \
	        -Dsonar.login=$(SONAR_AUTH_TOKEN) ; \
	  fi

.PHONY: analyze_sonar_generic
analyze_sonar_generic:
	[ -z "$(SONAR_AUTH_TOKEN)" ] || \
	  if [ -z "$(CHANGE_BRANCH)" ]; then \
	    $(DOCKER_RUN_USER) \
	      -v $$(pwd):/usr/src \
	      sonarsource/sonar-scanner-cli \
	        -Dsonar.organization=$(ORGANIZATION) \
	        -Dsonar.projectKey=$(ORGANIZATION)_$(REPO) \
	        -Dsonar.projectName="$(ORGANIZATION)/$(REPO)" \
	        -Dsonar.branch.name=$(BRANCH_NAME) \
	        -Dsonar.projectVersion=$(VERSION) \
	        -Dsonar.host.url=$(SONAR_HOST_URL) \
	        -Dsonar.login=$(SONAR_AUTH_TOKEN) \
	        -Dsonar.junit.reportPaths=target/surefire-reports,**/target/surefire-reports; \
	  else \
	    $(DOCKER_RUN_USER) \
	      -v $$(pwd):/usr/src \
	      sonarsource/sonar-scanner-cli \
	        -Dsonar.organization=$(ORGANIZATION) \
	        -Dsonar.projectKey=$(ORGANIZATION)_$(REPO) \
	        -Dsonar.projectName="$(ORGANIZATION)/$(REPO)" \
	        -Dsonar.pullrequest.key=$(PR_KEY) \
	        -Dsonar.pullrequest.branch=$(CHANGE_BRANCH) \
	        -Dsonar.pullrequest.base=$(CHANGE_TARGET) \
	        -Dsonar.projectVersion=$(VERSION) \
	        -Dsonar.host.url=$(SONAR_HOST_URL) \
	        -Dsonar.login=$(SONAR_AUTH_TOKEN) \
	        -Dsonar.junit.reportPaths=target/surefire-reports,**/target/surefire-reports; \
	  fi

.PHONY: analyze_sonar_js
analyze_sonar_js:
	[ -z "$(SONAR_AUTH_TOKEN)" ] || \
	  if [ -z "$(CHANGE_BRANCH)" ]; then \
	    $(DOCKER_RUN_USER) \
	      -v $$(pwd):/usr/src \
	      sonarsource/sonar-scanner-cli \
	        -Dsonar.organization=$(ORGANIZATION) \
	        -Dsonar.projectKey=$(ORGANIZATION)_$(REPO) \
	        -Dsonar.projectName="$(ORGANIZATION)/$(REPO)" \
	        -Dsonar.branch.name=$(BRANCH_NAME) \
	        -Dsonar.projectVersion=$(LONG_VERSION) \
	        -Dsonar.host.url=$(SONAR_HOST_URL) \
	        -Dsonar.login=$(SONAR_AUTH_TOKEN) \
	        -Dsonar.sources=src \
	        -Dsonar.tests=test \
	        -Dsonar.junit.reportPaths=build/junit.xml \
	        -Dsonar.javascript.lcov.reportPaths=build/lcov.info; \
	  else \
	    $(DOCKER_RUN_USER) \
	      -v $$(pwd):/usr/src \
	      sonarsource/sonar-scanner-cli \
	        -Dsonar.organization=$(ORGANIZATION) \
	        -Dsonar.projectKey=$(ORGANIZATION)_$(REPO) \
	        -Dsonar.projectName="$(ORGANIZATION)/$(REPO)" \
	        -Dsonar.pullrequest.key=$(PR_KEY) \
	        -Dsonar.pullrequest.branch=$(CHANGE_BRANCH) \
	        -Dsonar.pullrequest.base=$(CHANGE_TARGET) \
	        -Dsonar.projectVersion=$(LONG_VERSION) \
	        -Dsonar.host.url=$(SONAR_HOST_URL) \
	        -Dsonar.login=$(SONAR_AUTH_TOKEN) \
	        -Dsonar.sources=src \
	        -Dsonar.tests=test \
	        -Dsonar.junit.reportPaths=build/junit.xml \
	        -Dsonar.javascript.lcov.reportPaths=build/lcov.info; \
	  fi

.PHONY: archive_git
archive_git: build/$(REPO)-$(VERSION).zip build/$(REPO)-$(VERSION).tgz

build/$(REPO)-$(VERSION).zip:
	if [ -d .git ]; then \
	  git archive HEAD --format=zip -9 --output=build/$(REPO)-$(VERSION).zip; \
	fi

build/$(REPO)-$(VERSION).tgz:
	if [ -d .git ]; then \
	  git archive HEAD --format=zip -9 --output=build/$(REPO)-$(VERSION).tgz; \
	fi

$(MARKERS)/toolchain_vols: $(MARKERS)/build_dirs
	docker volume create toolchain-home-$(ISOLATION_ID)
	$(BUSYBOX_ROOT) chown -R $(UID):$(GID) $(TOOLCHAIN_HOME)
	@touch $@

$(MARKERS)/build_toolchain_docker: $(MARKERS)/build_dirs $(MARKERS)/toolchain_vols
	if ! docker image ls -qq $(TOOLCHAIN_IMAGE) > /dev/null; then \
	  echo "Pulling toolchain $(TOOLCHAIN_IMAGE)"; \
	  docker pull -qq $(TOOLCHAIN_IMAGE); \
	else \
	  echo "Toolchain $(TOOLCHAIN_IMAGE) already available"; \
	fi
	@touch $@

.PHONY: clean_toolchain_docker
clean_toolchain_docker:
	docker rmi -f $(TOOLCHAIN_IMAGE) || true
	docker volume rm -f toolchain-home-$(ISOLATION_ID) || true
	rm -f $(MARKERS)/toolchain_vols
	rm -f $(MARKERS)/build_toolchain_docker

.PHONY: fix_permissions
fix_permissions:
	$(BUSYBOX_ROOT) chown -R $(UID):$(GID) $(TOOLCHAIN_HOME)/.m2/repository || true
	$(BUSYBOX_ROOT) chown -R $(UID):$(GID) /project || true

# This will reset the build status possible causing steps to rerun
.PHONY: clean_markers
clean_markers:
	rm -rf $(CLEAN_DIRS)

$(MARKERS)/build_go: $(MARKERS)/build_dirs $(MARKERS)/build_toolchain_docker
	$(TOOLCHAIN) bash -c "if [ -r scripts/build ]; then scripts/build; else go build ./...; fi"
	@touch $@

.PHONY: clean_build_go
clean_build_go: $(MARKERS)/build_dirs $(MARKERS)/build_toolchain_docker
	$(TOOLCHAIN) go clean ./...
	rm -f $(MARKERS)/build_go

$(MARKERS)/build_mvn: $(MARKERS)/build_dirs $(MARKERS)/build_toolchain_docker
	$(DOCKER_MVN) compile
	@touch $@

$(MARKERS)/package_mvn: $(MARKERS)/build_dirs $(MARKERS)/build_toolchain_docker
	$(DOCKER_MVN) package
	@touch $@

clean_mvn: $(MARKERS)/build_toolchain_docker
	$(DOCKER_MVN) clean

##
# For this to work properly in pom.xml you need
# <skipTests>true</skipTests> in the maven-surefire-plugin
# configuration.
##
$(MARKERS)/test_mvn: $(MARKERS)/build_dirs $(MARKERS)/build_toolchain_docker
	$(DOCKER_MVN) -DskipTests=false test
	@touch $@

$(MARKERS)/test_go: $(MARKERS)/build_dirs $(MARKERS)/build_toolchain_docker
	$(TOOLCHAIN) go test ./...
	@touch $@

$(MARKERS)/publish_mvn: $(MARKERS)/build_toolchain_docker
	echo $(DOCKER_MVN) clean deploy -DupdateReleaseInfo=$(MAVEN_UPDATE_RELEASE_INFO) \
	  -DaltDeploymentRepository=$(MAVEN_DEPLOY_TARGET)
	$(DOCKER_MVN) -Drevision=0.0.0 versions:set -DnewVersion=$(MAVEN_REVISION)
	$(DOCKER_MVN) clean deploy -DupdateReleaseInfo=$(MAVEN_UPDATE_RELEASE_INFO) \
	  -DaltDeploymentRepository=$(MAVEN_DEPLOY_TARGET)

.PHONY: effective-pom
effective-pom: $(MARKERS)/build_toolchain_docker
	$(DOCKER_MVN) help:effective-pom

# Any prerequisite directories, which should also be gitignored
$(MARKERS)/build_dirs:
	mkdir -p $(CLEAN_DIRS)
	mkdir -p build
	mkdir -p $(MARKERS)
	touch $@

.PHONY: clean_dirs_standard
clean_dirs_standard:
	rm -rf $(CLEAN_DIRS)

.PHONY: clean_dirs
clean_dirs: clean_dirs_standard

$(MARKERS)/check_ignores: $(MARKERS)/build_dirs
	git check-ignore build
	git check-ignore $(MARKERS)
	@touch $@

.PHONY: what_version
what_version:
	@echo VERSION=$(VERSION)
	@echo LONG_VERSION=$(LONG_VERSION)
	@echo MAVEN_REVISION=$(MAVEN_REVISION)

##
# END Standardized directives
##

##
# GitHub release directives
##
GH := $(TOOLCHAIN) gh
GH_RELEASE = $(GH) release

.PHONY: gh-create-draft-release
gh-create-draft-release:
	if [ "$(RELEASABLE)" = "yes" ];then \
	  $(GH_RELEASE) create $(VERSION) -t "$(VERSION)" -F CHANGELOG.md; \
	fi

$(MARKERS)/analyze_npm: $(MARKERS)/build_dirs $(MARKERS)/build_toolchain_docker
	$(NPM) run audit
	@touch $@

$(MARKERS)/build_npm: $(MARKERS)/build_dirs $(MARKERS)/build_toolchain_docker
	$(NPM) ci
	$(NPM) run build
	@touch $@

$(MARKERS)/test_npm: $(MARKERS)/build_dirs $(MARKERS)/build_npm
	$(NPM) run test
	@touch $@
