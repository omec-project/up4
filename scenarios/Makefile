# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: Apache-2.0

OSTYPE := $(shell uname -s)
ifeq ($(OSTYPE),Linux)
NPROCS := $(shell nproc)
else ifeq ($(OSTYPE),Darwin) # Assume Mac OS X
NPROCS := $(shell sysctl -n hw.physicalcpu)
else
NPROCS := 1
endif

MKFILE_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR := $(patsubst %/,%,$(dir $(MKFILE_PATH)))

UP4_ROOT           := $(abspath $(CURRENT_DIR)/..)
ONOS_TEST          := $(CURRENT_DIR)/onos-test
SCENARIOS          := *.xml
DOCKER_COMPOSE_CMD := docker-compose --env-file ./tmp/.env.docker
CURRENT_USER       := $(shell id -u):$(shell id -g)

# Set to latest to use latest versions of components
UP4_ENV ?= stable

# Loading the env file allows to use the env variable as make variable
ifeq ($(UP4_ENV),latest)
include ../.env.latest
else
include ../.env.stable
endif

# Used when building the sdfabric-onos image
UP4_VERSION := $(shell git rev-parse --abbrev-ref HEAD)

ifeq ($(ONOS_IMAGE),sdfabric-onos-local:master)
BUILD_SDFABRIC_ONOS := true
# UP4 is built as part of sdfabric-onos, to avoid pushing UP4, we need to set
# PUSH_UP4 to a null value
PUSH_UP4            :=
else
BUILD_SDFABRIC_ONOS := false
PUSH_UP4            := true
endif

# Extracts the services from docker-compose, this is needed to avoid pulling
# sdfabric-onos images built locally, when downloading dependencies for docker-compose
STC_COMPOSE_SERVICES = $(shell $(CURRENT_DIR)/bin/parse_compose_services '$(CURRENT_DIR)/docker-compose.yml')
ifeq ($(BUILD_SDFABRIC_ONOS),true)
#avoid pulling the ONOS image if we build it locally
STC_COMPOSE_SERVICES_TO_PULL := $(filter-out onos%,$(STC_COMPOSE_SERVICES))
else
STC_COMPOSE_SERVICES_TO_PULL := $(STC_COMPOSE_SERVICES)
endif

# TODO: release 2.6 with support for quotes in step commands
STC_VERSION ?= 2.6-SNAPSHOT

# Define the topology, it can be leafspine or singlepair
TOPO ?= leafspine

STC_ENV := .env.stc-${TOPO}
TOPO_NETCFG := ${UP4_ROOT}/scenarios/topo/netcfg-${TOPO}.json

.PHONY: $(SCENARIOS)
.PRECIOUS: $(SCENARIOS)

# Some onos-* commands expect to be able to write in /tmp/stc
# (e.g., onos-check-components)
/tmp/stc:
	@mkdir -p /tmp/stc

# Create ./tmp before Docker does so it doesn't have root owner.
./tmp:
	@mkdir -p ./tmp

# Empty files to record the last value of a variable
./tmp/makevar.env.$(UP4_ENV): ./tmp
	@rm -f ./tmp/makevar.env.*
	@touch $@

./tmp/.env.docker: ./tmp/makevar.env.$(UP4_ENV)
	@echo "# Generated with make ./tmp/.env.docker" > ./tmp/.env.docker
	@cat ${UP4_ROOT}/.env.stable >> ./tmp/.env.docker
ifneq "${UP4_ENV}" "stable"
	@cat ${UP4_ROOT}/.env.${UP4_ENV} >> ./tmp/.env.docker
endif
	@echo "MN_SCRIPT=/topo/topo-gtp-${TOPO}.py" >> ./tmp/.env.docker
	@echo "STC_ENV_FILE=${STC_ENV}" >> ./tmp/.env.docker

$(SCENARIOS): onos-test ./tmp /tmp/stc ./tmp/.env.docker
	$(info *** Running STC scenario: $@)
	@export $(shell cat "${CURRENT_DIR}"/"${STC_ENV}" | grep -v '#' | xargs) && \
		export PATH="${CURRENT_DIR}/bin:${ONOS_TEST}/tools/test/bin:${ONOS_TEST}/tools/test/scenarios/bin:${ONOS_TEST}/tools/package/runtime/bin:${PATH}" && \
		export WORKSPACE=${CURRENT_DIR} && \
		export ONOS_ROOT=${ONOS_TEST} && \
		export UP4_ROOT=${UP4_ROOT} && \
		export STC_VERSION=${STC_VERSION} && \
		export DOCKER_COMPOSE_CMD="${DOCKER_COMPOSE_CMD}" && \
		export STC_ENV_FILE=${STC_ENV} && \
		export TOPO_NETCFG=${TOPO_NETCFG} && \
		export PUSH_UP4=$(PUSH_UP4) && \
		stc $@

deps: build-sdfabric-onos pull build stc-dev-build onos-test

