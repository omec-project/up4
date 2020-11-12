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
import signal
import struct

from threading import Lock, Thread
from ipaddress import IPv4Network, IPv4Address


# Global non-constants
sock: socket.socket = None
our_addr: str = ""
peer_addr: str = ""
our_seid: int = 1
peer_seid: int = 1
sequence_number: int = 0
thread_lock = Lock()
session_terminated = False
# End global non-constants

MSG_TYPES = {}
for num, name in pfcp.PFCPmessageType.items():
    MSG_TYPES[name] = num

HEARTBEAT_PERIOD = 5  # in seconds

UDP_PORT_PFCP = 8805
TEID = 255

IFACE_ACCESS = 0
IFACE_CORE = 1

UE_IPV4 = '17.0.0.1'

S1U_IPV4 = '140.0.100.254'
ENODEB_IPV4 = '140.0.100.1'

PDR_PRECEDENCE=2

def get_addresses_from_prefix(prefix : IPv4Network, count : int):
    # Currently this doesn't allow the address with host bits all 0,
    #  so the first host address is (prefix_addr & mask) + 1
    if count >= 2**(prefix.max_prefixlen - prefix.prefixlen):
        raise Exception("trying to generate more addresses than a prefix contains!")
    base_addr = ip2int(prefix.network_address) + 1
    offset = 0
    while offset < count:
        yield IPv4Address(base_addr + offset)
        offset += 1


def ip2int(addr : IPv4Address):
    return struct.unpack("!I", addr.packed)[0]


def int2ip(addr : int):
    return IPv4Address(addr)


def get_sequence_num(reset=False):
    thread_lock.acquire()
    global sequence_number
    if reset:
        sequence_number = 0
    sequence_number += 1
    thread_lock.release()
    return sequence_number


def open_socket(our_addr):
    sock = socket.socket(socket.AF_INET,  # Internet
                         socket.SOCK_DGRAM)  # UDP
    sock.bind((our_addr, UDP_PORT_PFCP))
    print("Socket opened ")
    return sock


def craft_fseid(seid, address):
    fseid = pfcp.IE_FSEID()
    fseid.v4 = 1
    fseid.seid = seid
    fseid.ipv4 = address
    return fseid


def craft_pdr(id, far_id, qer_id, urr_id, src_iface, update=False,
              ue_addr=None, from_tunnel=False, tunnel_dst=None, teid=None):
    ###
    pdr = pfcp.IE_CreatePDR() if not update else pfcp.IE_UpdatePDR()
    pdr_id = pfcp.IE_PDR_Id()
    pdr_id.id = id
    pdr.IE_list.append(pdr_id)
    priority = pfcp.IE_Precedence()
    priority.precedence = PDR_PRECEDENCE
    pdr.IE_list.append(priority)

    # Packet Detection Information
    pdi = pfcp.IE_PDI()

    # Source interface
    source_interface = pfcp.IE_SourceInterface()
    source_interface.interface = src_iface
    pdi.IE_list.append(source_interface)

    if from_tunnel:
        if tunnel_dst is None or teid is None:
            print("ERROR: tunnel dst and teid should be provided for tunnel PDR")
            return None
        # Add the F-TEID to the PDI
        fteid = pfcp.IE_FTEID()
        fteid.V4 = 1
        fteid.TEID = teid
        fteid.ipv4 = tunnel_dst
        pdi.IE_list.append(fteid)
        # Add outer header removal instruction to PDR
        outer_header_removal = pfcp.IE_OuterHeaderRemoval()
        outer_header_removal.header = 0
        pdr.IE_list.append(outer_header_removal)
    else:
        if ue_addr is None:
            print("UE address required for downlink PDRs!")
            return None
        ## Add UE IPv4 address to the PDI
        _ue_addr = pfcp.IE_UE_IP_Address()
        _ue_addr.V1 = 1
        _ue_addr.ipv4 = ue_addr
        pdi.IE_list.append(_ue_addr)
        # If its not from a tunnel, then its from the internet
        net_instance = pfcp.IE_NetworkInstance()
        net_instance.instance = "internetinternetinternetinterne"
        pdi.IE_list.append(net_instance)

    # Adda  fully wildcard SDF filter
    sdf = pfcp.IE_SDF_Filter()
    sdf.FD = 1
    sdf.flow_description = "0.0.0.0/0 0.0.0.0/0 0 : 65535 0 : 65535 0x0/0x0"
    pdi.IE_list.append(sdf)

    pdr.IE_list.append(pdi)

    # Add all rule IDs
    _far_id = pfcp.IE_FAR_Id()
    _far_id.id = far_id
    _qer_id = pfcp.IE_QER_Id()
    _qer_id.id = qer_id
    _urr_id = pfcp.IE_URR_Id()
    _urr_id.id = urr_id
    pdr.IE_list.append(_far_id)
    pdr.IE_list.append(_qer_id)
    pdr.IE_list.append(_urr_id)

    return pdr


