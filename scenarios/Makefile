# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

MKFILE_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR := $(patsubst %/,%,$(dir $(MKFILE_PATH)))

UP4_ROOT           := $(abspath $(CURRENT_DIR)/..)
ONOS_ROOT          := $(CURRENT_DIR)/onos
SCENARIOS          := *.xml
DOCKER_COMPOSE_CMD := docker-compose --env-file $(UP4_ROOT)/.env.docker

# TODO: release 2.6 with support for quotes in step commands
STC_VERSION ?= 2.6-SNAPSHOT

.PHONY: $(SCENARIOS)
.PRECIOUS: $(SCENARIOS)

# Some onos-* commands expect to be able to write in /tmp/stc
# (e.g., onos-check-components)
/tmp/stc:
	@mkdir -p /tmp/stc

$(SCENARIOS): /tmp/stc
	$(info *** Running STC scenario: $@)
	@export $(shell cat "${CURRENT_DIR}"/.env.stc | grep -v '#' | xargs) && \
		export PATH="${CURRENT_DIR}/bin:${ONOS_ROOT}/tools/test/bin:${ONOS_ROOT}/tools/test/scenarios/bin:${ONOS_ROOT}/tools/package/runtime/bin:${PATH}" && \
		export WORKSPACE=${CURRENT_DIR} && \
		export ONOS_ROOT=${ONOS_ROOT} && \
		export UP4_ROOT=${UP4_ROOT} && \
		export STC_VERSION=${STC_VERSION} && \
		export DOCKER_COMPOSE_CMD="${DOCKER_COMPOSE_CMD}" && \
		stc $@

deps: build stc-dev-build
	${DOCKER_COMPOSE_CMD} pull
	git submodule update --init --depth 1 onos

#FIXME: remove once 'quotes' branche is merged and 2.6 released:
stc-dev-build:
	rm -rf /tmp/onos-stc
	cd /tmp && git clone -b quotes --depth 1 https://github.com/opennetworkinglab/onos-stc.git
	cd /tmp/onos-stc && mvn -Dmaven.test.skip=true install

build:
	${DOCKER_COMPOSE_CMD} build --pull

reset:
	-${DOCKER_COMPOSE_CMD} down -t0 --remove-orphans
	-rm -rf ./tmp
	-rm -rf /tmp/stc

set-app-info:
	util/onos-cmd log:set INFO org.omecproject.up4

set-app-debug:
	util/onos-cmd log:set DEBUG org.omecproject.up4

mn-cli:
	$(info *** Attaching to Mininet CLI...)
	$(info *** To detach press Ctrl-D (Mininet will keep running))
	-@docker attach --detach-keys "ctrl-d" $(shell ${DOCKER_COMPOSE_CMD} ps -q mininet) || echo "*** Detached from Mininet CLI"

mn-log:
	docker logs -f mininet

onos-cli:
	ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" -o LogLevel=ERROR -p 8101 localhost

onos-log:
	${DOCKER_COMPOSE_CMD} logs -f onos1

onos-ui:
	open http://localhost:8181/onos/ui

mock-smf:
	docker-compose exec mock-smf /up4/bin/mock-smf.py pfcp-agent

p4rt-sh:
	docker-compose exec p4rtsh \
		/p4runtime-sh/docker_entry_point.sh \
		--grpc-addr onos1:51001 \
		--device-id 0 --election-id 0,1
