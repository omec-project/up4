<!--
SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
-->

# up4-app
ONOS Application for managing the hardware implementation of the abstract UP4 pipeline.

There are currently two ways to test the app. To test the app without using the northbound component, 
use the section titled "Setting up a test using CLI commands". This lets you access the southbound directly
via ONOS CLI commands. To test the entire app including the northbound component, use the section titled 
"Setting up a test using UP4 P4Runtime calls". After you have set up the testbed by following the instructions,
verify uplink and downlink packets transmit by following the final section.

To build:

    $ make deps build

To run integration tests (smoke test):

    $ cd ../scenarios
    $ make smoke.xml

For more information on integration tests, see [scenarios/README.md](../scenarios/README.md)

## Setting up a test using CLI commands
This is the first way to test the app, which does not include the P4Runtime server 
(aka the northbound service).

Download dependencies. This only needs to be done once:

    $ make deps

Build app:

    $ make build
    
Use STC scenarios to start Mininet and ONOS:

    $ cd ../scenarios
    $ make setup.xml net-setup.xml  

setup.xml includes steps to install the local build of the up4 app.

To re-install the app, e.g., after a new build, without re-starting ONOS:

    $ make app-reload
    
View ONOS logs
    
    $ make onos-log

Using the ONOS CLI, install table entries for uplink packets:

    onos> up4:s1u-insert 140.0.100.254  
    onos> up4:pdr-insert 1 17.0.0.1 1 255 140.0.100.254  
    onos> up4:far-insert 1 1

And for downlink:

    onos> up4:ue-pool-insert 17.0.0.0/24  
    onos> up4:pdr-insert 1 17.0.0.1 2  
    onos> up4:far-insert 1 2 255 140.0.100.254 140.0.100.1 
    
Next, go to the section on sending and receiving packets.
    
## Setting up a test using UP4 P4Runtime calls
This is the second way to test the app, which uses both the northbound and southbound.

The initial steps are the same as the previous case. Instead of using the ONOS CLI to install table
entries, you can do the same by using the p4runtime-shell:

    $ make p4rt-set-forwarding
    
If needed, those entries can also be cleared

    $ make p4rt-clear
    
    
## Sending and receiving packets
    
Verify uplink packets transmit:

    $ cd ../scenarios
    
    terminal1$ bin/mn-cmd enodeb traffic.py send-gtp
   
    terminal2$ bin/mn-cmd pdn traffic.py recv-udp
    
Verify downlink packets transmit:
    
    terminal1$ bin/mn-cmd pdn traffic.py send-udp
    
    terminal2$ bin/mn-cmd enodeb traffic.py recv-gtp
