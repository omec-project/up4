#!/usr/bin/python3
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

from scapy import all as scapy
from scapy.layers.inet import IP, UDP
from scapy.contrib import pfcp
import socket
import time
import argparse
import ifcfg


# Global non-constants
sock: socket.socket = None
our_addr: str = ""
peer_addr: str = ""
our_seid: int = 1
peer_seid: int = 1
sequence_number: int = 0
# End global non-constants


UDP_PORT_PFCP = 8805
TEID = 255

IFACE_ACCESS = 0
IFACE_CORE = 1

UE_IPV4 = '17.0.0.1'

S1U_IPV4 = '140.0.100.254'
ENODEB_IPV4 = '140.0.100.1'

PDR_PRECEDENCE=2


def get_sequence_num(reset=False):
    global sequence_number
    if reset:
        sequence_number = 0
    sequence_number += 1
    return sequence_number


def open_socket(our_addr):
    sock = socket.socket(socket.AF_INET,  # Internet
                         socket.SOCK_DGRAM)  # UDP
    sock.bind((our_addr, UDP_PORT_PFCP))
    print("Socket opened ")
    return sock


def craft_pdr(id, far_id, qer_id, urr_id, src_iface, ue_addr=None, from_tunnel=False, tunnel_dst=S1U_IPV4, teid=TEID):
    ###
    pdr = pfcp.IE_CreatePDR()
    pdr_id = pfcp.IE_PDR_Id(id=id)
    pdr.IE_list.append(pdr_id)
    priority = pfcp.IE_Precedence(precedence=PDR_PRECEDENCE)
    pdr.IE_list.append(priority)

    # Packet Detection Information
    pdi = pfcp.IE_PDI()

    # Source interface
    source_interface = pfcp.IE_SourceInterface(interface=src_iface)
    pdi.IE_list.append(source_interface)

    if from_tunnel:
        # Add the F-TEID to the PDI
        f_teid = pfcp.IE_FTEID(V4=1, TEID=teid, ipv4=tunnel_dst)
        pdi.IE_list.append(f_teid)
        # Add outer header removal instruction to PDR
        outer_header_removal = pfcp.IE_OuterHeaderRemoval(header=0)
        pdr.IE_list.append(outer_header_removal)
    else:
        if ue_addr is None:
            print("UE address required for downlink PDRs!")
            return None
        ## Add UE IPv4 address to the PDI
        ue_addr = pfcp.IE_UE_IP_Address(V1=1, ipv4=ue_addr)
        pdi.IE_list.append(ue_addr)
        # If its not from a tunnel, then its from the internet
        net_instance = pfcp.IE_NetworkInstance(instance="internetinternetinternetinterne")
        pdi.IE_list.append(net_instance)

    # Adda  fully wildcard SDF filter
    sdf = pfcp.IE_SDF_Filter()
    sdf.FD = 1
    sdf.flow_description = "0.0.0.0/0 0.0.0.0/0 0 : 65535 0 : 65535 0x0/0x0"
    pdi.IE_list.append(sdf)

    pdr.IE_list.append(pdi)

    # Add all rule IDs
    pdr.IE_list.append(pfcp.IE_FAR_Id(id=far_id))
    pdr.IE_list.append(pfcp.IE_QER_Id(id=qer_id))
    pdr.IE_list.append(pfcp.IE_URR_Id(id=urr_id))

    return pdr


def craft_far(id, update=False, forward_flag=False, drop_flag=False, buffer_flag=False,
              dst_iface=IFACE_ACCESS, tunnel=False, tunnel_dst=ENODEB_IPV4, teid=TEID):
    far = pfcp.IE_CreateFAR() if not update else pfcp.IE_UpdateFAR()
    far_id = pfcp.IE_FAR_Id(id=id)
    far.IE_List.append(far_id)

    # Apply Action
    apply_action = pfcp.IE_ApplyAction()
    apply_action.FORW = int(forward_flag)
    apply_action.DROP = int(drop_flag)
    apply_action.BUFF = int(buffer_flag)
    far.IE_list.append(apply_action)

    # Forwarding Parameters
    forward_param = pfcp.IE_ForwardingParameters() if not update else pfcp.IE_UpdateForwardingParameters()
    dst_iface = pfcp.IE_DestinationInterface(interface=dst_iface)
    forward_param.IE_list.append(dst_iface)

    if tunnel:
        outer_header = pfcp.IE_OuterHeaderCreation(GTPUUDPIPV4=1, ipv4=tunnel_dst, TEID=teid)
        forward_param.IE_list.append(outer_header)

    far.IE_list.append(forward_param)
    return far


