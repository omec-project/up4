#!/bin/sh

/bin/pfcpiface -config /opt/bess/bessctl/conf/upf.json -n4SrcIPStr 0.0.0.0 -p4RtcServerIP onos -p4RtcServerPort 51001
