#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
import queue
import sys
import time

from google.protobuf import text_format

sys.path.append('/p4runtime-sh/')

import p4runtime_sh.shell as sh
import argparse


def get_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", type=str, default="onos1:51001",
                        help="Address and port of the p4runtime server")
    parser.add_argument("--election_id", type=int, default=1,
                        help="Election ID")
    parser.add_argument("--type", type=str, default=None,
                        help="Type of stream message to listen for (all if not set)")
    parser.add_argument("--count", type=int, default=-1,
                        help="Exit when the number of messages has been received "
                             "(-1 to listen until timeout expires)")
    parser.add_argument("-t", type=int, default=10,
                        help="Timeout in seconds")
    return parser.parse_args()


args = get_args()


def main():
    # Connect to gRPC server
    sh.setup(
        device_id=1,
        grpc_addr=args.server,
        election_id=(0, args.election_id))

    timeout = args.t
    start = time.time()
    count = 0
    try:
        while True:
            remaining = timeout - (time.time() - start)
            if remaining < 0:
                break
            msg = sh.client.stream_in_q.get(timeout=remaining)
            if msg is not None:
                if args.type and not msg.HasField(args.type):
                    continue
                count = count + 1
                print(text_format.MessageToString(msg, as_one_line=True))
                if count == args.count:
                    break
    except queue.Empty:  # timeout expired
        pass

    sh.teardown()

    if count == 0:
        print("No message received before timeout (%d seconds)" % args.t)
        exit(1)
    else:
        exit(0)


if __name__ == "__main__":
    main()
