from scapy import all as scapy
from scapy.contrib import pfcp
import socket
import time
import argparse
import ifcfg

parser = argparse.ArgumentParser()
parser.add_argument("upfaddr", help="UPF addresses")
args = parser.parse_args()
print(args.upfaddr)
upfaddr = args.upfaddr

localaddr = ifcfg.interfaces()['eth0']['inet']


sock = None
peer_address = "0.0.0.0"
peer_seid = 0
PDR_ID = '1'
SESSION_ID = '1'
TEID = 255
TUNNEL_DPORT = '2152'
FAR_UPLINK = '1'
FAR_DOWNLINK = '2'

DIR_UPLINK = '1'
DIR_DOWNLINK = '2'

IFACE_ACCESS = '1'
IFACE_CORE = '2'

UE_IPV4 = '17.0.0.1'

S1U_IPV4 = '140.0.100.254'
ENODEB_IPV4 = '140.0.100.1'

TUNNEL_TYPE_GPDU = '3'

def open_socket():
  global sock
  UDP_IP = localaddr 
  UDP_PORT = 8805
  sock = socket.socket(socket.AF_INET, # Internet
                     socket.SOCK_DGRAM) # UDP
  sock.bind((UDP_IP, UDP_PORT))
  print("Socket opened ");

def craft_pfcp_association_setup_packet():
  #create PFCP packet 
  pfcp_header = pfcp.PFCP()
  
  #create setup request packet 
  setupreq = pfcp.PFCPAssociationSetupRequest()
  setupreq.version = 1
  
  #Let's add IEs into the message 
  ie1 = pfcp.IE_NodeId()
  ie1.ipv4 = localaddr
  setupreq.IE_list.append(ie1)
  ie2 = pfcp.IE_RecoveryTimeStamp()
  setupreq.IE_list.append(ie2)
  udp = scapy.UDP()
  ip = scapy.IP()
  ip.src = localaddr
  ip.dst = upfaddr 
  final = ip/udp/pfcp_header/setupreq
  return final

def send_receive_pfcp_association_message(setup_pkt):
  scapy.send(setup_pkt)
  data, addr = sock.recvfrom(1024) # buffer size is 1024 bytes
  print("received message: %s" % data)
  decoded_p1 = pfcp.PFCP()
  decoded_p1.dissect(data)
  if(decoded_p1.message_type == 6):
    #this is setup response 
    for ie in decoded_p1.payload.IE_list:
      if(ie.ie_type == 60):
        decoded_node_type = ie
        print("decoded node type : ")
      elif (ie.ie_type == 19):
        print("decoded cause : ",ie.cause)
      elif (ie.ie_type == 96):
        print("recovery timestamp received")
      elif (ie.ie_type == 116):
        print("decoded ip resource information received")
  else:
     print("Received packet {}",decoded_p1.message_type)

def setup_test():
  print("Setting up PFCP association ")
  setup = craft_pfcp_association_setup_packet()
  send_receive_pfcp_association_message(setup)

