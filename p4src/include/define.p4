/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
#ifndef __DEFINE__
#define __DEFINE__


// CPU_PORT specifies the P4 port number associated to controller packet-in and
// packet-out.
#define CPU_PORT 255

// CPU_CLONE_SESSION_ID specifies the mirroring session for packets to be cloned
// to the CPU port. For cloning to work, the P4Runtime client needs first to
// insert a CloneSessionEntry that maps this session ID to the CPU_PORT.
#define CPU_CLONE_SESSION_ID 99

// Table sizes to be tuned for hardware
#define MAX_PDRS 1024
#define MAX_APP_METERS 1024
#define MAX_SESSION_METERS 1024
#define MAX_ROUTES 1024


// Some sizes
#define ETH_HDR_SIZE 14
#define IPV4_HDR_SIZE 20
#define IPV4_MIN_IHL 5
#define UDP_HDR_SIZE 8
#define GTP_HDR_MIN_SIZE 8
#define GTPU_OPTIONS_HDR_BYTES 4
#define GTPU_EXT_PSC_HDR_BYTES 4
#define SLICE_ID_WIDTH 4
#define TC_WIDTH 2
#define SLICE_TC_WIDTH 6
// Some field values that would be excessive as enums
const bit<4> IP_VERSION_4 = 4;
const bit<8> DEFAULT_IPV4_TTL = 64;

typedef bit<48>  mac_addr_t;
typedef bit<32>  ipv4_addr_t;
typedef bit<16>  l4_port_t;
typedef bit<8>   ip_proto_t;
typedef bit<16>  eth_type_t;

const bit<16> UDP_PORT_GTPU = 2152;
const bit<3> GTP_V1 = 0x1;
const bit<1> GTP_PROTOCOL_TYPE_GTP = 0x1;
const bit<8> GTP_MESSAGE_TYPE_UPDU = 0xff;
const bit<8> GTPU_NEXT_EXT_NONE = 0x0;
const bit<8> GTPU_NEXT_EXT_PSC = 0x85;
const bit<4> GTPU_EXT_PSC_TYPE_DL = 4w0; // Downlink
const bit<4> GTPU_EXT_PSC_TYPE_UL = 4w1; // Uplink
const bit<8> GTPU_EXT_PSC_LEN = 8w1; // 1*4-octets

typedef bit<32> teid_t;
typedef bit<6> qfi_t;
typedef bit<32> counter_index_t;
typedef bit<8> tunnel_peer_id_t;
typedef bit<32> app_meter_idx_t;
typedef bit<32> session_meter_idx_t;
typedef bit<SLICE_TC_WIDTH> slice_tc_meter_idx_t;

typedef bit<SLICE_ID_WIDTH> slice_id_t;
typedef bit<TC_WIDTH> tc_t; // Traffic Class (for QoS) within a slice

const qfi_t DEFAULT_QFI = 0;

// Signal that NO application ID has been set
const bit<8> APP_ID_UNKNOWN = 0;

const session_meter_idx_t DEFAULT_SESSION_METER_IDX = 0;
const app_meter_idx_t DEFAULT_APP_METER_IDX = 0;

//------------------------------------------------------------------------------
// ENUMS
// These should be exposed to the control plane by P4Runtime.
//------------------------------------------------------------------------------

enum bit<8> GTPUMessageType {
    GPDU = 255
}

enum bit<16> EtherType {
    IPV4 = 0x0800,
    IPV6 = 0x86dd // unused
}

enum bit<8> IpProtocol {
    ICMP    = 1,
    TCP     = 6,
    UDP     = 17
}

enum bit<16> L4Port {
    DHCP_SERV       = 67, // naming this DHCP_SERVER causes a syntax error..
    DHCP_CLIENT     = 68,
    GTP_GPDU        = 2152,
    IPV4_IN_UDP     = 9875 // placeholder. port has not yet been assigned by IANA
}

enum bit<8> Direction {
    UNKNOWN             = 0x0,
    UPLINK              = 0x1,
    DOWNLINK            = 0x2,
    OTHER               = 0x3
};

enum bit<8> InterfaceType {
    UNKNOWN       = 0x0,
    ACCESS        = 0x1,
    CORE          = 0x2
}

enum bit<4> Slice {
    DEFAULT = 0x0
}

enum bit<2> TrafficClass {
    BEST_EFFORT = 0,
    CONTROL = 1,
    REAL_TIME = 2,
    ELASTIC = 3
}

enum bit<2> MeterColor {
    GREEN = V1MODEL_METER_COLOR_GREEN,
    YELLOW = V1MODEL_METER_COLOR_YELLOW,
    RED = V1MODEL_METER_COLOR_RED
}

#endif
