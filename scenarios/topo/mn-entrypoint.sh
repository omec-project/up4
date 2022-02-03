#!/bin/bash

# SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: Apache-2.0

mv /usr/sbin/tcpdump /usr/bin/tcpdump

MN_SCRIPT=${MN_SCRIPT:-/topo/topo-gtp-leafspine.py}

if [ -z "${PARALLEL_LINKS}" ]; then
	${MN_SCRIPT}
else
	${MN_SCRIPT} --parallel-links "${PARALLEL_LINKS}"
fi
