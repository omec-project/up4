#!/usr/bin/python
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0

import argparse

from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.topo import Topo
from stratum import StratumBmv2Switch
from mn_lib import IPv4Host, DbufHost

CPU_PORT = 255


class LeafSpine(Topo):
    """2x2 fabric topology with three IPv4 hosts emulating an enodeb (base
       station), a gateway to a Packet Data Metwork (PDN), and a DBUF host.
    """

    def __init__(self, *args, **kwargs):
        Topo.__init__(self, *args, **kwargs)
        # Extract parallelLinks option
        parallelLinks = args[0]

        # Leaves
        # gRPC port 50001
        leaf1 = self.addSwitch('leaf1', cls=StratumBmv2Switch,
                               cpuport=CPU_PORT)  # , loglevel="trace")
        # gRPC port 50002
        leaf2 = self.addSwitch('leaf2', cls=StratumBmv2Switch, cpuport=CPU_PORT)

        # Spines
        # gRPC port 50003
        spine1 = self.addSwitch('spine1', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        # gRPC port 50004
        spine2 = self.addSwitch('spine2', cls=StratumBmv2Switch, cpuport=CPU_PORT)

        # enodeb IPv4 host attached to leaf 1
        enodeb = self.addHost('enodeb', cls=IPv4Host, mac='00:00:00:00:00:10', ip='140.0.100.1/24',
                              gw='140.0.100.254')
        self.addLink(enodeb, leaf1)  # port 1

        # dbuf IPv4 host attached to leaf 1
        # DbufHost exists on the root net namespace (inNamespace=False) such
        # that onos can communicate with its grpc service port. However, by
        # running on the root namespace, the default route for docker
        # networking prevents dbuf from pinging hosts on subnets other than the
        # dbuf one (ip=...). However, dbuf can still ping the switch gateway
        # address (gw=...) which is all we need to receive and send buffered
        # packets.
        # If we don't use the switch gateway as drain address, then
        # we can specify the drainIp and drainMac, that will install a /32 route
        # and an ARP entry for those values.
        dbuf1 = self.addHost('dbuf1', cls=DbufHost, mac='00:00:00:00:db:0f', ip='140.0.99.1/24',
                             drainIp="140.0.0.2", drainMac="00:aa:00:00:00:01")
        self.addLink(dbuf1, leaf1)  # port 2

        # pdn IPv4 host attached to leaf 2
        pdn = self.addHost('pdn', cls=IPv4Host, mac='00:00:00:00:00:20', ip='140.0.200.1/24',
                           gw='140.0.200.254')
        self.addLink(pdn, leaf2)  # port 1

        # Switch Links
        for _ in range(parallelLinks):
            self.addLink(spine1, leaf1)
            self.addLink(spine1, leaf2)
            self.addLink(spine2, leaf1)
            self.addLink(spine2, leaf2)


def main(parallelLinks):
    net = Mininet(topo=LeafSpine(parallelLinks), controller=None)
    net.start()
    CLI(net)
    net.stop()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description='Mininet topology script for 2x2 fabric with stratum_bmv2 and IPv4 hosts')
    parser.add_argument('-pl', '--parallel-links', type=int, default=1,
                        help='change the number of parallel links between leaf and spine')
    args = parser.parse_args()
    setLogLevel('info')

    main(args.parallel_links)