def craft_qer(id, max_bitrate_up=12345678, max_bitrate_down=12345678,
              guaranteed_bitrate_up=12345678, guaranteed_bitrate_down=12345678):
    qer = pfcp.IE_CreateQER()
    # QER ID
    qer_id = pfcp.IE_QER_Id(id=1)
    qer.IE_list.append(qer_id)
    # Gate Status
    gate1 = pfcp.IE_GateStatus()
    qer.IE_list.append(gate1)
    # Maximum Bitrate
    max_bitrate = pfcp.IE_MBR(dl=max_bitrate_down, ul=max_bitrate_up)
    qer.IE_list.append(max_bitrate)
    # Guaranteed Bitrate
    guaranteed_bitrate = pfcp.IE_GBR(dl=guaranteed_bitrate_down, ul=guaranteed_bitrate_up)
    qer.IE_list.append(guaranteed_bitrate)
    return qer


def craft_urr(id, quota, threshold):
    urr = pfcp.IE_CreateURR()
    # URR ID
    urr_id = pfcp.IE_URR_Id(id=id)
    urr.IE_list.append(urr_id)
    # Measurement Method
    measure_method = pfcp.IE_MeasurementMethod(VOLUM=1)
    urr.IE_list.append(measure_method)
    # Report trigger
    report_trigger = pfcp.IE_ReportingTriggers(volume_threshold=1, volume_quota=1)
    urr.IE_list.append(report_trigger)
    # Volume quota
    volume_quota = pfcp.IE_VolumeQuota(TOVOL=1, total=quota)
    urr.IE_list.append(volume_quota)
    # Volume threshold
    volume_threshold = pfcp.IE_VolumeThreshold(TOVOL=1, total=threshold)
    urr.IE_list.append(volume_threshold)

    return urr


def craft_pfcp_association_setup_packet(our_addr, their_addr):
    # create PFCP packet
    pfcp_header = pfcp.PFCP()

    # create setup request packet
    setup_request = pfcp.PFCPAssociationSetupRequest(version=1)

    # Let's add IEs into the message
    ie1 = pfcp.IE_NodeId()
    ie1.ipv4 = our_addr
    setup_request.IE_list.append(ie1)
    ie2 = pfcp.IE_RecoveryTimeStamp()
    setup_request.IE_list.append(ie2)
    return IP(src=our_addr, dst=their_addr) / UDP() / pfcp_header / setup_request


def send_receive_pfcp_association_message(setup_pkt):
    scapy.send(setup_pkt)
    data, addr = sock.recvfrom(1024)  # buffer size is 1024 bytes
    print("received message: %s" % data)
    decoded_p1 = pfcp.PFCP()
    decoded_p1.dissect(data)
    if (decoded_p1.message_type == 6):
        # this is setup response
        for ie in decoded_p1.payload.IE_list:
            if (ie.ie_type == 60):
                decoded_node_type = ie
                print("decoded node type : ")
            elif (ie.ie_type == 19):
                print("decoded cause : ", ie.cause)
            elif (ie.ie_type == 96):
                print("recovery timestamp received")
            elif (ie.ie_type == 116):
                print("decoded ip resource information received")
    else:
        print("Received packet {}", decoded_p1.message_type)


def setup_test():
    print("Setting up PFCP association ")
    setup = craft_pfcp_association_setup_packet()
    send_receive_pfcp_association_message(setup)


def send_recv_pfcp(pkt: scapy.Packet):
    global peer_addr, peer_seid, sock

    pkt.show()
    scapy.send(pkt)
    data, addr = sock.recvfrom(1024)  # buffer size is 1024 bytes
    print("received message: %s" % data)
    decoded_p1 = pfcp.PFCP()
    decoded_p1.dissect(data)
    decoded_p1.show()
    if (decoded_p1.message_type == 51 or decoded_p1.message_type == 53):
        for ie in decoded_p1.payload.IE_list:
            if (ie.ie_type == 60):
                print("decoded node type : ", ie)
            elif (ie.ie_type == 19):
                print("decoded cause : ", ie.cause)
            elif (ie.ie_type == 57):
                print("FSEID recieved ")
                print("FSEID seid {} and ip address {} ", ie.seid, ie.ipv4)
                peer_addr = ie.ipv4
                peer_seid = ie.seid
            elif (ie.ie_type == 96):
                print("recovery timestamp received")
            elif (ie.ie_type == 116):
                print("decoded ip resource information received")



