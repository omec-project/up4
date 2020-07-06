#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
# yapf: disable
import sys
sys.path.append('/p4runtime-sh/')

import p4runtime_sh.shell as sh


FALSE = '0'

PDR_ID_UPLINK = '1'
PDR_ID_DOWNLINK = '2'
CTR_ID_UPLINK = '1'
CTR_ID_DOWNLINK = '2'
SESSION_ID = '1'
TEID = '255'
TUNNEL_DPORT = '2152'
FAR_UPLINK = '1'
FAR_DOWNLINK = '2'

DIR_UPLINK = '1'
DIR_DOWNLINK = '2'

IFACE_ACCESS = '1'
IFACE_CORE = '2'

UE_IPV4 = '17.0.0.1'
UE_POOL = '17.0.0.0/24'

S1U_IPV4 = '140.0.100.254'
ENODEB_IPV4 = '140.0.100.1'

TUNNEL_TYPE_GPDU = '3'


entries = []


def main():
    sh.setup(
        device_id=1,
        grpc_addr='onos:51001',
        election_id=(0, 1), # (high, low)
        config=sh.FwdPipeConfig('/p4c-out/p4info.txt', '/p4c-out/p4info.bin')
    )

    if sys.argv[1] == 'filter':
        #========================#
        # Filter Entries
        #========================#

        ## Uplink
        uplinkFilter = sh.TableEntry('PreQosPipe.source_iface_lookup')(action='PreQosPipe.set_source_iface')
        uplinkFilter.match['ipv4_dst_prefix'] = S1U_IPV4 + '/32'
        uplinkFilter.action['src_iface'] = IFACE_ACCESS
        uplinkFilter.action['direction'] = DIR_UPLINK
        uplinkFilter.delete()

        ## Downlink
        downlinkFilter = sh.TableEntry('PreQosPipe.source_iface_lookup')(action='PreQosPipe.set_source_iface')
        downlinkFilter.match['ipv4_dst_prefix'] = UE_POOL
        downlinkFilter.action['src_iface'] = IFACE_CORE
        downlinkFilter.action['direction'] = DIR_DOWNLINK
        downlinkFilter.delete()


    if sys.argv[1] == 'pdr':
        #========================#
        # PDR Entries
        #========================#

        ## Uplink
        uplinkPdr = sh.TableEntry('PreQosPipe.pdrs')(action='PreQosPipe.set_pdr_attributes')
        # Match fields
        uplinkPdr.match['src_iface'] = IFACE_ACCESS
        uplinkPdr.match['ue_addr'] = UE_IPV4
        uplinkPdr.match['teid'] = TEID
        uplinkPdr.match['tunnel_ipv4_dst'] = S1U_IPV4
        # Action params
        uplinkPdr.action['id'] = PDR_ID_UPLINK
        uplinkPdr.action['fseid'] = SESSION_ID
        uplinkPdr.action['ctr_id'] = CTR_ID_UPLINK
        uplinkPdr.action['far_id'] = FAR_UPLINK
        uplinkPdr.delete()

        ## Downlink
        downlinkPdr = sh.TableEntry('PreQosPipe.pdrs')(action='PreQosPipe.set_pdr_attributes')
        # Match fields
        downlinkPdr.match['src_iface'] = IFACE_CORE
        downlinkPdr.match['ue_addr'] = UE_IPV4
        # Action params
        downlinkPdr.action['id'] = PDR_ID_DOWNLINK
        downlinkPdr.action['fseid'] = SESSION_ID
        downlinkPdr.action['ctr_id'] = CTR_ID_DOWNLINK
        downlinkPdr.action['far_id'] = FAR_DOWNLINK
        downlinkPdr.delete()


    if sys.argv[1] == "far":
        #========================#
        # FAR Entries
        #========================#

        ## Uplink
        uplinkFar = sh.TableEntry('PreQosPipe.load_far_attributes')(action='PreQosPipe.load_normal_far_attributes')
        # Match fields
        uplinkFar.match['far_id'] = FAR_UPLINK
        uplinkFar.match['session_id'] = SESSION_ID
        # Action params
        uplinkFar.action['needs_dropping'] = FALSE
        uplinkFar.action['notify_cp'] = FALSE
        uplinkFar.delete()

        ## Downlink
        downlinkFar = sh.TableEntry('PreQosPipe.load_far_attributes')(action='PreQosPipe.load_tunnel_far_attributes')
        # Match fields
        downlinkFar.match['far_id'] = FAR_DOWNLINK
        downlinkFar.match['session_id'] = SESSION_ID
        # Action params
        downlinkFar.action['needs_dropping'] = FALSE
        downlinkFar.action['notify_cp'] = FALSE
        downlinkFar.action['tunnel_type'] = TUNNEL_TYPE_GPDU
        downlinkFar.action['src_addr'] = S1U_IPV4
        downlinkFar.action['dst_addr'] = ENODEB_IPV4
        downlinkFar.action['teid'] = TEID
        downlinkFar.action['dport'] = TUNNEL_DPORT
        downlinkFar.delete()


    sh.teardown()


if __name__ == "__main__":
    main()

# yapf: enable

