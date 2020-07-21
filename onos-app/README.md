# up4-app
ONOS Application for managing the hardware implementation of the abstract UP4 pipeline.

There are currently two ways to test the app. To test the app without using the northbound component, 
use the section titled "Setting up a test using CLI commands". This lets you access the southbound directly
via ONOS CLI commands. To test the entire app including the northbound component, use the section titled 
"Setting up a test using UP4 P4Runtime calls". After you have set up the testbed by following the instructions,
verify uplink and downlink packets transmit by following the final section.

To test everything all at once:

    $ make deps build check

## Setting up a test using CLI commands
Download dependencies. This only needs to be done once:

    $ make deps
    
Start mininet and ONOS:

    $ make start  
    
View ONOS logs
    
    $ make onos-logs
    
Push the netcfg  

    $ make netcfg  
    
Run the ONOS CLI, activate four apps, and install routes 
 
    $ make onos-cli  
    onos> app activate fabric segmentrouting netcfghostprovider org.onosproject.protocols.grpc
    onos> route-add 17.0.0.0/24 140.0.100.1  
Build and load the UP4 app

    $ make build
    $ make app-load
Using the ONOS CLI, install table entries for uplink packets:

    onos> up4:s1u-insert device:leaf1 140.0.100.254  
    onos> up4:pdr-insert device:leaf1 1 17.0.0.1 1 255 140.0.100.254  
    onos> up4:far-insert device:leaf1 1 1     
And for downlink:

    onos> up4:ue-pool-insert device:leaf1 17.0.0.0/24  
    onos> up4:pdr-insert device:leaf1 1 17.0.0.1 2  
    onos> up4:far-insert device:leaf1 1 2 255 140.0.100.254 140.0.100.1 
    
## Setting up a test using UP4 P4Runtime calls
Download dependencies. This only needs to be done once:

    $ make deps
    
Start mininet and ONOS:

    $ make start  
    
Build the UP4 app

    $ make build
    
View ONOS logs
    
    $ make onos-logs
    
Wait for ONOS to warm up. Then, activate apps, install routes, load the UP4 app, and push the netcfg
with a single command

    $ make onos-config
    
Install table entries via p4runtime-shell

    $ make p4rtsh-program
    
If needed, those entries can also be cleared

    $ make p4rtsh-clear
    
    
## Sending and receiving packets
    
Verify uplink packets transmit:
    
    terminal1$ util/mn-cmd enodeb /util/traffic.py send-gtp
    
    terminal2$ util/mn-cmd pdn /util/traffic.py recv-udp
    
Verify downlink packets transmit:
    
    terminal1$ util/mn-cmd pdn /util/traffic.py send-udp
    
    terminal2$ util/mn-cmd enodeb /util/traffic.py recv-gtp
