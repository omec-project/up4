# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

onos_url := http://localhost:8181/onos
onos_curl := curl --fail -sSL --user onos:rocks --noproxy localhost

include ../util/docker/Makefile.vars


ONOS_BOOT_TIME := 30
CONFIG_WAIT_TIME := 10
PKT_WAIT_TIME := 5



_docker_pull_all:
	docker pull ${P4RT_SH_IMG}@${P4RT_SH_SHA}
	docker tag ${P4RT_SH_IMG}@${P4RT_SH_SHA} ${P4RT_SH_IMG}
	docker pull ${MN_TUTORIAL_IMG}@${MN_TUTORIAL_SHA}
	docker tag ${MN_TUTORIAL_IMG}@${MN_TUTORIAL_SHA} ${MN_TUTORIAL_IMG}
	docker pull ${ONOS_IMG}@${ONOS_SHA}
	docker tag ${ONOS_IMG}@${ONOS_SHA} ${ONOS_IMG}
	docker pull ${MAVEN_IMG}@${MAVEN_SHA}
	docker tag ${MAVEN_IMG}@${MAVEN_SHA} ${MAVEN_IMG}

deps: _docker_pull_all get-onos-tools

onos-ui:
	open ${onos_url}/ui

get-onos-tools:
	curl -sS --fail https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.2.2/onos-admin-2.2.2.tar.gz \
  > tools.tar.gz
	tar xf tools.tar.gz
	mv onos-admin-2.2.2 onos-tools
	rm tools.tar.gz

local-build:
	cd .. && make local-app-build

build:
	cd .. && make app-build

start:
	$(info *** Starting ONOS and Mininet (topo-gtp.py)... )
	@mkdir -p tmp/onos
	@docker-compose up -d

clean:
	rm -rf target/
	rm -rf tmp/

p4rt-shell:
	docker exec -it $(shell docker-compose ps -q p4rtsh)  \
		/p4runtime-sh/docker_entry_point.sh \
		-v --grpc-addr onos:51001 \
		--device-id 0 --election-id 0,1

p4rtsh-program:
	docker exec -it $(shell docker-compose ps -q p4rtsh) /scripts/do_program.sh program

p4rtsh-clear:
	docker exec -it $(shell docker-compose ps -q p4rtsh) /scripts/do_program.sh clear

p4rtsh-bash:
	docker exec -it $(shell docker-compose ps -q p4rtsh) /bin/bash

iface-clear:
	docker exec -it $(shell docker-compose ps -q p4rtsh) /scripts/do_selective_clear.sh filter

pdr-clear:
	docker exec -it $(shell docker-compose ps -q p4rtsh) /scripts/do_selective_clear.sh pdr

far-clear:
	docker exec -it $(shell docker-compose ps -q p4rtsh) /scripts/do_selective_clear.sh far

stop:
	$(info *** Stopping ONOS and Mininet...)
	@NGSDN_TOPO_PY=foo docker-compose down

reset: stop
	-$(NGSDN_TUTORIAL_SUDO) rm -rf ./tmp


mn-cli:
	$(info *** Attaching to Mininet CLI...)
	$(info *** To detach press Ctrl-D (Mininet will keep running))
	-@docker attach --detach-keys "ctrl-d" $(shell docker-compose ps -q mininet) || echo "*** Detached from Mininet CLI"

mn-log:
	docker logs -f mininet
	
onos-logs:
	docker-compose logs -f onos

onos-config:
	util/onos-cmd route-add 17.0.0.0/24 140.0.100.1
	make netcfg
	make app-load

app-load:
	./onos-tools/onos-app localhost install! app/target/up4-app-1.0.0.oar

app-reload:
	./onos-tools/onos-app localhost reinstall! app/target/up4-app-1.0.0.oar

check-slow: ONOS_BOOT_TIME := 60
check-slow: CONFIG_WAIT_TIME := 30
check-slow: PKT_WAIT_TIME := 20
check-slow: _check