def craft_far(id, update=False, forward_flag=False, drop_flag=False, buffer_flag=False,
              dst_iface=IFACE_ACCESS, tunnel=False, tunnel_dst=None, teid=None):
    far = pfcp.IE_CreateFAR() if not update else pfcp.IE_UpdateFAR()
    far_id = pfcp.IE_FAR_Id()
    far_id.id = id
    far.IE_list.append(far_id)

    # Apply Action
    apply_action = pfcp.IE_ApplyAction()
    apply_action.FORW = int(forward_flag)
    apply_action.DROP = int(drop_flag)
    apply_action.BUFF = int(buffer_flag)
    far.IE_list.append(apply_action)

    # Forwarding Parameters
    forward_param = pfcp.IE_ForwardingParameters() if not update else pfcp.IE_UpdateForwardingParameters()
    _dst_iface = pfcp.IE_DestinationInterface()
    _dst_iface.interface = dst_iface
    forward_param.IE_list.append(_dst_iface)

    if tunnel:
        if tunnel_dst is None or teid is None:
            print("ERROR: tunnel dst and teid should be provided for tunnel FAR")
        outer_header = pfcp.IE_OuterHeaderCreation()
        outer_header.GTPUUDPIPV4 = 1
        outer_header.ipv4 = tunnel_dst
        outer_header.TEID = teid
        forward_param.IE_list.append(outer_header)

    far.IE_list.append(forward_param)
    return far


def craft_qer(id, max_bitrate_up=12345678, max_bitrate_down=12345678,
              guaranteed_bitrate_up=12345678, guaranteed_bitrate_down=12345678, update=False):
    qer = pfcp.IE_CreateQER() if not update else pfcp.IE_UpdateQER()
    # QER ID
    qer_id = pfcp.IE_QER_Id()
    qer_id.id = id
    qer.IE_list.append(qer_id)
    # Gate Status
    gate1 = pfcp.IE_GateStatus()
    qer.IE_list.append(gate1)
    # Maximum Bitrate
    max_bitrate = pfcp.IE_MBR()
    max_bitrate.ul = max_bitrate_up
    max_bitrate.dl = max_bitrate_down
    qer.IE_list.append(max_bitrate)
    # Guaranteed Bitrate
    guaranteed_bitrate = pfcp.IE_GBR()
    guaranteed_bitrate.ul = guaranteed_bitrate_up
    guaranteed_bitrate.dl = guaranteed_bitrate_down
    qer.IE_list.append(guaranteed_bitrate)
    return qer


def craft_urr(id, quota, threshold, update=False):
    urr = pfcp.IE_CreateURR() if not update else pfcp.IE_UpdateURR()
    # URR ID
    urr_id = pfcp.IE_URR_Id()
    urr_id.id = id
    urr.IE_list.append(urr_id)
    # Measurement Method
    measure_method = pfcp.IE_MeasurementMethod()
    measure_method.VOLUM=1
    urr.IE_list.append(measure_method)
    # Report trigger
    report_trigger = pfcp.IE_ReportingTriggers()
    report_trigger.volume_threshold = 1
    report_trigger.volume_quota = 1
    urr.IE_list.append(report_trigger)
    # Volume quota
    volume_quota = pfcp.IE_VolumeQuota()
    volume_quota.TOVOL = 1
    volume_quota.total = quota
    urr.IE_list.append(volume_quota)
    # Volume threshold
    volume_threshold = pfcp.IE_VolumeThreshold()
    volume_threshold.TOVOL = 1
    volume_threshold.total = threshold
    urr.IE_list.append(volume_threshold)

    return urr


