package org.omecproject.up4.behavior;

import org.omecproject.up4.impl.NorthConstants;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiMatchKey;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;

import java.util.Arrays;

public final class TestConstants {
    public static final ImmutableByteSequence ALL_ONES_32 = ImmutableByteSequence.ofOnes(4);
    public static final ImmutableByteSequence SESSION_ID =
            ImmutableByteSequence.ofOnes(NorthConstants.SESSION_ID_BITWIDTH / 8);
    public static final int COUNTER_ID = 1;
    public static final int PDR_ID = 1;
    public static final int FAR_ID = 1;
    public static final ImmutableByteSequence TEID = ImmutableByteSequence.copyFrom(0xff);
    public static final Ip4Address UE_ADDR = Ip4Address.valueOf("10.0.0.1");
    public static final Ip4Address S1U_ADDR = Ip4Address.valueOf("192.168.0.1");
    public static final Ip4Address ENB_ADDR = Ip4Address.valueOf("192.168.0.2");
    public static final Ip4Prefix S1U_IFACE = Ip4Prefix.valueOf(S1U_ADDR, 24);
    public static final Ip4Prefix UE_POOL = Ip4Prefix.valueOf("17.0.0.0/16");
    public static final ImmutableByteSequence TUNNEL_DPORT = ImmutableByteSequence.copyFrom((short) 1024);

    public static final ImmutableByteSequence FALSE_BYTE = ImmutableByteSequence.copyFrom((byte) 0);
    public static final ImmutableByteSequence TRUE_BYTE = ImmutableByteSequence.copyFrom((byte) 1);

    public static final PiTableEntry UP4_UPLINK_PDR = PiTableEntry.builder()
            .forTable(NorthConstants.PDR_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SRC_IFACE_KEY,
                            ImmutableByteSequence.copyFrom(NorthConstants.IFACE_ACCESS)))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.TEID_KEY, TEID, ALL_ONES_32))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.TUNNEL_DST_KEY,
                            ImmutableByteSequence.copyFrom(S1U_ADDR.toOctets()), ALL_ONES_32))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_PDR)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.PDR_ID_PARAM, PDR_ID),
                            new PiActionParam(NorthConstants.SESSION_ID_PARAM, SESSION_ID),
                            new PiActionParam(NorthConstants.CTR_ID, COUNTER_ID),
                            new PiActionParam(NorthConstants.FAR_ID_PARAM, FAR_ID),
                            new PiActionParam(NorthConstants.DECAP_FLAG_PARAM, TRUE_BYTE)
                    ))
                    .build())
            .build();

    public static final PiTableEntry UP4_DOWNLINK_PDR = PiTableEntry.builder()
            .forTable(NorthConstants.PDR_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SRC_IFACE_KEY,
                            ImmutableByteSequence.copyFrom((byte) NorthConstants.IFACE_CORE)))
                    .addFieldMatch(new PiTernaryFieldMatch(NorthConstants.UE_ADDR_KEY,
                            ImmutableByteSequence.copyFrom(UE_ADDR.toOctets()), ALL_ONES_32))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_PDR)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.PDR_ID_PARAM, PDR_ID),
                            new PiActionParam(NorthConstants.SESSION_ID_PARAM, SESSION_ID),
                            new PiActionParam(NorthConstants.CTR_ID, COUNTER_ID),
                            new PiActionParam(NorthConstants.FAR_ID_PARAM, FAR_ID),
                            new PiActionParam(NorthConstants.DECAP_FLAG_PARAM, FALSE_BYTE)
                    ))
                    .build())
            .build();


    public static final PiTableEntry UP4_UPLINK_FAR = PiTableEntry.builder()
            .forTable(NorthConstants.FAR_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.FAR_ID_KEY,
                            ImmutableByteSequence.copyFrom(FAR_ID)))
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SESSION_ID_KEY, SESSION_ID))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_FAR_NORMAL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.DROP_FLAG, FALSE_BYTE),
                            new PiActionParam(NorthConstants.NOTIFY_FLAG, FALSE_BYTE)
                    ))
                    .build())
            .build();

    public static final PiTableEntry UP4_DOWNLINK_FAR = PiTableEntry.builder()
            .forTable(NorthConstants.FAR_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.FAR_ID_KEY,
                            ImmutableByteSequence.copyFrom(FAR_ID)))
                    .addFieldMatch(new PiExactFieldMatch(NorthConstants.SESSION_ID_KEY, SESSION_ID))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_FAR_TUNNEL)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.DROP_FLAG, FALSE_BYTE),
                            new PiActionParam(NorthConstants.NOTIFY_FLAG, FALSE_BYTE),
                            new PiActionParam(NorthConstants.TUNNEL_TYPE_PARAM, NorthConstants.TUNNEL_TYPE_GTPU),
                            new PiActionParam(NorthConstants.TUNNEL_SRC_PARAM, S1U_ADDR.toInt()),
                            new PiActionParam(NorthConstants.TUNNEL_DST_PARAM, ENB_ADDR.toInt()),
                            new PiActionParam(NorthConstants.TEID_PARAM, TEID),
                            new PiActionParam(NorthConstants.TUNNEL_DPORT_PARAM, TUNNEL_DPORT)))
                    .build())
            .build();

    public static final PiTableEntry UP4_UPLINK_INTERFACE = PiTableEntry.builder()
            .forTable(NorthConstants.IFACE_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiLpmFieldMatch(
                            NorthConstants.IFACE_DST_PREFIX_KEY,
                            ImmutableByteSequence.copyFrom(S1U_IFACE.address().toOctets()),
                            S1U_IFACE.prefixLength()))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_IFACE)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.SRC_IFACE_PARAM, NorthConstants.IFACE_ACCESS),
                            new PiActionParam(NorthConstants.DIRECTION, NorthConstants.DIRECTION_UPLINK)
                    ))
                    .build()).build();

    public static final PiTableEntry UP4_DOWNLINK_INTERFACE = PiTableEntry.builder()
            .forTable(NorthConstants.IFACE_TBL)
            .withMatchKey(PiMatchKey.builder()
                    .addFieldMatch(new PiLpmFieldMatch(
                            NorthConstants.IFACE_DST_PREFIX_KEY,
                            ImmutableByteSequence.copyFrom(UE_POOL.address().toOctets()),
                            UE_POOL.prefixLength()))
                    .build())
            .withAction(PiAction.builder()
                    .withId(NorthConstants.LOAD_IFACE)
                    .withParameters(Arrays.asList(
                            new PiActionParam(NorthConstants.SRC_IFACE_PARAM, NorthConstants.IFACE_CORE),
                            new PiActionParam(NorthConstants.DIRECTION, NorthConstants.DIRECTION_DOWNLINK)
                    ))
                    .build()).build();


    private static ImmutableByteSequence toSessionId(long value) {
        try {
            return ImmutableByteSequence.copyFrom(value).fit(NorthConstants.SESSION_ID_BITWIDTH);
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            return ImmutableByteSequence.ofZeros(NorthConstants.SESSION_ID_BITWIDTH / 8);
        }
    }

    /**
     * Hidden constructor for utility class.
     */
    private TestConstants() {
    }
}
