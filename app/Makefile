# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

CURRENT_UID              := $(shell id -u)
CURRENT_GID              := $(shell id -g)
MKFILE_PATH              := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR              := $(patsubst %/,%,$(dir $(MKFILE_PATH)))

# By default use docker volume to persist mvn cache,
# but allow passing a directory, e.g., local ~/.m2
mvn_cache_docker_volume := mvn-cache-up4
MVN_CACHE ?= ${mvn_cache_docker_volume}
MVN_FLAGS ?=

onos_tools_dir := ${CURRENT_DIR}/../scenarios/onos-stc/tools/package/runtime/bin

include ../util/docker/Makefile.vars

_docker_pull_all:
	docker pull ${MAVEN_IMG}
	docker pull ${PROTOC_IMG}

deps: _docker_pull_all app/external/dbuf/dbuf.proto app/src/main/java/org/omecproject/dbuf/grpc/Dbuf.java

app/src/main/resources/p4info.txt: ../p4src/build/p4info.txt
	mkdir -p app/src/main/resources
	cp ../p4src/build/p4info.txt app/src/main/resources/p4info.txt

app/external/dbuf/dbuf.proto:
	git submodule update --init app/external/dbuf

# TODO (carmelo): it would be nice to have mvn invoke protoc
#  and treat generated sources the mvn way
app/src/main/java/org/omecproject/dbuf/grpc/Dbuf.java: app/external/dbuf/dbuf.proto
	docker run --rm -v ${CURRENT_DIR}/app/external/dbuf:/root/dbuf \
		-v ${CURRENT_DIR}/app/src/main/java:/java_out -w /root/dbuf \
		${PROTOC_IMG} -I=/root/dbuf --java_out=/java_out \
		--plugin=protoc-gen-grpc-java=/usr/bin/protoc-gen-grpc-java --grpc-java_out=/java_out \
		dbuf.proto

_build_resources: \
	app/src/main/resources/p4info.txt \
	app/src/main/java/org/omecproject/dbuf/grpc/Dbuf.java

# Is this still needed if we allow passing a local mvn cache to make build?
local-build: _build_resources
	mvn ${MVN_FLAGS} clean install

build: _build_resources
	docker run -it --rm -v ${CURRENT_DIR}:/root -v ${MVN_CACHE}:/root/.m2 -w /root \
		${MAVEN_IMG} bash -c "mvn ${MVN_FLAGS} clean install && chown -R ${CURRENT_UID}:${CURRENT_GID} /root"

# ci-verify is a build profile specified in onos-dependencies's pom.xml
# It's used to run javadoc validation and other checks that should not
# run on local build, but during CI.
build-ci: MVN_FLAGS := -Pci-verify -Pcoverage
build-ci: build

clean:
	rm -rf app/target/
	rm -rf app/src/main/java/org/omecproject/dbuf/grpc/

p4rt-shell:
	cd ../scenarios && docker-compose exec p4rtsh  \
		/p4runtime-sh/docker_entry_point.sh \
		-v --grpc-addr onos1:51001 \
		--device-id 0 --election-id 0,1

p4rt-set-forwarding:
	cd ../scenarios && docker-compose exec p4rtsh /p4rt/do_program.sh program

p4rt-set-buffering:
	cd ../scenarios && docker-compose exec p4rtsh /p4rt/do_program.sh --buffer program

p4rt-clear:
	cd ../scenarios && docker-compose exec p4rtsh /p4rt/do_program.sh clear

p4rt-bash:
	cd ../scenarios && docker-compose exec p4rtsh /bin/bash

iface-clear:
	cd ../scenarios && docker-compose exec p4rtsh /p4rt/do_selective_clear.sh filter

pdr-clear:
	cd ../scenarios && docker-compose exec p4rtsh /p4rt/do_selective_clear.sh pdr

far-clear:
	cd ../scenarios && docker-compose exec p4rtsh /p4rt/do_selective_clear.sh far

app-load:
	${onos_tools_dir}/onos-app localhost install! app/target/up4-app-1.0.0-SNAPSHOT.oar

app-reload:
	${onos_tools_dir}/onos-app localhost reinstall! app/target/up4-app-1.0.0-SNAPSHOT.oar