def craft_pfcp_association_setup_packet():
    # create PFCP packet
    pfcp_header = pfcp.PFCP()
    # create setup request packet
    setup_request = pfcp.PFCPAssociationSetupRequest()
    setup_request.version = 1
    # Let's add IEs into the message
    ie1 = pfcp.IE_NodeId()
    ie1.ipv4 = our_addr
    setup_request.IE_list.append(ie1)
    ie2 = pfcp.IE_RecoveryTimeStamp()
    setup_request.IE_list.append(ie2)
    return IP(src=our_addr, dst=peer_addr) / UDP() / pfcp_header / setup_request


def add_rules_to_request(args, request, update=False, add_pdrs=False, add_fars=False, add_urrs=False, add_qers=False):
    rule_count = args.ue_count * 2
    ue_addr_gen = get_addresses_from_prefix(args.ue_pool, args.ue_count)
    teid_gen = iter(range(args.teid_base, args.teid_base + rule_count))
    far_id_gen = iter(range(args.far_base, args.far_base + rule_count))
    pdr_id_gen = iter(range(args.pdr_base, args.pdr_base + rule_count))
    urr_id_gen = iter(range(args.urr_base, args.urr_base + rule_count))
    qer_id_gen = iter(range(args.urr_base, args.urr_base + rule_count))

    for ue_index in range(args.ue_count):
        ue_addr = next(ue_addr_gen)
        teid1 = next(teid_gen)
        teid2 = next(teid_gen)

        pdr_id1 = next(pdr_id_gen)
        pdr_id2 = next(pdr_id_gen)

        far_id1 = next(far_id_gen)
        far_id2 = next(far_id_gen)

        urr_id1 = next(urr_id_gen)
        urr_id2 = next(urr_id_gen)

        qer_id1 = next(qer_id_gen)
        qer_id2 = next(qer_id_gen)

        if add_pdrs:
            pdr1 = craft_pdr(id=pdr_id1, far_id=far_id1, qer_id=qer_id1, urr_id=urr_id1,
                             src_iface=IFACE_ACCESS, from_tunnel=True, teid=teid1, tunnel_dst=args.s1u_addr,
                             update=update)
            request.IE_list.append(pdr1)
            pdr2 = craft_pdr(id=pdr_id2, far_id=far_id2, qer_id=qer_id2, urr_id=urr_id2,
                             src_iface=IFACE_CORE, from_tunnel=False, ue_addr=ue_addr,
                             update=update)
            request.IE_list.append(pdr2)

        if add_fars:
            far1 = craft_far(id=far_id1, update=update, forward_flag=True, tunnel=False, dst_iface=IFACE_CORE)
            request.IE_list.append(far1)
            far2 = craft_far(id=far_id2, update=update, forward_flag=True, dst_iface=IFACE_ACCESS,
                             tunnel=True, tunnel_dst=args.enb_addr, teid=teid2)
            request.IE_list.append(far2)

        if add_qers:
            qer1 = craft_qer(id=qer_id1, update=update)
            request.IE_list.append(qer1)
            qer2 = craft_qer(id=qer_id2, update=update)
            request.IE_list.append(qer2)

        if add_urrs:
            urr1 = craft_urr(id=urr_id1, quota=100000, threshold=4000, update=update)
            request.IE_list.append(urr1)
            urr2 = craft_urr(id=urr_id2, quota=100000, threshold=50000, update=update)
            request.IE_list.append(urr2)


def craft_pfcp_session_est_packet(args):
    pfcp_header = pfcp.PFCP()
    pfcp_header.version = 1
    pfcp_header.S = 1
    pfcp_header.message_type = MSG_TYPES["session_establishment_request"]
    pfcp_header.seid = 0
    pfcp_header.seq = get_sequence_num(reset=True)

    establishment_request = pfcp.PFCPSessionEstablishmentRequest()
    # add IEs into message
    nodeid = pfcp.IE_NodeId()
    nodeid.ipv4 = our_addr
    establishment_request.IE_list.append(nodeid)

    fseid = craft_fseid(our_seid, our_addr)
    establishment_request.IE_list.append(fseid)

    pdn_type = pfcp.IE_PDNType()
    establishment_request.IE_list.append(pdn_type)

    add_rules_to_request(args=args, request=establishment_request, update=False,
                         add_pdrs=True, add_fars=True, add_urrs=True, add_qers=True)

    return IP(src=our_addr, dst=peer_addr) / UDP() / pfcp_header / establishment_request


