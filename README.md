<!--
SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
SPDX-License-Identifier: Apache-2.0
-->

# UP4

[![codecov](https://codecov.io/gh/omec-project/up4/branch/master/graph/badge.svg?token=ZJ1RZ6CFXK)](https://codecov.io/gh/omec-project/up4)
[![Build Status](https://jenkins.opencord.org/buildStatus/icon?job=up4-postmerge-pfcp&subject=up4-postmerge-pfcp)](https://jenkins.opencord.org/job/up4-postmerge-pfcp/)
[![Build Status](https://jenkins.opencord.org/buildStatus/icon?job=up4-postmerge-p4rt&subject=up4-postmerge-p4rt)](https://jenkins.opencord.org/job/up4-postmerge-p4rt/)
[![Build Status](https://jenkins.opencord.org/buildStatus/icon?job=up4-devel-nightly-pfcp&subject=up4-devel-nightly-pfcp)](https://jenkins.opencord.org/job/up4-devel-nightly-pfcp/)
[![Build Status](https://jenkins.opencord.org/buildStatus/icon?job=up4-devel-nightly-p4rt&subject=up4-devel-nightly-p4rt)](https://jenkins.opencord.org/job/up4-devel-nightly-p4rt/)

This repository is part of the SD-Fabric project. It provides an ONOS app that
abstracts a network of one or more fabric switches as a virtual "One-Big-UPF",
which can be integrated with a 4G/5G mobile core control plane.

The UP4 app is essentially a P4Runtime server that translates read and write
requests into multiple ONOS API calls for the underlying physical devices. The
One-Big-UPF abstraction is defined using a P4 program describing a "virtual UPF
pipeline". Such P4 program doesn't run on switches, but it's used as the schema
to define the content of the P4Runtime messages that can be exchanged with the
UP4 app.

To learn about the architecture, capabilitites, and instructions, including
integration with standard 3GPP interfaces such as PFCP, please refer to the
official [SD-Fabric documentation][sdfab-docs].

To learn about the origins of the UP4 project and the rationale behind it,
check the paper:

*R. MacDavid et al. [A P4-based 5G User Plane Function][up4-sosr21], SOSR 2021*

## Requirements

To build and test UP4 you will need the following software to be installed on
your machine:

* Docker
* make

Docker is used to run the necessary without worrying about additional
dependencies. Before starting, make sure to fetch all the required Docker
images:

    make deps

## Content

### P4 Implementation

The directory `p4src` contains the P4 program defining the virtual UPF pipeline.

To build the P4 program:

    make build

To generate the pipeline graphs (in PDF format):

    make graph

### ONOS App

The directory `app` contains the Java code for the ONOS app implementation.

To build the app:

    make app-build

The `app` directory has further instructions for loading and testing.

### Packet-based Unit Tests

The directory `ptf` contains unit tests for the virtual UPF P4 program. Tests
use PTF, a Python-based framework for data plane testing, and `stratum_bmv2`,
the reference P4 software switch ([BMv2 simple_switch][bmv2]) built with
[Stratum][stratum] support to provide a P4Runtime and gNMI server interface.

To run all test cases:

    make check

`ptf/tests` contains the actual test case implementation, organized in
groups, e.g., `routing.py` for all test cases pertaining the routing
functionality, `packetio.py` for control packet I/O, etc.

To run all tests in a group:

    make check TEST=<GROUP>

To run a specific test case:

    make check TEST=<GROUP>.<TEST NAME>

For example:

    make check TEST=packetio.PacketOutTest

`ptf/lib` contains the test runner as well as libraries useful to simplify
test case implementation (e.g., `helper.py` provides a P4Info helper with
methods convenient to construct P4Runtime table entries)

### Integration Tests

The directory `scenarios` contains integration test scenarios and scripts to run
a Mininet-based emulated network of BMv2 switches controlled by ONOS and UP4.

Check the included [README](scenarios/docs/README.md) for more information.

[sdfab-docs]: https://docs.sd-fabric.org/master/advanced/upf.html
[up4-sosr21]: https://www.cs.princeton.edu/~jrex/papers/up4-sosr21.pdf
[bmv2]: https://github.com/p4lang/behavioral-model
[stratum]: https://github.com/stratum/stratum