def craft_pfcp_session_est_packet():
    # fill pfcp header
    global our_addr
    pfcp_header = pfcp.PFCP()
    pfcp_header.version = 1
    pfcp_header.S = 1
    pfcp_header.message_type = 50
    pfcp_header.seid = 0
    pfcp_header.seq = get_sequence_num(reset=True)

    establishment_request = pfcp.PFCPSessionEstablishmentRequest()
    # add IEs into message
    nodeid = pfcp.IE_NodeId()
    nodeid.ipv4 = our_addr
    establishment_request.IE_list.append(nodeid)

    fseid = pfcp.IE_FSEID(v4=1, seid=our_seid, ipv4=our_addr)
    establishment_request.IE_list.append(fseid)

    pdn_type = pfcp.IE_PDNType()
    establishment_request.IE_list.append(pdn_type)

    pdr1 = craft_pdr(id=1, far_id=1, qer_id=1, urr_id=1, src_iface=IFACE_ACCESS,
                     from_tunnel=True, teid=255, tunnel_dst=S1U_IPV4)
    establishment_request.IE_list.append(pdr1)

    pdr2 = craft_pdr(id=2, far_id=2, qer_id=2, urr_id=2, src_iface=IFACE_CORE,
                     from_tunnel=False, ue_addr=UE_IPV4)
    establishment_request.IE_list.append(pdr2)

    far1 = craft_far(id=1, update=False, forward_flag=True, tunnel=False, dst_iface=IFACE_CORE)
    establishment_request.IE_list.append(far1)

    far2 = craft_far(id=2, update=False, forward_flag=True, tunnel=False, dst_iface=IFACE_ACCESS)
    establishment_request.IE_list.append(far2)

    qer1 = craft_qer(id=1)
    establishment_request.IE_list.append(qer1)

    qer2 = craft_qer(id=2)
    establishment_request.IE_list.append(qer2)

    urr1 = craft_urr(id=1, quota=100000, threshold=4000)
    establishment_request.IE_list.append(urr1)

    urr2 = craft_urr(id=2, quota=100000, threshold=50000)
    establishment_request.IE_list.append(urr2)

    return IP(src=our_addr, dst=peer_addr) / UDP() / pfcp_header / establishment_request



def establish_pfcp_session():
    print("Establishing PFCP session ")
    pkt = craft_pfcp_session_est_packet()
    send_recv_pfcp(pkt)


def craft_pfcp_session_modify_packet():
    global our_addr, peer_addr, peer_seid

    # fill pfcp header
    pfcp_header = pfcp.PFCP()
    pfcp_header.version = 1
    pfcp_header.S = 1
    pfcp_header.message_type = 52
    pfcp_header.seid = peer_seid
    pfcp_header.seq = get_sequence_num()

    modification_request = pfcp.PFCPSessionModificationRequest()
    fseid = pfcp.IE_FSEID(v4=1, seid=our_seid, ipv4=our_addr)
    modification_request.IE_list.append(fseid)

    far2 = craft_far(id=2, update=True, forward_flag=True, dst_iface=IFACE_ACCESS,
                     tunnel=True, tunnel_dst=ENODEB_IPV4, teid=TEID)
    modification_request.IE_list.append(far2)

    return IP(src=our_addr, dst=peer_addr) / UDP() / pfcp_header / modification_request


def modify_pfcp_session():
    print("modify PFCP session ")
    pkt = craft_pfcp_session_modify_packet()
    send_recv_pfcp(pkt)


def craft_pfcp_session_delete_packet():
    global our_addr, peer_addr, peer_seid
    # fill pfcp header
    pfcp_header = pfcp.PFCP()
    pfcp_header.version = 1
    pfcp_header.S = 1
    pfcp_header.message_type = 54
    pfcp_header.seid = peer_seid
    pfcp_header.seq = get_sequence_num()

    delete_pkt = IP(src=our_addr, dst=peer_addr) / UDP() / pfcp_header
    return delete_pkt


def delete_pfcp_session(sock: socket.socket):
    print("delete PFCP session ")
    pkt = craft_pfcp_session_delete_packet()
    send_recv_pfcp(sock, pkt)


def main():
    global our_addr, peer_addr, sock

    our_addr = ifcfg.interfaces()['eth0']['inet']
    sock = open_socket(our_addr)


    parser = argparse.ArgumentParser()
    parser.add_argument("upfaddr", help="UPF addresses")
    args = parser.parse_args()
    peer_addr = args.upfaddr

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
            establish_pfcp_session()
        elif option == 3:
            print("Selected option 3 - Modify PFCP Session")
            modify_pfcp_session()
        elif option == 4:
            print("Selected option 4. Delete PFCP Session ")
            delete_pfcp_session()
        elif option == 5:
            print("Selected option 5. Exiting program")
            return


if __name__ == "__main__":
    main()
