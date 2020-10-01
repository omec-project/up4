#!/usr/bin/python
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

import argparse

from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import Host
from mininet.topo import Topo
from stratum import StratumBmv2Switch

CPU_PORT = 255


class IPv4Host(Host):
    """Host that can be configured with an IPv4 gateway (default route).
    """

    def config(self, mac=None, ip=None, defaultRoute=None, lo='up', gw=None, **_params):
        super(IPv4Host, self).config(mac, ip, defaultRoute, lo, **_params)
        self.cmd('ip -4 addr flush dev %s' % self.defaultIntf())
        self.cmd('ip -6 addr flush dev %s' % self.defaultIntf())
        self.cmd('sysctl -w net.ipv4.ip_forward=0')
        self.cmd('ip -4 link set up %s' % self.defaultIntf())
        self.cmd('ip -4 addr add %s dev %s' % (ip, self.defaultIntf()))
        if gw:
            self.cmd('ip -4 route add default via %s' % gw)
        # Disable offload
        for attr in ["rx", "tx", "sg"]:
            cmd = "/sbin/ethtool --offload %s %s off" % (self.defaultIntf(), attr)
            self.cmd(cmd)

        def updateIP():
            return ip.split('/')[0]

        self.defaultIntf().updateIP = updateIP


class DbufHost(IPv4Host):

    def __init__(self, name, inNamespace=False, **params):
        super(DbufHost, self).__init__(name, inNamespace, **params)

    def config(self, mac=None, ip=None, defaultRoute=None, lo='up', gw=None, **_params):
        super(DbufHost, self).config(mac, ip, defaultRoute, lo, gw, **_params)
        self.cmd('/usr/local/bin/dbuf > /tmp/dbuf_%s.log 2>&1 &' % self.name)


class TutorialTopo(Topo):
    """2x2 fabric topology for GTP encap exercise with 2 IPv4 hosts emulating an
       enodeb (base station) and a gateway to a Packet Data Metwork (PDN)
    """

    def __init__(self, *args, **kwargs):
        Topo.__init__(self, *args, **kwargs)

        # Leaves
        # gRPC port 50001
        leaf1 = self.addSwitch('leaf1', cls=StratumBmv2Switch,
                               cpuport=CPU_PORT)  #, loglevel="trace")
        # gRPC port 50002
        leaf2 = self.addSwitch('leaf2', cls=StratumBmv2Switch, cpuport=CPU_PORT)

        # Spines
        # gRPC port 50003
        spine1 = self.addSwitch('spine1', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        # gRPC port 50004
        spine2 = self.addSwitch('spine2', cls=StratumBmv2Switch, cpuport=CPU_PORT)

        # Switch Links
        self.addLink(spine1, leaf1)
        self.addLink(spine1, leaf2)
        self.addLink(spine2, leaf1)
        self.addLink(spine2, leaf2)

        # enodeb IPv4 host attached to leaf 1
        enodeb = self.addHost('enodeb', cls=IPv4Host, mac='00:00:00:00:00:10', ip='140.0.100.1/24',
                              gw='140.0.100.254')
        self.addLink(enodeb, leaf1)  # port 3

        # dbuf IPv4 host attached to leaf 1
        # DbufHost exists on the root net namespace (inNamespace=False) such
        # that onos can communicate with its grpc service port. However, by
        # running on the root namespace, the default route for docker
        # networking prevents dbuf from pinging hosts on subnets other than the
        # dbuf one (ip=...). However, dbuf can still ping the switch gateway
        # address (gw=...) which is all we need to receive and send buffered
        # packets.
        dbuf1 = self.addHost('dbuf1', cls=DbufHost, mac='00:00:00:00:db:0f', ip='140.0.99.1/24',
                             gw='140.0.99.254')
        self.addLink(dbuf1, leaf1)  # port 4

        # pdn IPv4 host attached to leaf 2
        pdn = self.addHost('pdn', cls=IPv4Host, mac='00:00:00:00:00:20', ip='140.0.200.1/24',
                           gw='140.0.200.254')
        self.addLink(pdn, leaf2)  # port 3


def main():
    net = Mininet(topo=TutorialTopo(), controller=None)
    net.start()
    CLI(net)
    net.stop()
    print '#' * 80
    print 'ATTENTION: Mininet was stopped! Perhaps accidentally?'
    print 'No worries, it will restart automatically in a few seconds...'
    print 'To access again the Mininet CLI, use `make mn-cli`'
    print 'To detach from the CLI (without stopping), press Ctrl-D'
    print 'To permanently quit Mininet, use `make stop`'
    print '#' * 80


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description='Mininet topology script for 2x2 fabric with stratum_bmv2 and IPv4 hosts')
    args = parser.parse_args()
    setLogLevel('info')

    main()
