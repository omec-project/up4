# up4-app
ONOS Application for managing the hardware implementation of the abstract UP4 pipeline  
## Testing Instructions
Start mininet and ONOS:

    $ make start  
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
Verify uplink packets transmit:
    
    terminal1$ util/mn-cmd enodeb /util/traffic.py send-gtp
    
    terminal2$ util/mn-cmd pdn /util/traffic.py recv
    
Verify downlink packets transmit:
    
    terminal1$ util/mn-cmd pdn /util/traffic.py send-udp
    
    terminal2$ util/mn-cmd enodeb /util/traffic.py recv
