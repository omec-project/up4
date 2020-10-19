onos_url := http://localhost:8181/onos
onos_curl := curl --fail -sSL --user onos:rocks --noproxy localhost


start:
	$(info *** Starting ONOS, Mininet \(topo-gtp.py\), PFCP, and Scapy... )
	@mkdir -p tmp/onos
	@NGSDN_TOPO_PY=topo-gtp.py docker-compose up -d

stop:
	$(info *** Stopping ONOS and Mininet...)
	@NGSDN_TOPO_PY=foo docker-compose down


get-onos-tools:
	curl -sS --fail https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.2.2/onos-admin-2.2.2.tar.gz \
  > tools.tar.gz
	tar xf tools.tar.gz
	mv onos-admin-2.2.2 onos-tools
	rm tools.tar.gz


netcfg:
	$(info *** Pushing ./util/netcfg.json to ONOS...)
	${onos_curl} -X POST -H 'Content-Type:application/json' \
		${onos_url}/v1/network/configuration -d@../up4/app/util/netcfg.json
	@echo


onos-config:
	util/onos-cmd app activate segmentrouting
	util/onos-cmd app activate pipelines.fabric
	util/onos-cmd app activate netcfghostprovider
	util/onos-cmd app activate org.onosproject.protocols.grpc
	sleep 1
	util/onos-cmd route-add 17.0.0.0/24 140.0.100.1
	sleep 1
	make netcfg
	sleep 3
	onos-tools/onos-app localhost install! ../up4/app/app/target/up4-app-1.0.0-SNAPSHOT.oar


agent-start:
	docker exec $(shell docker-compose ps -q pfcp) /bin/pfcpiface -config /opt/bess/bessctl/conf/upf.json -n4SrcIPStr 0.0.0.0 -p4RtcServerIP onos -p4RtcServerPort 51001

attach-script:
	docker exec -it $(shell docker-compose ps -q scapy) python3 /util/testAttach.py pfcp

mn-cli:
	$(info *** Attaching to Mininet CLI...)
	$(info *** To detach press Ctrl-D (Mininet will keep running))
	-@docker attach --detach-keys "ctrl-d" $(shell docker-compose ps -q mininet) || echo "*** Detached from Mininet CLI"

mn-logs:
	docker logs -f mininet
	
onos-logs:
	docker-compose logs -f onos

pfcp-logs:
	docker-compose logs -f pfcp

scapy-logs:
	docker-compose logs -f scapy

onos-cli:
	echo "Password: rocks"
	ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" -o LogLevel=ERROR -p 8101 onos@localhost
