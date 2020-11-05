#!/bin/bash
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

# For running program_many_flows.py outside of a docker-compose environment.

docker run -ti \
    -v $PWD:/scripts/ \
    --entrypoint /scripts/do_program.sh \
    p4lang/p4runtime-sh $@
