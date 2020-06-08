onos_url := http://localhost:8181/onos
onos_curl := curl --fail -sSL --user onos:rocks --noproxy localhost


get-onos-tools:
	curl -sS --fail https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.2.2/onos-admin-2.2.2.tar.gz \
  > tools.tar.gz
	tar xf tools.tar.gz
	mv onos-admin-2.2.2 onos-tools
	rm tools.tar.gz

build:
	mvn package

start:
	$(info *** Starting ONOS and Mininet (topo-gtp.py)... )
	$(info *** Remember to activate fabric, segmentrouting, and netcfghostprovider)
	@mkdir -p tmp/onos
	@NGSDN_TOPO_PY=topo-gtp.py docker-compose up -d

stop:
	$(info *** Stopping ONOS and Mininet...)
	@NGSDN_TOPO_PY=foo docker-compose down -t0

reset: stop
	-$(NGSDN_TUTORIAL_SUDO) rm -rf ./tmp


mn-start:
	docker run --privileged --rm -it \
	  -v /tmp/mn-stratum:/tmp \
	  -p 50001-50030:50001-50030 \
	  opennetworking/mn-stratum

mn-cli:
	$(info *** Attaching to Mininet CLI...)
	$(info *** To detach press Ctrl-D (Mininet will keep running))
	-@docker attach --detach-keys "ctrl-d" $(shell docker-compose ps -q mininet) || echo "*** Detached from Mininet CLI"

mn-log:
	docker logs -f mininet
	
onos-log:
	docker-compose logs -f onos

onos-start:
	docker run -it --rm \
	  -e ONOS_APPS=gui2,drivers.bmv2,pipelines.fabric,lldpprovider,hostprovider,fwd \
	  -p 8101:8101 -p 8181:8181 \
	  onosproject/onos:2.2-latest

app-load:
	./onos-tools/onos-app localhost install! target/up4-app-0.1.oar

app-reload:
	./onos-tools/onos-app localhost reinstall! target/up4-app-0.1.oar


netcfg:
	$(info *** Pushing ./util/netcfg.json to ONOS...)
	${onos_curl} -X POST -H 'Content-Type:application/json' \
		${onos_url}/v1/network/configuration -d@./util/netcfg.json
	@echo

onos-cli:
	echo "Password: rocks"
	ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" -o LogLevel=ERROR -p 8101 onos@localhost

