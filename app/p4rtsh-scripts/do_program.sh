#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

cd /p4runtime-sh/
source $VENV/bin/activate

/scripts/program_many_flows.py $@