def craft_pfcp_session_est_packet():
  #fill pfcp header 
  pfcp_header = pfcp.PFCP()
  pfcp_header.version=1
  pfcp_header.S=1
  pfcp_header.message_type=50
  pfcp_header.seid=0
  pfcp_header.seq=1
  
  est = pfcp.PFCPSessionEstablishmentRequest()
  #add IEs into message
  nodeid = pfcp.IE_NodeId()
  nodeid.ipv4= localaddr 
  est.IE_list.append(nodeid)
  
  fseid = pfcp.IE_FSEID()
  fseid.v4=1
  fseid.seid=1
  fseid.ipv4="1.1.1.1"
  est.IE_list.append(fseid)
  
  pdn_type = pfcp.IE_PDNType()
  est.IE_list.append(pdn_type)
  
  pdr1 = pfcp.IE_CreatePDR()
  pdr1_id = pfcp.IE_PDR_Id()
  pdr1_id.id = 1
  pdr1.IE_list.append(pdr1_id)
  priority1 = pfcp.IE_Precedence()
  priority1.precedence = 2
  pdr1.IE_list.append(priority1)
  pdi1 = pfcp.IE_PDI()
  
  #soure interface
  si1 = pfcp.IE_SourceInterface() 
  si1.interface=0
  pdi1.IE_list.append(si1)
  #F-TEID
  fteid1 = pfcp.IE_FTEID()
  fteid1.V4=1
  fteid1.TEID=255
  fteid1.ipv4=S1U_IPV4
  pdi1.IE_list.append(fteid1)
  #SDF filter 
  sdf1 = pfcp.IE_SDF_Filter()
  sdf1.FD=1
  sdf1.flow_description="0.0.0.0/0 0.0.0.0/0 0 : 65535 0 : 65535 0x0/0x0"
  pdi1.IE_list.append(sdf1)
  
  pdr1.IE_list.append(pdi1)
  outerHdrRml1 = pfcp.IE_OuterHeaderRemoval()
  outerHdrRml1.header=0
  pdr1.IE_list.append(outerHdrRml1)
  farid1 = pfcp.IE_FAR_Id()
  farid1.id = 1
  pdr1.IE_list.append(farid1)
  qerid1 = pfcp.IE_QER_Id()
  qerid1.id = 1
  pdr1.IE_list.append(qerid1)
  urrid1 = pfcp.IE_URR_Id()
  urrid1.id = 1
  pdr1.IE_list.append(urrid1)
  
  
  est.IE_list.append(pdr1)
  
  ###
  pdr2 = pfcp.IE_CreatePDR()
  pdr2_id = pfcp.IE_PDR_Id()
  pdr2_id.id = 2
  pdr2.IE_list.append(pdr2_id)
  priority2 = pfcp.IE_Precedence()
  priority2.precedence = 2
  pdr2.IE_list.append(priority2)
  
  pdi2 = pfcp.IE_PDI()
  
  #soure interface
  si2 = pfcp.IE_SourceInterface() 
  si2.interface=1
  pdi2.IE_list.append(si2)
  #network instance 
  ni = pfcp.IE_NetworkInstance()
  ni.instance = "internetinternetinternetinterne"
  pdi2.IE_list.append(ni);
  
  #ue IP address 
  ueaddr = pfcp.IE_UE_IP_Address() 
  ueaddr.V4 = 1
  ueaddr.ipv4 = UE_IPV4
  pdi2.IE_list.append(ueaddr);
  
  #SDF filter 
  sdf2 = pfcp.IE_SDF_Filter()
  sdf2.FD=1
  sdf2.flow_description="0.0.0.0/0 0.0.0.0/0 0 : 65535 0 : 65535 0x0/0x0"
  pdi2.IE_list.append(sdf2)
  
  pdr2.IE_list.append(pdi2)
  
  farid2 = pfcp.IE_FAR_Id()
  farid2.id = 2
  pdr2.IE_list.append(farid2)
  qerid2 = pfcp.IE_QER_Id()
  qerid2.id = 2
  pdr2.IE_list.append(qerid2)
  
  urrid2 = pfcp.IE_URR_Id()
  urrid2.id = 2
  pdr2.IE_list.append(urrid2)
  
  
  est.IE_list.append(pdr2)
  
  far1 = pfcp.IE_CreateFAR()
  #fill far 1
  #FAR id
  farid1 = pfcp.IE_FAR_Id()
  farid1.id = 1
  far1.IE_list.append(farid1)
  #Apply Action
  appAction1 = pfcp.IE_ApplyAction() 
  appAction1.FORW = 1
  far1.IE_list.append(appAction1)
  #Forwarding Parameters
  forwardParam1 = pfcp.IE_ForwardingParameters()
  destintf1 = pfcp.IE_DestinationInterface()
  destintf1.interface=1 #core
  forwardParam1.IE_list.append(destintf1)
  far1.IE_list.append(forwardParam1)
  
  est.IE_list.append(far1)
  
  far2 = pfcp.IE_CreateFAR()
  
  #fill far 2
  #FAR id
  farid2 = pfcp.IE_FAR_Id()
  farid2.id = 2
  far2.IE_list.append(farid2)
  #Apply Action
  appAction2 = pfcp.IE_ApplyAction() 
  appAction2.FORW = 1
  far2.IE_list.append(appAction2)
  #Forwarding Parameters
  forwardParam2 = pfcp.IE_ForwardingParameters()
  destintf2 = pfcp.IE_DestinationInterface()
  destintf2.interface=0 #access
  forwardParam2.IE_list.append(destintf2)
  far2.IE_list.append(forwardParam2)
  
  est.IE_list.append(far2)
  
  qer1 = pfcp.IE_CreateQER()
  #QER ID
  qerid1 = pfcp.IE_QER_Id()
  qerid1.id = 1
  qer1.IE_list.append(qerid1)
  #Gate STtus 
  gate1 = pfcp.IE_GateStatus()
  qer1.IE_list.append(gate1)
  #MBR
  mbr1 = pfcp.IE_MBR()
  mbr1.dl=12345678
  mbr1.ul=12345678
  qer1.IE_list.append(mbr1)
  #GBR
  gbr1 = pfcp.IE_GBR()
  gbr1.dl=12345678
  gbr1.ul=12345678
  qer1.IE_list.append(gbr1)
  est.IE_list.append(qer1)
  
  qer2 = pfcp.IE_CreateQER()
  #QER ID
  qerid2 = pfcp.IE_QER_Id()
  qerid2.id = 2
  qer2.IE_list.append(qerid2)
  #Gate STtus 
  gate2 = pfcp.IE_GateStatus()
  qer2.IE_list.append(gate2)
  #MBR
  mbr2 = pfcp.IE_MBR()
  mbr2.dl=12345678
  mbr2.ul=12345678
  qer2.IE_list.append(mbr2)
  #GBR
  gbr2 = pfcp.IE_GBR()
  gbr1.dl=12345678
  gbr1.ul=12345678
  qer2.IE_list.append(gbr2)
  
  est.IE_list.append(qer2)
  
  urr1 = pfcp.IE_CreateURR()
  #URR ID
  urrid1 = pfcp.IE_URR_Id()
  urrid1.id = 1
  urr1.IE_list.append(urrid1)
  #Measurement Method 
  measure1 = pfcp.IE_MeasurementMethod()
  measure1.VOLUM = 1
  urr1.IE_list.append(measure1)
  #report trigger
  rep1 = pfcp.IE_ReportingTriggers()
  rep1.volume_threshold = 1
  rep1.volume_quota = 1
  urr1.IE_list.append(rep1)
  #VolumeQuota
  voqu1 = pfcp.IE_VolumeQuota()
  voqu1.TOVOL = 1
  voqu1.total = 100000
  urr1.IE_list.append(voqu1)
  voth1 = pfcp.IE_VolumeThreshold()
  voth1.TOVOL = 1
  voth1.total = 40000
  urr1.IE_list.append(voth1)
  est.IE_list.append(urr1)
  
  urr2 = pfcp.IE_CreateURR()
  #URR ID
  urrid2 = pfcp.IE_URR_Id()
  urrid2.id = 2
  urr2.IE_list.append(urrid2)
  #Measurement Method 
  measure2 = pfcp.IE_MeasurementMethod()
  measure2.VOLUM = 1
  urr2.IE_list.append(measure2)
  #report trigger
  rep2 = pfcp.IE_ReportingTriggers()
  rep2.volume_threshold = 1
  rep2.volume_quota = 1
  urr2.IE_list.append(rep2)
  #VolumeQuota
  voqu2 = pfcp.IE_VolumeQuota()
  voqu2.TOVOL = 1
  voqu2.total = 100000
  urr2.IE_list.append(voqu2)
  voth2 = pfcp.IE_VolumeThreshold()
  voth2.TOVOL = 1
  voth2.total = 50000
  urr2.IE_list.append(voth2)
  est.IE_list.append(urr2)
  
  udp = scapy.UDP()
  ip = scapy.IP()
  ip.src = localaddr
  ip.dst = upfaddr 
  final = ip/udp/pfcp_header/est
  return final

