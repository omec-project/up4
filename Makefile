# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

mkfile_path := $(abspath $(lastword $(MAKEFILE_LIST)))
curr_dir := $(patsubst %/,%,$(dir $(mkfile_path)))

main_file := p4src/main.p4

include util/docker/Makefile.vars

default: build check

_docker_pull_all:
	docker pull ${P4RT_SH_IMG}@${P4RT_SH_SHA}
	docker tag ${P4RT_SH_IMG}@${P4RT_SH_SHA} ${P4RT_SH_IMG}
	docker pull ${P4C_IMG}@${P4C_SHA}
	docker tag ${P4C_IMG}@${P4C_SHA} ${P4C_IMG}
	docker pull ${MN_STRATUM_IMG}@${MN_STRATUM_SHA}
	docker tag ${MN_STRATUM_IMG}@${MN_STRATUM_SHA} ${MN_STRATUM_IMG}
	docker pull ${PTF_IMG}@${PTF_SHA}
	docker tag ${PTF_IMG}@${PTF_SHA} ${PTF_IMG}

deps: _docker_pull_all

clean:
	-rm -rf p4src/build
	-rm -rf ptf/*.log
	-rm -rf ptf/*.pcap

app-build:
	@mkdir -p app/src/main/resources
	cp p4src/build/p4info.txt app/src/main/resources/
	cd app && mvn clean install

docker-app-build:
	@mkdir -p app/src/main/resources
	cp p4src/build/p4info.txt app/src/main/resources/
	docker run -it --rm -v ${HOME}/.m2:/root/.m2 \
		-v ${PWD}:/root/up4 -w /root/up4/app maven:3.6.3-openjdk-11-slim \
		mvn clean install

build: ${main_file}
	$(info *** Building P4 program...)
	@mkdir -p p4src/build
	docker run --rm -v ${curr_dir}:/workdir -w /workdir ${P4C_IMG} \
		p4c-bm2-ss ${P4C_FLAGS} --arch v1model -o p4src/build/bmv2.json \
		--p4runtime-files p4src/build/p4info.txt,p4src/build/p4info.bin \
		--Wdisable=unsupported \
		${main_file}
	@echo "*** P4 program compiled successfully! Output files are in p4src/build"

graph: ${main_file}
	$(info *** Generating P4 program graphs...)
	@mkdir -p p4src/build/graphs
	docker run --rm -v ${curr_dir}:/workdir -w /workdir ${P4C_IMG} \
		p4c-graphs --graphs-dir p4src/build/graphs ${main_file}
	for f in p4src/build/graphs/*.dot; do \
		docker run --rm -v ${curr_dir}:/workdir -w /workdir ${P4C_IMG} \
			dot -Tpdf $${f} > $${f}.pdf; rm -f $${f}; \
	done
	@echo "*** Done! Graph files are in p4src/build/graphs"

check:
	@cd ptf && PTF_DOCKER_IMG=$(PTF_IMG) ./run_tests ${PTF_TEST_PARAMS} $(TEST)

.yapf:
	rm -rf ./yapf
	git clone --depth 1 https://github.com/google/yapf.git .yapf
	rm -rf .yapf/.git

prettify: .yapf
	PYTHONPATH=${curr_dir}/.yapf python .yapf/yapf -ir -e .yapf/ .
