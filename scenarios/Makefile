MKFILE_PATH := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR := $(patsubst %/,%,$(dir $(MKFILE_PATH)))
UP4_ROOT    := $(CURRENT_DIR)/..
ONOS_ROOT   := $(CURRENT_DIR)/onos-stc
SCENARIOS   := *.xml

INTERACTIVE ?= true

.PHONY = phony $(SCENARIOS)
phony:

$(SCENARIOS): phony
	$(info *** Running STC scenario: $@)
	@export $(shell cat "${DIR}"/.env | xargs) && \
		export PATH=${CURRENT_DIR}/bin:${ONOS_ROOT}/tools/test/bin:${ONOS_ROOT}/tools/test/scenarios/bin:${PATH} && \
		export WORKSPACE=${CURRENT_DIR} && \
		export ONOS_ROOT=${ONOS_ROOT} && \
		export UP4_ROOT=${UP4_ROOT} && \
		stcColor=${INTERACTIVE} stc $@

deps: build pull
	git submodule update --init --depth 1 onos-stc


build:
	docker-compose build

pull:
	docker-compose pull

reset:
	-docker-compose down -t0 --remove-orphans
	-rm -rf ./tmp