check: _check


_check:
	make start
	@echo "*** Giving ONOS time to warm up..."
	sleep ${ONOS_BOOT_TIME}
	make onos-config
	sleep ${CONFIG_WAIT_TIME}
	make p4rtsh-program
	sleep 1
	@echo "*** Sending downlink..."
	make PKT_WAIT_TIME=${PKT_WAIT_TIME} send-downlink
	@echo "*** Sending uplink..."
	make PKT_WAIT_TIME=${PKT_WAIT_TIME} send-uplink
	make stop

send-downlink:
	@# util/mn-cmd requires a TTY because it uses docker -it option
	@# hence we use screen for putting it in the background
	screen -d -m util/mn-cmd pdn /util/traffic.py send-udp
	util/mn-cmd enodeb /util/traffic.py recv-gtp -t ${PKT_WAIT_TIME}
	@# kill sending process
	util/mn-cmd pdn pkill "traffic.py"

send-uplink:
	@# util/mn-cmd requires a TTY because it uses docker -it option
	@# hence we use screen for putting it in the background
	screen -d -m util/mn-cmd enodeb /util/traffic.py send-gtp
	util/mn-cmd pdn /util/traffic.py recv-udp -t ${PKT_WAIT_TIME}
	@# kill sending process
	util/mn-cmd enodeb pkill "traffic.py"

netcfg:
	$(info *** Pushing ./util/netcfg.json to ONOS...)
	${onos_curl} -X POST -H 'Content-Type:application/json' \
		${onos_url}/v1/network/configuration -d@./util/netcfg.json
	@echo

onos-cli:
	echo "Password: rocks"
	ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" -o LogLevel=ERROR -p 8101 onos@localhost


test-onos-netcfg:
	${onos_curl} -X POST -H 'Content-Type:application/json' \
    	${onos_url}/v1/network/configuration -d@./util/ptf-netcfg.json

test-onos-config:
	#util/onos-cmd app deactivate lldpprovider # the packet injects ruin tests
	#util/onos-cmd app activate segmentrouting
	#util/onos-cmd app activate pipelines.fabric
	#util/onos-cmd app activate netcfghostprovider
	#util/onos-cmd app activate org.onosproject.protocols.grpc
	#sleep 1
	util/onos-cmd route-add 17.0.0.0/24 140.0.100.1
	sleep 1
	make test-onos-netcfg
	sleep 3
	onos-tools/onos-app localhost install! ./target/up4-app-0.1.oar


test-onos-start:
	docker run --name test-onos-img -d -it --privileged --rm \
		-e ONOS_APPS=gui2,drivers.bmv2,segmentrouting,pipelines.fabric,hostprovider,netcfghostprovider,org.onosproject.protocols.grpc,fwd \
		-p 8101:8101 \
		-p 8181:8181 \
		-p 51001:51001 \
		onosproject/onos:2.2-latest


test-sw-start:
	docker run --name test-bmv2-img -d --privileged --rm \
	  -v "/Users/macdavid/Code/up4/ptf":/ptf -w /ptf \
	  -v "/Users/macdavid/Code/up4/p4src/build":/p4c-out \
	  -v "/Users/macdavid/Code/up4/app/util":/util \
	  --network "container:test-onos-img" \
	  ccasconeonf/up4-ptf:latest \
	  ./lib/start_bmv2.sh > /dev/null

test-onos-logs:
	docker logs -f test-onos-img

test-onos-stop:
	docker stop -t0 test-onos-img > /dev/null

test-sw-stop:
	docker stop -t0 test-bmv2-img > /dev/null

test-run-ptf:
	docker exec test-bmv2-img ./lib/runner.py \
    --bmv2-json /p4c-out/bmv2.json \
    --p4info /p4c-out/p4info.bin \
    --grpc-addr localhost:51001 \
    --device-id 1 \
    --ptf-dir ./tests \
    --cpu-port 255 \
    --port-map /ptf/lib/port_map.json ${TEST}

