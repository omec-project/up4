#!/bin/bash
set -ex
apt-get update
apt-get install -y git net-tools python3 tcpdump vim iputils-ping
git clone https://github.com/secdev/scapy
apt install -y python3-pip
apt install -y screen
cd scapy/
python3 setup.py install
pip3 install ifcfg
