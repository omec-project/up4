#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
# yapf: disable
import sys
sys.path.append('/p4runtime-sh/')

import p4runtime_sh.shell as sh
import socket
import struct
import argparse
from ipaddress import IPv4Network, IPv4Address


FALSE = '0'
TRUE='1'

TUNNEL_DPORT = '2152'

DIR_UPLINK = '1'
DIR_DOWNLINK = '2'

IFACE_ACCESS = '1'
IFACE_CORE = '2'

TUNNEL_TYPE_GPDU = '3'

def get_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ue-count", type=int, default=1)
    parser.add_argument("--ue-pool", type=IPv4Network,
            default=IPv4Network("17.0.0.0/24"))
    parser.add_argument("--s1u-addr", type=IPv4Address,
            default=IPv4Address("140.0.100.254"))
    parser.add_argument("--enb-addr", type=IPv4Address,
            default=IPv4Address("140.0.100.1"))
    parser.add_argument("--teid-base", type=int, default=255)
    parser.add_argument("--session-id",type=int, default=1)
    parser.add_argument("--pdr-base", type=int, default=1)
    parser.add_argument("--far-base", type=int, default=1)
    parser.add_argument("--ctr-base", type=int, default=1)
    parser.add_argument("--server", type=str, default="onos:51001")
    parser.add_argument("action", choices=["program", "clear", "dry"])
    return parser.parse_args()

args = get_args()



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


entries = []

def addEntry(entry, action):
    if action == "program":
        try:
            entry.insert()
            print("*** Entry added.")
        except Exception as e:
            print("Except during table insertion:", e)
            print("Entry was:", entry)
            print("%d entries were successfully added before failure" % len(entries))
            clearEntries()
            sys.exit(1)
    entries.append(entry)

def clearEntries():
    for i, entry in enumerate(entries):
        entry.delete()
        print("*** Entry %d of %d deleted." % (i+1, len(entries)))


def main():
    # Connect to gRPC server
    sh.setup(
        device_id=1,
        grpc_addr=args.server,
        election_id=(0, 1), # (high, low)
        config=sh.FwdPipeConfig('/p4c-out/p4info.txt', '/p4c-out/p4info.bin')
    )


    #========================#
    # Filter Entries
    #========================#

    ## Uplink
    entry = sh.TableEntry('PreQosPipe.source_iface_lookup')(action='PreQosPipe.set_source_iface')
    entry.match['ipv4_dst_prefix'] = str(args.s1u_addr) + '/32'
    entry.action['src_iface'] = IFACE_ACCESS
    entry.action['direction'] = DIR_UPLINK
    addEntry(entry, args.action)

    ## Downlink
    entry = sh.TableEntry('PreQosPipe.source_iface_lookup')(action='PreQosPipe.set_source_iface')
    entry.match['ipv4_dst_prefix'] = str(args.ue_pool)
    entry.action['src_iface'] = IFACE_CORE
    entry.action['direction'] = DIR_DOWNLINK
    addEntry(entry, args.action)


    # table entry parameter generators
    rule_count = args.ue_count * 2
    ue_addr_gen = get_addresses_from_prefix(args.ue_pool, args.ue_count)
    teid_gen   = iter(range(args.teid_base, args.teid_base + args.ue_count))
    far_id_gen = iter(range(args.far_base,   args.far_base + rule_count))
    pdr_id_gen = iter(range(args.pdr_base,   args.pdr_base + rule_count))
    ctr_id_gen = iter(range(args.ctr_base,   args.ctr_base + rule_count))

    for ue_index in range(args.ue_count):
        ue_addr = next(ue_addr_gen)
        teid = next(teid_gen)

        pdr_uplink = next(pdr_id_gen)
        pdr_downlink = next(pdr_id_gen)

        far_uplink = next(far_id_gen)
        far_downlink = next(far_id_gen)

        pdr_ctr_uplink = next(ctr_id_gen)
        pdr_ctr_downlink = next(ctr_id_gen)


        #========================#
        # PDR Entries
        #========================#

        ## Uplink
        entry = sh.TableEntry('PreQosPipe.pdrs')(action='PreQosPipe.set_pdr_attributes')
        # Match fields
        entry.match['src_iface'] = IFACE_ACCESS
        entry.match['ue_addr'] = str(ue_addr)
        entry.match['teid'] = str(teid)
        entry.match['tunnel_ipv4_dst'] = str(args.s1u_addr)
        # Action params
        entry.action['id']     = str(pdr_uplink)
        entry.action['fseid']  = str(args.session_id)
        entry.action['ctr_id'] = str(pdr_ctr_uplink)
        entry.action['far_id'] = str(far_uplink)
        addEntry(entry, args.action)

        ## Downlink
        entry = sh.TableEntry('PreQosPipe.pdrs')(action='PreQosPipe.set_pdr_attributes')
        # Match fields
        entry.match['src_iface'] = IFACE_CORE
        entry.match['ue_addr'] = str(ue_addr)
        # Action params
        entry.action['id'] = str(pdr_downlink)
        entry.action['fseid'] = str(args.session_id)
        entry.action['ctr_id'] = str(pdr_ctr_downlink)
        entry.action['far_id'] = str(far_downlink)
        addEntry(entry, args.action)


        #========================#
        # FAR Entries
        #========================#

        ## Uplink
        entry = sh.TableEntry('PreQosPipe.load_far_attributes')(action='PreQosPipe.load_normal_far_attributes')
        # Match fields
        entry.match['far_id'] = str(far_uplink)
        entry.match['session_id'] = str(args.session_id)
        # Action params
        entry.action['needs_dropping'] = FALSE
        entry.action['notify_cp'] = FALSE
        addEntry(entry, args.action)

        ## Downlink
        entry = sh.TableEntry('PreQosPipe.load_far_attributes')(action='PreQosPipe.load_tunnel_far_attributes')
        # Match fields
        entry.match['far_id'] = str(far_downlink)
        entry.match['session_id'] = str(args.session_id)
        # Action params
        entry.action['needs_dropping'] = FALSE
        entry.action['notify_cp'] = FALSE
        entry.action['tunnel_type'] = TUNNEL_TYPE_GPDU
        entry.action['src_addr'] = str(args.s1u_addr)
        entry.action['dst_addr'] = str(args.enb_addr)
        entry.action['teid'] = str(teid)
        entry.action['dport'] = TUNNEL_DPORT
        addEntry(entry, args.action)

    if args.action == "program":
        print("All entries added sucessfully.")

    elif args.action == "clear":
        clearEntries()

    elif args.action == "dry":
        for entry in entries:
            print(entry)

    sh.teardown()



if __name__ == "__main__":
    main()

# yapf: enable
