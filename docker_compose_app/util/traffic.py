#!/usr/bin/python

from scapy.contrib import gtp
from scapy.all import send, sniff, Ether, IP, UDP
import sys

RATE = 5  # packets per second

UE_ADDR = '17.0.0.1'
ENB_ADDR = '140.0.100.1'
PDN_ADDR = '140.0.200.1'
S1U_ADDR = '140.0.100.254'

UE_PORT = 400
PDN_PORT = 80
GPDU_PORT = 2152

TEID=255

pkt_count = 0


def send_gtp():
    pkt =   IP(src=ENB_ADDR, dst=S1U_ADDR) / \
            UDP(dport=GPDU_PORT) / \
            gtp.GTPHeader(version=1, gtp_type=0xff, teid=TEID) / \
            IP(src=UE_ADDR, dst=PDN_ADDR) / \
            UDP(sport=UE_PORT, dport=PDN_PORT) / \
            ' '.join(['P4 is inevitable!'] * 50)

    send(pkt, inter=1.0 / RATE, loop=True, verbose=True)


def send_udp():
    pkt =   IP(src=PDN_ADDR, dst=UE_ADDR) / \
            UDP(sport=PDN_PORT, dport=UE_PORT) / \
            ' '.join(['P4 is great!'] * 50)

    send(pkt, inter=1.0 / RATE, loop=True, verbose=True)


def handle_pkt(pkt):
    global pkt_count
    pkt_count = pkt_count + 1
    if gtp.GTP_U_Header in pkt:
        is_gtp_encap = True
    else:
        is_gtp_encap = False

    print("[%d] %d bytes: %s -> %s, is_gtp_encap=%s\n\t%s" \
          % (pkt_count, len(pkt), pkt[IP].src, pkt[IP].dst,
             is_gtp_encap, pkt.summary()))

def sniff_stuff():
    print("Will print a line for each UDP packet received...")
    sniff(count=0, store=False, filter="udp",
          prn=lambda x: handle_pkt(x))


if __name__ == "__main__":

    funcs = {"send-gtp" : send_gtp,
             "send-udp" : send_udp,
             "recv"     : sniff_stuff}

    usage = "usage: %s <%s>" % (sys.argv[0], '|'.join(funcs.keys()))


    command = sys.argv[1] if len(sys.argv) > 1 else None

    if command not in funcs:
        print(usage)
        exit(1)

    funcs[command]()


