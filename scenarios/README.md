# Integration tests scenarios

Contains test scenarios of the ONOS System Test Coordinator (STC) framework.

WIP.

# Requirements

- Java Runtime Environment (tested with JDK 11)
- Docker
- docker-compose

# Introduction

STC is...

Initially designed to work with cell environment.

Re-use commands from ONOS repo to verify ONOS state, but override some to work with Docker environment.

## Quick steps

Download dependencies:

    make deps

To run a scenario XML file
    
    make my_scenario.xml
    
Logs in ./tmp/stc