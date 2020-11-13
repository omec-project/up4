MKFILE_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR := $(patsubst %/,%,$(dir $(MKFILE_PATH)))

include ../util/docker/Makefile.vars

UP4_ROOT    := $(CURRENT_DIR)/..
ONOS_ROOT   := $(CURRENT_DIR)/onos
SCENARIOS   := *.xml

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
	@export $(shell cat "${CURRENT_DIR}"/.env | xargs) && \
		export PATH=${CURRENT_DIR}/bin:${ONOS_ROOT}/tools/test/bin:${ONOS_ROOT}/tools/test/scenarios/bin:${ONOS_ROOT}/tools/package/runtime/bin:${PATH} && \
		export WORKSPACE=${CURRENT_DIR} && \
		export ONOS_ROOT=${ONOS_ROOT} && \
		export UP4_ROOT=${UP4_ROOT} && \
		export STC_VERSION=${STC_VERSION} && \
		stc $@

deps: build stc-dev-build
	docker-compose pull
	git submodule update --init --depth 1 onos

#FIXME: remove once 'quotes' branche is merged and 2.6 released:
stc-dev-build:
	rm -rf /tmp/onos-stc
	cd /tmp && git clone -b quotes --depth 1 https://github.com/opennetworkinglab/onos-stc.git
	cd /tmp/onos-stc && mvn -Dmaven.test.skip=true install

# Images used in docker-compose, pull sha-specific tag to maximize chances of reproducible runs.
# TODO: use non-latest for tost, as harbor doesn't store for long untagged images
_docker_pull:
	docker pull ${ONOS_IMG}@${ONOS_SHA}
	docker tag ${ONOS_IMG}@${ONOS_SHA} ${ONOS_IMG}

build:
	docker-compose build --pull

reset:
	-docker-compose down -t0 --remove-orphans
	-rm -rf ./tmp
	-rm -rf /tmp/stc

set-app-info:
	util/onos-cmd log:set INFO org.omecproject.up4

set-app-debug:
	util/onos-cmd log:set DEBUG org.omecproject.up4

mn-cli:
	$(info *** Attaching to Mininet CLI...)
	$(info *** To detach press Ctrl-D (Mininet will keep running))
	-@docker attach --detach-keys "ctrl-d" $(shell docker-compose ps -q mininet) || echo "*** Detached from Mininet CLI"

mn-log:
	docker logs -f mininet

onos-cli:
	ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" -o LogLevel=ERROR -p 8101 localhost

onos-log:
	docker-compose logs -f onos1

onos-ui:
	open http://localhost:8181/onos/ui