#!/bin/bash

# SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

mv /usr/sbin/tcpdump /usr/bin/tcpdump

if [ -z ${PARALLEL_LINKS} ]; then
	/topo/topo-gtp.py
else
	/topo/topo-gtp.py --parallel-links ${PARALLEL_LINKS}
fi