def send_receive_pfcp_create_session_message(create_session_pkt):
  create_session_pkt.show()
  scapy.send(create_session_pkt)
  data, addr = sock.recvfrom(1024) # buffer size is 1024 bytes
  print("received message: %s" % data)
  decoded_p1 = pfcp.PFCP()
  decoded_p1.dissect(data)
  decoded_p1.show()
  if(decoded_p1.message_type == 51):
    #this is setup response 
    for ie in decoded_p1.payload.IE_list:
      if(ie.ie_type == 60):
        decoded_node_type = ie
        print("decoded node type : ")
      elif (ie.ie_type == 19):
        print("decoded cause : ",ie.cause)
      elif (ie.ie_type == 57):
        print("FSEID recieved ")
        print("FSEID seid {} and ip address {} ",ie.seid, ie.ipv4)
        global peer_address, peer_seid
        peer_address = ie.ipv4
        peer_seid = ie.seid
  else:
    print("Received packet {}",decoded_p1.message_type)

def create_pfcp_session_test():
  print("Creating PFCP session ")
  create = craft_pfcp_session_est_packet()
  send_receive_pfcp_create_session_message(create)

def craft_pfcp_session_modify_packet():
  global peer_address, peer_seid
  udp = scapy.UDP()
  ip = scapy.IP()
  
  ip.src = localaddr
  ip.dst = peer_address 
  
  #fill pfcp header 
  pfcp_header = pfcp.PFCP()
  pfcp_header.version=1
  pfcp_header.S=1
  pfcp_header.message_type=52
  pfcp_header.seid=peer_seid
  pfcp_header.seq=2
  
  mod = pfcp.PFCPSessionModificationRequest()
  fseid = pfcp.IE_FSEID()
  fseid.v4=1
  fseid.seid=1
  fseid.ipv4="1.1.1.1"
  mod.IE_list.append(fseid)
  
  far1 = pfcp.IE_UpdateFAR()
  #FAR id
  farid1 = pfcp.IE_FAR_Id()
  farid1.id = 2
  far1.IE_list.append(farid1)
  #Apply Action
  appAction1 = pfcp.IE_ApplyAction() 
  appAction1.FORW = 1
  far1.IE_list.append(appAction1)
  
  updforwarding = pfcp.IE_UpdateForwardingParameters()
  
  destintf1 = pfcp.IE_DestinationInterface()
  destintf1.interface=0 #access
  updforwarding.IE_list.append(destintf1)
  
  outerHeader = pfcp.IE_OuterHeaderCreation()
  outerHeader.GTPUUDPIPV4=1
  outerHeader.ipv4=ENODEB_IPV4
  outerHeader.TEID=TEID
  updforwarding.IE_list.append(outerHeader)
  
  far1.IE_list.append(updforwarding)
  
  mod.IE_list.append(far1)
  udp = scapy.UDP()
  ip = scapy.IP()
  ip.src = localaddr 
  ip.dst = upfaddr 
 
  modify_pkt = ip/udp/pfcp_header/mod
  return modify_pkt

