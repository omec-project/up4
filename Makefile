# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

MKFILE_PATH              := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR              := $(patsubst %/,%,$(dir $(MKFILE_PATH)))

main_file := p4src/main.p4

include .env.stable

default: build check

_docker_pull_all:
	docker pull ${P4C_IMAGE}
	docker pull ${PTF_IMAGE}
	docker pull ${PTF_BMV2_IMAGE}

deps: _docker_pull_all

clean:
	-rm -rf p4src/build
	-rm -rf ptf/*.log
	-rm -rf ptf/*.pcap
	-rm -rf .m2
	-rm -rf app/target
	-rm -rf app/app/target
	-rm -rf app/api/target

# Required by tost build scripts - do not remove!
_prepare_app_build:
	cd app && make _build_resources

local-app-build:
	cd app && make local-build

app-build:
	cd app && make build

app-build-ci:
	cd app && make build-ci

app-check:
	cd app && make check

build: ${main_file}
	$(info *** Building P4 program...)
	@mkdir -p p4src/build
	docker run --rm -v ${CURRENT_DIR}:/workdir -w /workdir ${P4C_IMAGE} \
		p4c-bm2-ss ${P4C_FLAGS} --arch v1model -o p4src/build/bmv2.json \
		--p4runtime-files p4src/build/p4info.txt,p4src/build/p4info.bin \
		--Wdisable=unsupported \
		${main_file}
	@echo "*** P4 program compiled successfully! Output files are in p4src/build"

graph: ${main_file}
	$(info *** Generating P4 program graphs...)
	@mkdir -p p4src/build/graphs
	docker run --rm -v ${CURRENT_DIR}:/workdir -w /workdir ${P4C_IMAGE} \
		p4c-graphs --graphs-dir p4src/build/graphs ${main_file}
	for f in p4src/build/graphs/*.dot; do \
		docker run --rm -v ${CURRENT_DIR}:/workdir -w /workdir ${P4C_IMAGE} \
			dot -Tpdf $${f} > $${f}.pdf; rm -f $${f}; \
	done
	@echo "*** Done! Graph files are in p4src/build/graphs"

check:
	@cd ptf && PTF_IMAGE=$(PTF_IMAGE) PTF_BMV2_IMAGE=$(PTF_BMV2_IMAGE) ./run_tests ${PTF_TEST_PARAMS} $(TEST)

.yapf:
	rm -rf ./yapf
	git clone --depth 1 https://github.com/google/yapf.git .yapf
	rm -rf .yapf/.git

prettify: .yapf
	PYTHONPATH=${CURRENT_DIR}/.yapf python3 .yapf/yapf -ir -e .yapf/ .

reuse-lint:
	docker run --rm -v ${CURRENT_DIR}:/up4 -w /up4 omecproject/reuse-verify:latest reuse lint

reuse-addheader:
	docker run --rm -v ${CURRENT_DIR}:/up4 -w /up4 omecproject/reuse-verify:latest reuse addheader \
		--copyright "Open Networking Foundation <info@opennetworking.org>" \
		--license "LicenseRef-ONF-Member-1.0" \
		--year "2020-present" $(FILE)

build-ptf:
	cd ptf && docker build --build-arg MN_STRATUM_IMAGE=$(MN_STRATUM_IMAGE) \
		-t opennetworking/up4-ptf .

push-ptf:
	# Remember to update Makefile.vars with the new image sha
	docker push opennetworking/up4-ptf