def craft_pfcp_session_modify_packet(args):
    # fill pfcp header
    pfcp_header = pfcp.PFCP()
    pfcp_header.version = 1
    pfcp_header.S = 1
    pfcp_header.message_type = MSG_TYPES["session_modification_request"]
    pfcp_header.seid = peer_seid
    pfcp_header.seq = get_sequence_num()

    modification_request = pfcp.PFCPSessionModificationRequest()
    fseid = craft_fseid(our_seid, our_addr)
    modification_request.IE_list.append(fseid)

    add_rules_to_request(args, modification_request, update=True, add_fars=True)

    return IP(src=our_addr, dst=peer_addr) / UDP() / pfcp_header / modification_request


def craft_pfcp_session_delete_packet():
    pfcp_header = pfcp.PFCP()
    pfcp_header.version = 1
    pfcp_header.S = 1
    pfcp_header.message_type = MSG_TYPES["session_deletion_request"]
    pfcp_header.seid = peer_seid
    pfcp_header.seq = get_sequence_num()

    delete_pkt = IP(src=our_addr, dst=peer_addr) / UDP() / pfcp_header
    return delete_pkt


def send_recv_pfcp(pkt: scapy.Packet, expected_response_type: int, quiet=True):
    global peer_addr, peer_seid

    if not quiet:
        pkt.show()
    verbosity = 0 if quiet else 1
    scapy.send(pkt, verbose=verbosity)
    data, addr = sock.recvfrom(1024)  # buffer size is 1024 bytes
    if not quiet:
        print("Received message: %s" % data)
    response = pfcp.PFCP()
    response.dissect(data)
    if not quiet:
        response.show()
    if response.message_type == expected_response_type:
        for ie in response.payload.IE_list:
            if not quiet:
                if ie.ie_type == 60:
                    print("decoded node type : ", ie)
                elif ie.ie_type == 19:
                    print("decoded cause : ", ie.cause)
                elif ie.ie_type == 57:
                    print("FSEID recieved ")
                    print("FSEID seid {} and ip address {} ", ie.seid, ie.ipv4)
                elif ie.ie_type == 96:
                    print("recovery timestamp received")
                elif ie.ie_type == 116:
                    print("decoded ip resource information received")
            if ie.ie_type == 57:
                peer_seid = int(ie.seid)
                print("Peer addr: %s, Peer SEID: %d" % (peer_addr, peer_seid))
    else:
        print("ERROR: Expected response of type %s but received %s"
              % (pfcp.PFCPmessageType[expected_response_type], pfcp.PFCPmessageType[response.message_type]))


def setup_pfcp_association(args):
    pkt = craft_pfcp_association_setup_packet()
    send_recv_pfcp(pkt, MSG_TYPES["association_setup_response"])


def establish_pfcp_session(args):
    print("Crafting packet")
    pkt = craft_pfcp_session_est_packet(args)
    print("Done crafting packet")
    send_recv_pfcp(pkt, MSG_TYPES["session_establishment_response"])


def modify_pfcp_session(args):
    pkt = craft_pfcp_session_modify_packet(args)
    send_recv_pfcp(pkt, MSG_TYPES["session_modification_response"])


def delete_pfcp_session(args):
    pkt = craft_pfcp_session_delete_packet()
    send_recv_pfcp(pkt, MSG_TYPES["session_deletion_response"])


