# Integration Test Scenarios

This directory contains test scenarios for the ONOS System Test Coordinator (STC) framework.

From <https://wiki.onosproject.org/x/oYe9>
> STC is a developer oriented framework for testing and debugging running ONOS clusters. It allows
> modular definition and composition of test scenarios, as well as reuse of test modules. Developers
> can use scenarios in a semi-automated or fully automated manner. STC tracks dependencies between
> scenarios and allows parallel execution of steps that don't depend on each other to improve
> performance and to allow modeling concurrent activities like running a step on each node in a
> cluster. Larger scenarios can be built up from smaller scenarios allowing reuse of tests.

We use STC to test the UP4 ONOS app in a Mininet-based emulated environment including:
* 2x2 fabric of `stratum_bmv2` switches
* Emulated eNodeB and Packet Data Network (PDN) hosts (regular Mininet hosts)
* DBUF service (running in a Mininet host)
* 1 ONOS instance
* PFCP agent (WIP)

Scenarios are defined using XML files contained in this directory.

To learn more about the STC framework:
* Developing your own STC test: https://wiki.onosproject.org/x/u4q9
* Reference: https://wiki.onosproject.org/x/ZIPM
* Advanced topics: https://wiki.onosproject.org/x/uYq9

# Requirements

To run test scenarios you need to install the following software:

- Java Runtime Environment 1.8 or higher (tested with
  [Zulu OpenJDK 11](https://www.azul.com/downloads/zulu-community/?architecture=x86-64-bit&package=jdk), but any JDK or JRE should do)
- Docker v19 or higher
- docker-compose
- make

## Quick steps

Download dependencies (do only once):

    make deps

Run the smoke scenario:
    
    make smoke.xml**

This scenario is used to verify UP4 app builds. It included a non-exhaustive set of tests that aim
at ensuring that the most important functions work. Some functions tested in this scenario
are:
* UP4 app install and activation
* UP4 northbound APIs (via P4Runtime calls)
* Basic GTP termination for both uplink and downlink traffic
* Basic downlink buffering capabilities

Logs for each step executed in the scenario can be found in `tmp/stc`.

During the scenario execution you can access the STC web UI at <http://localhost:9999>. The UI shows
a graph of all the test steps and their dependencies.

## Scenarios

To execute a given scenario:

    make scenario.xml

**setup.xml**
* Start Docker containers for Mininet and ONOS
* Install local build of UP4 app
* Verify that all components have started correctly

**net-setup.xml**
* Push netcfg.json to ONOS
* Verify that all switches, links, and host are discovered successfully

**forwarding.xml**
* Use UP4 northbound APIs to set up GTP termination and forwarding
* Check forwarding by sending and receving traffic using the eNodeB and PDN Mininet hosts

**buffering.xml**
* Same as forwarding.xml but checks the case where downlink buffering is enabled

**teardown.xml**
* Stops Docker containers

**smoke.xml**
* Combines the above scenarios in one test

## Reusing ONOS STC commands with Docker

The ONOS repository comes with many useful commands to test a running ONOS instance. Example of such
commands are:

* `onos-check-apps`: to verify apps have been activated
* `onos-check-logs`: to verify that the log is free of errors
* `onos-check-flow`: the verify that no flows are in pending state
* `onos-check-summary`: to verify that devices, links, and hosts have been discovered
* and many more

To be able to reuse such commands, we clone the ONOS repository in `./onos` and add the
following directories to `PATH` when running `stc` (see `$(SCENARIOS):` target in `Makefile`):
* ./onos/tools/test/bin
* ./onos/tools/test/scenarios/bin

However, `onos-check-*` commands were developed to be used with "cells", an environment for running
multiple ONOS instances in VMs. Instead, here we use STC to coordinate execution of steps in the
local machine using Docker. For this reason, we override some of the `onos-check-*` commands with a
modified version that works with the Docker-based environment.

The modified `onos-check-*` commands, along with other new commands, can be found in `./bin`.

The main difference between the cell environment and the Docker-based one is that with cells, we
assume that ONOS is running in a VM that can be operated via SSH. The modified commands perform
equivalent actions without using SSH, but instead executing command directly inside the
corresponding containers. 