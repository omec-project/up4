# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0

from mininet.node import Host, Node
import socket
import struct

DBUF_DROP_TIMEOUT_SEC = "30s"
DBUF_NUM_QUEUES = 10
DBUF_MAX_PKTS_PER_QUEUE = 16


def ip2long(ip):
    """
    Convert an IP string to long
    """
    packedIP = socket.inet_aton(ip)
    return struct.unpack("!L", packedIP)[0]


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

    def config(self, drainIp=None, drainMac=None, **_params):
        super(DbufHost, self).config(**_params)
        self.setDrainIpAndMac(self.defaultIntf(), drainIp, drainMac)
        self.startDbuf()

    def startDbuf(self):
        args = map(str, [
            "-max_queues",
            DBUF_NUM_QUEUES,
            "-max_packet_slots_per_queue",
            DBUF_MAX_PKTS_PER_QUEUE,
            "-queue_drop_timeout",
            DBUF_DROP_TIMEOUT_SEC,
        ])
        # Send to background
        cmd = '/usr/local/bin/dbuf %s > /tmp/dbuf_%s.log 2>&1 &' \
              % (" ".join(args), self.name)
        print(cmd)
        self.cmd(cmd)

    def setDrainIpAndMac(self, intf, drainIp=None, drainMac=None):
        if drainIp:
            self.setHostRoute(drainIp, intf)
            if drainMac:
                self.setARP(drainIp, drainMac)


class DualHomedIpv4Host(Host):
    """A dual homed host that can be configured with an IPv4 gateway (default route).
    """

    def __init__(self, name, **kwargs):
        super(DualHomedIpv4Host, self).__init__(name, **kwargs)
        self.bond0 = None

    def config(self, ip=None, gw=None, **kwargs):
        super(DualHomedIpv4Host, self).config(**kwargs)
        intf0 = self.intfs[0].name
        intf1 = self.intfs[1].name
        self.bond0 = "%s-bond0" % self.name
        self.cmd('modprobe bonding')
        self.cmd('ip link add %s type bond miimon 100 mode balance-xor xmit_hash_policy layer2+3' %
                 self.bond0)
        self.cmd('ip link set %s down' % intf0)
        self.cmd('ip link set %s down' % intf1)
        self.cmd('ip link set %s master %s' % (intf0, self.bond0))
        self.cmd('ip link set %s master %s' % (intf1, self.bond0))
        self.cmd('ip addr flush dev %s' % intf0)
        self.cmd('ip addr flush dev %s' % intf1)
        self.cmd('ip link set %s up' % self.bond0)

        self.cmd('sysctl -w net.ipv4.ip_forward=0')
        self.cmd('ip -4 addr add %s dev %s' % (ip, self.bond0))
        if gw:
            self.cmd('ip -4 route add default via %s' % gw)
        # Disable offload
        for attr in ["rx", "tx", "sg"]:
            cmd = "/sbin/ethtool --offload %s %s off" % (self.defaultIntf(), attr)
            self.cmd(cmd)

    def terminate(self, **kwargs):
        self.cmd('ip link set %s down' % self.bond0)
        self.cmd('ip link delete %s' % self.bond0)
        super(DualHomedIpv4Host, self).terminate()


class DualHomedDbufHost(DualHomedIpv4Host, DbufHost):

    def __init__(self, name, inNamespace=False, **params):
        super(DualHomedDbufHost, self).__init__(name, inNamespace=inNamespace, **params)

    def config(self, drainIp=None, drainMac=None, **_params):
        super(DualHomedDbufHost, self).config(**_params)
        self.setDrainIpAndMac(self.bond0, drainIp, drainMac)