def send_receive_pfcp_modify_session_message(modify_pkt):
  modify_pkt.show()
  scapy.send(modify_pkt)
  data, addr = sock.recvfrom(1024) # buffer size is 1024 bytes
  print("received message: %s" % data)
  decoded_p1 = pfcp.PFCP()
  decoded_p1.dissect(data)
  if(decoded_p1.message_type == 53):
    #this is setup response 
    for ie in decoded_p1.payload.IE_list:
      if(ie.ie_type == 60):
        decoded_node_type = ie
        print("decoded node type : ")
      elif (ie.ie_type == 19):
        print("decoded cause : ",ie.cause)
      elif (ie.ie_type == 96):
        print("recovery timestamp received")
      elif (ie.ie_type == 116):
        print("decoded ip resource information received")
  else:
    print("Received packet {}",decoded_p1.message_type)

def modify_pfcp_session_test():
  print("modify PFCP session ")
  modify = craft_pfcp_session_modify_packet()
  send_receive_pfcp_modify_session_message(modify)

def craft_pfcp_session_delete_packet():
  global peer_address, peer_seid
  udp = scapy.UDP()
  ip = scapy.IP()
  
  ip.src = localaddr
  ip.dst = peer_address 
  ip.dst = upfaddr 
  
  #fill pfcp header 
  pfcp_header = pfcp.PFCP()
  pfcp_header.version=1
  pfcp_header.S=1
  pfcp_header.message_type=54
  pfcp_header.seid=peer_seid
  pfcp_header.seq= 3
  
  delete_pkt = ip/udp/pfcp_header
  return delete_pkt

def send_receive_pfcp_delete_session_message(delete_pkt):
  scapy.send(delete_pkt)
  delete_pkt.show()
  data, addr = sock.recvfrom(1024) # buffer size is 1024 bytes
  print("received message: %s" % data)
  decoded_p1 = pfcp.PFCP()
  decoded_p1.dissect(data)
  if(decoded_p1.message_type == 53):
    #this is setup response 
    for ie in decoded_p1.payload.IE_list:
      if(ie.ie_type == 60):
        decoded_node_type = ie
        print("decoded node type : ")
      elif (ie.ie_type == 19):
        print("decoded cause : ",ie.cause)
      elif (ie.ie_type == 96):
        print("recovery timestamp received")
      elif (ie.ie_type == 116):
        print("decoded ip resource information received")
  else:
    print("Received packet {}",decoded_p1.message_type)


def delete_pfcp_session_test():
  print("delete PFCP session ")
  delete = craft_pfcp_session_delete_packet()
  send_receive_pfcp_delete_session_message(delete)


def main():
  open_socket()
  while True:
    print("1 - Initiate PFCP Setup Association ")
    print("2 - Initiate PFCP Session Establishment")
    print("3 - Initiate PFCP Session Modification ")
    print("4 - Release PFCP Session ")
    print("5 - Exit test ")
    try:
      option = int(input("Enter your option : "))
    except Exception as e:
      print(e)

    if option == 1:
        print("Selected option 1 - Setup Association")
        setup_test()
    elif option == 2:
        print("Selected option 2 - Create PFCP Session")
        create_pfcp_session_test()
    elif option == 3:
        print("Selected option 3 - Modify PFCP Session")
        modify_pfcp_session_test()
    elif option == 4:
        print("Selected option 4. Delete PFCP Session ")
        delete_pfcp_session_test()
    elif option == 5:
        print("Selected option 5. Exiting program")
        return

if __name__ == "__main__":
    main()
