#!/usr/bin/python
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

from scapy.contrib import gtp
from scapy.all import send, sniff, Ether, IP, UDP
import sys, signal
import argparse

RATE = 5  # packets per second

UE_ADDR = '17.0.0.1'
ENB_ADDR = '140.0.100.1'
PDN_ADDR = '140.0.200.1'
S1U_ADDR = '140.0.100.254'

UE_PORT = 400
PDN_PORT = 80
GPDU_PORT = 2152

TEID = 255

pkt_count = 0

exitOnSuccess = False


def prep_brief_test(timeout):
    global exitOnSuccess
    exitOnSuccess = True
    # wait timeout seconds or exit
    signal.signal(signal.SIGALRM, handle_timeout)
    signal.alarm(timeout)


def send_gtp(args):
    pkt =   IP(src=ENB_ADDR, dst=S1U_ADDR) / \
            UDP(dport=GPDU_PORT) / \
            gtp.GTPHeader(version=1, gtp_type=0xff, teid=TEID) / \
            IP(src=UE_ADDR, dst=PDN_ADDR) / \
            UDP(sport=UE_PORT, dport=PDN_PORT) / \
            ' '.join(['P4 is inevitable!'] * 50)

    send(pkt, inter=1.0 / RATE, loop=args.count != 1, count=args.count, verbose=True)


def send_udp(args):
    pkt = IP(src=PDN_ADDR, dst=UE_ADDR) / \
          UDP(sport=PDN_PORT, dport=UE_PORT) / \
          ' '.join(['P4 is great!'] * 50)

    send(pkt, inter=1.0 / RATE, loop=args.count != 1, count=args.count, verbose=True)


def handle_pkt(pkt, kind):
    global exitOnSuccess
    exp_gtp_encap = False
    if kind == "gtp":
        exp_gtp_encap = True
    elif kind != "udp":
        print("Bad handle_pkt kind argument: %s" % kind)
        exit(1)

    global pkt_count
    pkt_count = pkt_count + 1
    if gtp.GTP_U_Header in pkt:
        is_gtp_encap = True
    else:
        is_gtp_encap = False

    print("[%d] %d bytes: %s -> %s, is_gtp_encap=%s\n\t%s" \
          % (pkt_count, len(pkt), pkt[IP].src, pkt[IP].dst,
             is_gtp_encap, pkt.summary()))

    if exitOnSuccess:
        if exp_gtp_encap == is_gtp_encap:
            print("Received expected packet!")
            exit(0)
        else:
            if exp_gtp_encap:
                print("Expected a GTP encapped packet but received non-encapped!")
            else:
                print("Expected a non-GTP-encapped packet but received encapped!")
            exit(1)


def sniff_gtp(args):
    sniff_stuff(args, kind="gtp")


def sniff_udp(args):
    sniff_stuff(args, kind="udp")


def sniff_nothing(args):
    def succeed_on_timeout(signum, frame):
        print("Received no packet after %d seconds, as expected." % args.timeout)
        exit(0)

    def fail_on_sniff(pkt):
        print("Received unexpected packet!")
        pkt.show()
        exit(1)
    signal.signal(signal.SIGALRM, succeed_on_timeout)
    signal.alarm(args.timeout)
    sniff(count=0, store=False, filter="udp", prn=fail_on_sniff)


def sniff_stuff(args, kind):
    if args.timeout != 0:
        prep_brief_test(args.timeout)
    print("Will print a line for each UDP packet received...")
    sniff(count=0, store=False, filter="udp", prn=lambda x: handle_pkt(x, kind))


def handle_timeout(signum, frame):
    print("Timeout! Did not receive expected packet")
    exit(1)


if __name__ == "__main__":

    funcs = {
        "send-gtp": send_gtp,
        "send-udp": send_udp,
        "recv-gtp": sniff_gtp,
        "recv-udp": sniff_udp,
        "recv-none": sniff_nothing
    }

    parser = argparse.ArgumentParser()
    parser.add_argument("command", type=str, help="The action to perform",
                        choices=funcs.keys())
    parser.add_argument("-t", dest="timeout", type=int, default=0, help="How long to wait for packets before giving up")
    parser.add_argument("-c", dest="count", type=int, default=1, help="How many packets to transmit")
    args = parser.parse_args()

    funcs[args.command](args)
