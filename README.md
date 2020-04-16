# UP4

This project provides a reference P4 implementation of the mobile core user plane. The initial focus is 4G, but eventually, the same P4 program will evolve to support 5G.

## Requirements

To build and test the P4 program you will need the following software to be
installed on your machine:

* Docker
* make

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
