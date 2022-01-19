#!/usr/bin/python
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

from mininet.cli import CLI
from mininet.net import Mininet
from mininet.topo import Topo
from stratum import StratumBmv2Switch
from mn_lib import DualHomedDbufHost, DualHomedIpv4Host, IPv4Host

CPU_PORT = 255


class SinglePairLeaf(Topo):
    """Single pair-leaf topology with three IPv4 hosts emulating an enodeb
       (base station), a gateway to a Packet Data Metwork (PDN), and a DBUF
       dual-homed host.
    """

    def __init__(self, *args, **kwargs):
        Topo.__init__(self, *args, **kwargs)

        # Pair-leaf
        # gRPC port 50001
        leaf1 = self.addSwitch('leaf1', cls=StratumBmv2Switch,
                               cpuport=CPU_PORT)  # , loglevel="trace")
        # gRPC port 50002
        leaf2 = self.addSwitch('leaf2', cls=StratumBmv2Switch,
                               cpuport=CPU_PORT)  # , loglevel="trace")

        # dbuf IPv4 host attached to both leaf (dual-homed)
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
        dbuf1 = self.addHost('dbuf1', cls=DualHomedDbufHost, mac='00:00:00:00:db:0f',
                             ip='140.0.99.1/24', drainIp="140.0.99.254", drainMac="00:aa:00:00:00:01")
        self.addLink(dbuf1, leaf1)  # port 1
        self.addLink(dbuf1, leaf2)  # port 1

        # pdn IPv4 host attached to leaf 2
        pdn = self.addHost('pdn', cls=DualHomedIpv4Host, mac='00:00:00:00:00:20',
                           ip='140.0.200.1/24', gw='140.0.200.254')
        self.addLink(pdn, leaf1)  # port 2
        self.addLink(pdn, leaf2)  # port 2

        # enodeb IPv4 host attached to leaf 1
        enodeb1 = self.addHost('enodeb1', cls=IPv4Host, mac='00:00:00:00:00:10',
                               ip='140.0.100.1/24', gw='140.0.100.254')
        self.addLink(enodeb1, leaf1)  # port 3

        # enodeb IPv4 host attached to leaf 2
        enodeb2 = self.addHost('enodeb2', cls=IPv4Host, mac='00:00:00:00:00:11',
                               ip='140.0.101.1/24', gw='140.0.101.254')
        self.addLink(enodeb2, leaf2)  # port 3

        # Pair link
        self.addLink(leaf1, leaf2)  # port 4 of leaf1 and leaf2


def main():
    net = Mininet(topo=SinglePairLeaf(), controller=None)
    net.start()
    CLI(net)
    net.stop()


if __name__ == "__main__":
    main()
