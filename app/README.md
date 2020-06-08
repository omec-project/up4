# up4-app
ONOS Application for managing the hardware implementation of the abstract UP4 pipeline

Start mininet and ONOS:

    make start  
Push the netcfg  

    make netcfg  
Run the ONOS CLI, activate three apps, and install routes 
 
    make onos-cli  
    onos> app activate fabric segmentrouting netcfghostprovider  
    onos> route-add 17.0.0.0/24 140.0.100.1  
Load our app  

    make app-load
Install table entries for the uplink:

    up4:s1u-insert device:leaf1 140.0.100.254  
    up4:pdr-insert device:leaf1 1 17.0.0.1 1 255 140.0.100.254  
    up4:far-insert device:leaf1 1 1     
Install table entries for the downlink:

    up4:ue-pool-insert device:leaf1 17.0.0.0/24  
    up4:pdr-insert device:leaf1 1 17.0.0.1 2  
    up4:far-insert device:leaf1 1 2 255 140.0.100.254 140.0.100.1  
