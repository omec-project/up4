#!/bin/sh

# SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
#
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

/bin/pfcpiface -config /opt/bess/bessctl/conf/upf.json -n4SrcIPStr 0.0.0.0 -p4RtcServerIP onos -p4RtcServerPort 51001