onos-test:
	curl -sS --fail https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.5.4/onos-test-2.5.4.tar.gz --output onos-test.tar.gz
	tar xf onos-test.tar.gz
	rm onos-test.tar.gz
	mv onos-test-2.5.4 onos-test

#FIXME: remove once 'quotes' branch is merged and 2.6 released:
stc-dev-build:
	rm -rf /tmp/onos-stc
	cd /tmp && git clone -b quotes --depth 1 https://github.com/opennetworkinglab/onos-stc.git
	cd /tmp/onos-stc && mvn -q -B -Dmaven.test.skip=true install

pull: ./tmp/.env.docker
	${DOCKER_COMPOSE_CMD} pull $(STC_COMPOSE_SERVICES_TO_PULL)

build: ./tmp/.env.docker
	${DOCKER_COMPOSE_CMD} build --pull $(STC_COMPOSE_SERVICES_TO_PULL)

build-sdfabric-onos: .build-sdfabric-onos
.build-sdfabric-onos: ./tmp/.env.docker sdfabric-onos
ifeq ($(BUILD_SDFABRIC_ONOS),true)
	cd sdfabric-onos && \
	git pull origin master && \
	ONOS_VERSION=${ONOS_VERSION} \
		TRELLIS_CONTROL_VERSION=${TRELLIS_CONTROL_VERSION} \
		FABRIC_TNA_VERSION=${FABRIC_TNA_VERSION} \
		UP4_VERSION=${UP4_VERSION} \
		USE_LOCAL_SNAPSHOT_ARTIFACTS=true \
		DOCKER_TAG=master \
		DOCKER_REPOSITORY=`echo ${ONOS_IMAGE} | sed 's/:master//'` \
		make docker-build JOBS=${NPROCS} SHOW_SENSITIVE_OUTPUT=false
	@# use the .build-sdfabric-onos to cache the current built sdfabric-onos image
	@# to reset it, call "make reset-sdfabric-onos"
	touch .build-sdfabric-onos
endif

sdfabric-onos:
ifeq ($(BUILD_SDFABRIC_ONOS),true)
	git clone "https://gerrit.onosproject.org/sdfabric-onos" sdfabric-onos
endif

reset-sdfabric-onos:
	-rm .build-sdfabric-onos

reset:
	-${DOCKER_COMPOSE_CMD} down -t0 --remove-orphans
	-make fix-permissions
	-rm -rf ./tmp
	-rm -rf /tmp/stc

set-app-info:
	util/onos-cmd log:set INFO org.omecproject.up4

set-app-debug:
	util/onos-cmd log:set DEBUG org.omecproject.up4

mn-cli: ./tmp/.env.docker
	$(info *** Attaching to Mininet CLI...)
	$(info *** To detach press Ctrl-D (Mininet will keep running))
	-@docker attach --detach-keys "ctrl-d" $(shell ${DOCKER_COMPOSE_CMD} ps -q mininet) || echo "*** Detached from Mininet CLI"

mn-log:
	docker logs -f mininet

onos-cli:
	ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" -o LogLevel=ERROR -p 8101 localhost

onos-log: ./tmp/.env.docker
	${DOCKER_COMPOSE_CMD} logs -f onos1

onos-ui:
	open http://localhost:8181/onos/ui

mock-smf: ./tmp/.env.docker
	${DOCKER_COMPOSE_CMD} exec mock-smf /up4/bin/mock-smf.py pfcp-agent \
		--pcap-file /tmp/mock-smf.pcap -vvv

p4rt-sh: ./tmp/.env.docker
	${DOCKER_COMPOSE_CMD} exec p4rt \
		python3 -m p4runtime_sh \
		--grpc-addr onos1:51001 \
		--device-id 1 --election-id 0,1

# This is required for Jenkins to be able to archive build artifacts. Files created by most
# containers will have root owner. Jenkins doesn't allow sudo, run chown inside a container as there
# we can be root.
# FIXME: The right solution is to run containers as non-root.
fix-permissions:
	test -d ./tmp && docker run --rm -v ${CURRENT_DIR}/tmp:/tmp privatebin/chown -R ${CURRENT_USER} /tmp || true

versions: ./tmp/.env.docker
	@echo "UP4_ENV=${UP4_ENV}"
	@export $(shell cat "${CURRENT_DIR}"/tmp/.env.docker | grep -v '#' | xargs) && \
	echo $${ONOS_IMAGE} && \
	docker inspect $${ONOS_IMAGE} | jq -r '.[0].Config.Labels' && \
	echo $${DBUF_IMAGE} && \
    docker inspect $${DBUF_IMAGE} | jq -r '.[0].Config.Labels' && \
    echo $${PFCP_AGENT_IMAGE} && \
    docker inspect $${PFCP_AGENT_IMAGE} | jq -r '.[0].Config.Labels' && \
    echo $${MN_STRATUM_IMAGE} && \
    docker inspect $${MN_STRATUM_IMAGE} | jq -r '.[0].Config.Labels'
