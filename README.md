<!--
Copyright 2020-present Open Networking Foundation
SPDX-License-Identifier: Apache-2.0
-->

# UP4

[![CircleCI](https://circleci.com/gh/omec-project/up4.svg?style=svg&circle-token=dd7ccddc58269dfdd22af69a892b6a4ee33b3474)](https://circleci.com/gh/omec-project/up4)
[![codecov](https://codecov.io/gh/omec-project/up4/branch/master/graph/badge.svg?token=ZJ1RZ6CFXK)](https://codecov.io/gh/omec-project/up4)

This project provides a reference P4 implementation of the mobile core user plane forwarding model. The initial focus is the 4G SPGW-u, but eventually, the same P4 program will evolve to support the 5G UPF.
The project also provides testing for the reference P4 implementation, and an ONOS app for abstracting supported physical devices as the reference implementation.


## Requirements

To build and test the P4 program you will need the following software to be
installed on your machine:

* Docker
* make
* maven (if building the app)

Docker is used to run the necessary without worrying about additional
dependencies. Before starting, make sure to fetch all the required Docker
images:

    make deps

## Content

### P4 Implementation

The directory `p4src` contains the P4 program defining the reference packet
forwarding pipeline of an UPF.

To build the P4 program:

    make build

To generate the pipeline graphs (in PDF format):

    make graph
    
### ONOS App

The directory `app` contains an ONOS app which abstracts physical TOST devices as the P4 program defined in `p4src`.
P4runtime clients may connect to the app and send entity read and write requests for said P4 program, and the app will translate those
requests into stratum-p4runtime calls on physical TOST switches.

To build the app:

    make app-build

The `app` directory has further instructions for loading and testing.

### Packet-based Unit Tests

The directory `ptf` contains unit tests for the P4 program. Tests use PTF, a
Python-based framework for data plane testing, and `stratum_bmv2`, the reference
P4 software switch ([BMv2 simple_switch][bmv2]) built with [Stratum][stratum]
support to provide a P4Runtime and gNMI server interface.

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


[bmv2]: https://github.com/p4lang/behavioral-model
[stratum]: https://github.com/stratum/stratum
