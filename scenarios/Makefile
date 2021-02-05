# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

MKFILE_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR := $(patsubst %/,%,$(dir $(MKFILE_PATH)))

UP4_ROOT           := $(abspath $(CURRENT_DIR)/..)
ONOS_ROOT          := $(CURRENT_DIR)/onos
SCENARIOS          := *.xml
DOCKER_COMPOSE_CMD := docker-compose --env-file ./tmp/.env.docker
CURRENT_USER       := $(shell id -u):$(shell id -g)

# Set to devel to use in-development versions of components
UP4_ENV ?= stable

# TODO: release 2.6 with support for quotes in step commands
STC_VERSION ?= 2.6-SNAPSHOT

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

$(SCENARIOS): ./tmp /tmp/stc ./tmp/.env.docker
	$(info *** Running STC scenario: $@)
	@export $(shell cat "${CURRENT_DIR}"/.env.stc | grep -v '#' | xargs) && \
		export PATH="${CURRENT_DIR}/bin:${ONOS_ROOT}/tools/test/bin:${ONOS_ROOT}/tools/test/scenarios/bin:${ONOS_ROOT}/tools/package/runtime/bin:${PATH}" && \
		export WORKSPACE=${CURRENT_DIR} && \
		export ONOS_ROOT=${ONOS_ROOT} && \
		export UP4_ROOT=${UP4_ROOT} && \
		export STC_VERSION=${STC_VERSION} && \
		export DOCKER_COMPOSE_CMD="${DOCKER_COMPOSE_CMD}" && \
		stc $@

deps: pull build stc-dev-build
	git submodule update --init --depth 1 onos

#FIXME: remove once 'quotes' branch is merged and 2.6 released:
stc-dev-build:
	rm -rf /tmp/onos-stc
	cd /tmp && git clone -b quotes --depth 1 https://github.com/opennetworkinglab/onos-stc.git
	cd /tmp/onos-stc && mvn -Dmaven.test.skip=true install

pull: ./tmp/.env.docker
	${DOCKER_COMPOSE_CMD} pull

build: ./tmp/.env.docker
	${DOCKER_COMPOSE_CMD} build --pull

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

mock-smf:
	docker-compose exec mock-smf /up4/bin/mock-smf.py pfcp-agent \
		--pcap-file /tmp/mock-smf.pcap -vvv

p4rt-sh:
	docker-compose exec p4rt \
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