def send_pfcp_heartbeats():
    while True:
        time.sleep(HEARTBEAT_PERIOD)
        if session_terminated:
            return
        pfcp_header = pfcp.PFCP()
        pfcp_header.version = 1
        pfcp_header.S = 0  # SEID flag false
        pfcp_header.seq = get_sequence_num()
        pfcp_header.message_type = MSG_TYPES["heartbeat_request"]

        heartbeat = pfcp.PFCPHeartbeatRequest()
        heartbeat.version = 1
        heartbeat.IE_list.append(pfcp.IE_RecoveryTimeStamp())

        pkt = IP(src=our_addr, dst=peer_addr) / UDP() / pfcp_header / heartbeat
        send_recv_pfcp(pkt, MSG_TYPES["heartbeat_response"], quiet=True)
        print("Heartbeat.")


class ArgumentParser(argparse.ArgumentParser):

    def error(self, message):
        raise Exception("Bad parser arguments.")


def get_option_parser():
    parser = ArgumentParser(formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument("option", type=int, help="The PFCP message type")
    parser.add_argument("--buffer", action='store_true',
                        help="If this argument is present, downlink fars will have" +
                             " the buffering flag set to true")
    parser.add_argument("--ue-count", type=int, default=1,
                        help="The number of UE flows for which table entries should be created.")
    parser.add_argument("--ue-pool", type=IPv4Network,
                        default=IPv4Network("17.0.0.0/24"),
                        help="The IPv4 prefix from which UE addresses will be drawn.")
    parser.add_argument("--s1u-addr", type=IPv4Address,
                        default=IPv4Address("140.0.100.254"),
                        help="The IPv4 address of the UPF's S1U interface")
    parser.add_argument("--enb-addr", type=IPv4Address,
                        default=IPv4Address("140.0.100.1"),
                        help="The IPv4 address of the eNodeB")
    parser.add_argument("--teid-base", type=int, default=255,
                        help="The first TEID to use for the first UE. " +
                             "Further TEIDs will be generated by incrementing.")
    parser.add_argument("--pdr-base", type=int, default=1,
                        help="The first PDR ID to use for the first UE. " +
                             "Further PDR IDs will be generated by incrementing.")
    parser.add_argument("--far-base", type=int, default=1,
                        help="The first FAR ID to use for the first UE. " +
                             "Further FAR IDs will be generated by incrementing.")
    parser.add_argument("--urr-base", type=int, default=1,
                        help="The first URR ID to use for the first UE. " +
                             "Further counter indices will be generated by incrementing.")
    parser.add_argument("--qer-base", type=int, default=1,
                        help="The first QER ID to use for the first UE. " +
                             "Further counter indices will be generated by incrementing.")
    return parser


def handle_user_input():
    global session_terminated

    parser = get_option_parser()

    while True:
        print("1 - Initiate PFCP Setup Association ")
        print("2 - Initiate PFCP Session Establishment")
        print("3 - Initiate PFCP Session Modification ")
        print("4 - Release PFCP Session ")
        print("5 - Exit test ")
        try:
            print("ONE")
            args = parser.parse_args(input("Enter your option : "))
            print("TWO")
        except Exception as e:
            print("THREE")
            print(e)
            parser.print_help()
            continue
        try:
            print("address:", peer_addr)
            if args.option == 1:
                print("Selected option 1 - Setup Association")
                setup_pfcp_association(args)
                print("Done setup")
            elif args.option == 2:
                print("Selected option 2 - Create PFCP Session")
                establish_pfcp_session(args)
                print("Done creation")
            elif args.option == 3:
                print("Selected option 3 - Modify PFCP Session")
                modify_pfcp_session(args)
                print("Done modification")
            elif args.option == 4:
                print("Selected option 4. Delete PFCP Session ")
                delete_pfcp_session(args)
                print("Done deletion")
            elif args.option == 5:
                session_terminated = True
                print("Selected option 5. Exiting program")
                return
        except Exception as e:
            session_terminated = True
            raise e


def main():
    global our_addr, peer_addr, sock

    our_addr = ifcfg.interfaces()['eth0']['inet']
    sock = open_socket(our_addr)

    parser = argparse.ArgumentParser()
    parser.add_argument("upfaddr", help="UPF address")
    args = parser.parse_args()
    peer_addr = socket.gethostbyname(args.upfaddr)

    thread1 = Thread(target=handle_user_input)
    thread2 = Thread(target=send_pfcp_heartbeats)

    thread1.start()
    thread2.start()
    thread1.join()
    thread2.join()


if __name__ == "__main__":
    main()
