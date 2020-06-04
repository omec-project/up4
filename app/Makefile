get-onos-tools:
	curl -sS --fail https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.2.2/onos-admin-2.2.2.tar.gz \
  > tools.tar.gz
	tar xf tools.tar.gz
	mv onos-admin-2.2.2 onos-tools
	rm tools.tar.gz

onos-start:
	docker run -it --rm \
	  -e ONOS_APPS=gui2,drivers.bmv2,pipelines.fabric,lldpprovider,hostprovider,fwd \
	  -p 8101:8101 -p 8181:8181 \
	  onosproject/onos:2.2.x


onos-configure:
	./onos-tools/onos-netcfg localhost netcfg.json

mn-start:
	docker run --privileged --rm -it \
	  -v /tmp/mn-stratum:/tmp \
	  -p 50001-50030:50001-50030 \
	  opennetworking/mn-stratum

onos-cli:
	echo "Password: rocks"
	ssh -o "UserKnownHostsFile=/dev/null" -o "StrictHostKeyChecking=no" -o LogLevel=ERROR -p 8101 onos@localhost

build:
	mvn package

app-load:
	./onos-tools/onos-app localhost install! target/up4-app-0.1.oar

app-reload:
	./onos-tools/onos-app localhost reinstall! target/up4-app-0.1.oar

