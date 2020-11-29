#!/usr/bin/python
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0

from scapy.contrib import gtp
from scapy.all import send, sniff, Ether, IP, UDP
import sys, signal

RATE = 5  # packets per second
TIMEOUT = 10  # seconds until timeout when waiting for a packet

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


def send_gtp(send_count=None):
    pkt =   IP(src=ENB_ADDR, dst=S1U_ADDR) / \
            UDP(dport=GPDU_PORT) / \
            gtp.GTPHeader(version=1, gtp_type=0xff, teid=TEID) / \
            IP(src=UE_ADDR, dst=PDN_ADDR) / \
            UDP(sport=UE_PORT, dport=PDN_PORT) / \
            ' '.join(['P4 is inevitable!'] * 50)

    send(pkt, inter=1.0 / RATE, loop=send_count is None, count=send_count, verbose=True)


def send_gtp_10():
    send_gtp(10)


def send_udp(send_count=None):
    pkt =   IP(src=PDN_ADDR, dst=UE_ADDR) / \
            UDP(sport=PDN_PORT, dport=UE_PORT) / \
            ' '.join(['P4 is great!'] * 50)

    send(pkt, inter=1.0 / RATE, loop=send_count is None, count=send_count, verbose=True)


def send_udp_10():
    send_udp(10)


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


def sniff_gtp(kind="gtp"):
    sniff_stuff(kind="gtp")


def sniff_udp():
    sniff_stuff(kind="udp")


def sniff_stuff(kind):
    print("Will print a line for each UDP packet received...")
    sniff(count=0, store=False, filter="udp", prn=lambda x: handle_pkt(x, kind))


def handle_timeout(signum, frame):
    print("Timeout! Did not receive expected packet")
    exit(1)


if __name__ == "__main__":

    funcs = {
        "send-gtp": send_gtp,
        "send-gtp-10": send_gtp_10,
        "send-udp": send_udp,
        "send-udp-10": send_udp_10,
        "recv-gtp": sniff_gtp,
        "recv-udp": sniff_udp
    }

    usage = "usage: %s <%s> [-t <timeout>]" % (sys.argv[0], '|'.join(funcs.keys()))

    command = sys.argv[1] if len(sys.argv) > 1 else None

    if len(sys.argv) > 2:
        if sys.argv[2] == '-t':
            try:
                timeout = int(sys.argv[3])
                prep_brief_test(timeout)
            except:
                print("Bad or no timeout provided with -t flag")
                print(usage)
                exit(1)
        else:
            print(usage)
            exit(1)

    if command not in funcs:
        print(usage)
        exit(1)

    funcs[command]()
