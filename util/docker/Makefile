include Makefile.vars
# SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

build: build-ptf
push: push-ptf

build-ptf:
	cd ptf && docker build --build-arg MN_STRATUM_SHA=$(MN_STRATUM_SHA) \
		-t ${PTF_IMG} .

push-ptf:
	# Remember to update Makefile.vars with the new image sha
	docker push ${PTF_IMG}

build-stc:
	cd stc && docker build -t ${STC_IMG} .