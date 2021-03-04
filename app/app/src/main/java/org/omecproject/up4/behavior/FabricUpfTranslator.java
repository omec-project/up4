/*
 * SPDX-License-Identifier: LicenseRef-ONF-Member-1.0
 * SPDX-FileCopyrightText: {year}-present Open Networking Foundation <info@opennetworking.org>
 */

package org.omecproject.up4.behavior;

import org.apache.commons.lang3.tuple.Pair;
import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.GtpTunnel;
import org.omecproject.up4.PacketDetectionRule;
import org.omecproject.up4.UpfInterface;
import org.omecproject.up4.UpfProgrammableException;
import org.omecproject.up4.UpfRuleIdentifier;
import org.omecproject.up4.impl.SouthConstants;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiTableAction;

import java.util.Arrays;

/**
 * Provides logic to translate UPF entities into pipeline-specific ones and vice-versa.
 * Implementation should be stateless, with all state delegated to FabricUpfStore.
 */
public class FabricUpfTranslator {

    private final FabricUpfStore upfStore;

    public FabricUpfTranslator(FabricUpfStore upfStore) {
        this.upfStore = upfStore;
    }

    /**
     * Returns true if the given table entry is a Packet Detection Rule from the physical fabric pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a fabric.p4 PDR
     * @return true if the entry is a fabric.p4 PDR
     */
    public boolean isFabricPdr(FlowRule entry) {
        return entry.table().equals(SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_PDRS)
                || entry.table().equals(SouthConstants.FABRIC_INGRESS_SPGW_DOWNLINK_PDRS);
    }

    /**
     * Returns true if the given table entry is a Forwarding Action Rule from the physical fabric pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a fabric.p4 FAR
     * @return true if the entry is a fabric.p4 FAR
     */
    public boolean isFabricFar(FlowRule entry) {
        return entry.table().equals(SouthConstants.FABRIC_INGRESS_SPGW_FARS);
    }

    /**
     * Returns true if the given table entry is an interface table entry from the fabric.p4 physical pipeline, and
     * false otherwise.
     *
     * @param entry the entry that may or may not be a fabric.p4 UPF interface
     * @return true if the entry is a fabric.p4 UPF interface
     */
    public boolean isFabricInterface(FlowRule entry) {
        return entry.table().equals(SouthConstants.FABRIC_INGRESS_SPGW_INTERFACES);
    }


    /**
     * Translate a fabric.p4 PDR table entry to a PacketDetectionRule instance for easier handling.
     *
     * @param entry the fabric.p4 entry to translate
     * @return the corresponding PacketDetectionRule
     * @throws UpfProgrammableException if the entry cannot be translated
     */
    public PacketDetectionRule fabricEntryToPdr(FlowRule entry)
            throws UpfProgrammableException {
        var pdrBuilder = PacketDetectionRule.builder();
        Pair<PiCriterion, PiTableAction> matchActionPair = FabricUpfTranslatorUtil.fabricEntryToPiPair(entry);
        PiCriterion match = matchActionPair.getLeft();
        PiAction action = (PiAction) matchActionPair.getRight();

        // Grab keys and parameters that are present for all PDRs
        int globalFarId = FabricUpfTranslatorUtil.getParamInt(action, SouthConstants.FAR_ID);
        UpfRuleIdentifier farId = upfStore.localFarIdOf(globalFarId);
        pdrBuilder.withCounterId(FabricUpfTranslatorUtil.getParamInt(action, SouthConstants.CTR_ID))
                .withLocalFarId(farId.getSessionLocalId())
                .withSessionId(farId.getPfcpSessionId());

        if (FabricUpfTranslatorUtil.fieldIsPresent(match, SouthConstants.HDR_TEID)) {
            // F-TEID is only present for GTP-matching PDRs
            ImmutableByteSequence teid = FabricUpfTranslatorUtil.getFieldValue(match, SouthConstants.HDR_TEID);
            Ip4Address tunnelDst = FabricUpfTranslatorUtil.getFieldAddress(match, SouthConstants.HDR_TUNNEL_IPV4_DST);
            pdrBuilder.withTeid(teid)
                    .withTunnelDst(tunnelDst);
        } else if (FabricUpfTranslatorUtil.fieldIsPresent(match, SouthConstants.HDR_UE_ADDR)) {
            // And UE address is only present for non-GTP-matching PDRs
            pdrBuilder.withUeAddr(FabricUpfTranslatorUtil.getFieldAddress(match, SouthConstants.HDR_UE_ADDR));
        } else {
            throw new UpfProgrammableException("Read malformed PDR from dataplane!:" + entry);
        }

        return pdrBuilder.build();
    }

    /**
     * Translate a fabric.p4 FAR table entry to a ForwardActionRule instance for easier handling.
     *
     * @param entry the fabric.p4 entry to translate
     * @return the corresponding ForwardingActionRule
     * @throws UpfProgrammableException if the entry cannot be translated
     */
    public ForwardingActionRule fabricEntryToFar(FlowRule entry)
            throws UpfProgrammableException {
        var farBuilder = ForwardingActionRule.builder();
        Pair<PiCriterion, PiTableAction> matchActionPair = FabricUpfTranslatorUtil.fabricEntryToPiPair(entry);
        PiCriterion match = matchActionPair.getLeft();
        PiAction action = (PiAction) matchActionPair.getRight();

        int globalFarId = FabricUpfTranslatorUtil.getFieldInt(match, SouthConstants.HDR_FAR_ID);
        UpfRuleIdentifier farId = upfStore.localFarIdOf(globalFarId);

        boolean dropFlag = FabricUpfTranslatorUtil.getParamInt(action, SouthConstants.DROP) > 0;
        boolean notifyFlag = FabricUpfTranslatorUtil.getParamInt(action, SouthConstants.NOTIFY_CP) > 0;

        // Match keys
        farBuilder.withSessionId(farId.getPfcpSessionId())
                .setFarId(farId.getSessionLocalId());

        // Parameters common to all types of FARs
        farBuilder.setDropFlag(dropFlag)
                .setNotifyFlag(notifyFlag);

        PiActionId actionId = action.id();

        if (actionId.equals(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_TUNNEL_FAR)
                || actionId.equals(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_DBUF_FAR)) {
            // Grab parameters specific to encapsulating FARs if they're present
            Ip4Address tunnelSrc = FabricUpfTranslatorUtil.getParamAddress(action, SouthConstants.TUNNEL_SRC_ADDR);
            Ip4Address tunnelDst = FabricUpfTranslatorUtil.getParamAddress(action, SouthConstants.TUNNEL_DST_ADDR);
            ImmutableByteSequence teid = FabricUpfTranslatorUtil.getParamValue(action, SouthConstants.TEID);
            short tunnelSrcPort = (short) FabricUpfTranslatorUtil.getParamInt(action, SouthConstants.TUNNEL_SRC_PORT);

            farBuilder.setBufferFlag(actionId.equals(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_DBUF_FAR));

            farBuilder.setTunnel(
                    GtpTunnel.builder()
                            .setSrc(tunnelSrc)
                            .setDst(tunnelDst)
                            .setTeid(teid)
                            .setSrcPort(tunnelSrcPort)
                            .build());
        }
        return farBuilder.build();
    }

    /**
     * Translate a fabric.p4 interface table entry to a UpfInterface instance for easier handling.
     *
     * @param entry the fabric.p4 entry to translate
     * @return the corresponding UpfInterface
     * @throws UpfProgrammableException if the entry cannot be translated
     */
    public UpfInterface fabricEntryToInterface(FlowRule entry)
            throws UpfProgrammableException {
        Pair<PiCriterion, PiTableAction> matchActionPair = FabricUpfTranslatorUtil.fabricEntryToPiPair(entry);
        PiCriterion match = matchActionPair.getLeft();
        PiAction action = (PiAction) matchActionPair.getRight();

        var ifaceBuilder = UpfInterface.builder()
                .setPrefix(FabricUpfTranslatorUtil.getFieldPrefix(match, SouthConstants.HDR_IPV4_DST_ADDR));

        int interfaceType = FabricUpfTranslatorUtil.getParamInt(action, SouthConstants.SRC_IFACE);
        if (interfaceType == SouthConstants.INTERFACE_ACCESS) {
            ifaceBuilder.setAccess();
        } else if (interfaceType == SouthConstants.INTERFACE_CORE) {
            ifaceBuilder.setCore();
        } else if (interfaceType == SouthConstants.INTERFACE_DBUF) {
            ifaceBuilder.setDbufReceiver();
        }
        return ifaceBuilder.build();
    }

    /**
     * Translate a ForwardingActionRule to a FlowRule to be inserted into the fabric.p4 pipeline.
     * A side effect of calling this method is the FAR object's globalFarId is assigned if it was not already.
     *
     * @param far      The FAR to be translated
     * @param deviceId the ID of the device the FlowRule should be installed on
     * @param appId    the ID of the application that will insert the FlowRule
     * @param priority the FlowRule's priority
     * @return the FAR translated to a FlowRule
     * @throws UpfProgrammableException if the FAR to be translated is malformed
     */
    public FlowRule farToFabricEntry(ForwardingActionRule far, DeviceId deviceId, ApplicationId appId, int priority)
            throws UpfProgrammableException {
        PiAction action;
        if (!far.encaps()) {
            action = PiAction.builder()
                    .withId(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_NORMAL_FAR)
                    .withParameters(Arrays.asList(
                            new PiActionParam(SouthConstants.DROP, far.drops() ? 1 : 0),
                            new PiActionParam(SouthConstants.NOTIFY_CP, far.notifies() ? 1 : 0)
                    ))
                    .build();

        } else {
            if (far.tunnelSrc() == null || far.tunnelDst() == null
                    || far.teid() == null || far.tunnel().srcPort() == null) {
                throw new UpfProgrammableException(
                        "Not all action parameters present when translating " +
                                "intermediate encapsulating/buffering FAR to physical FAR!");
            }
            // TODO: copy tunnel destination port from logical switch write requests, instead of hardcoding 2152
            PiActionId actionId = far.buffers() ? SouthConstants.FABRIC_INGRESS_SPGW_LOAD_DBUF_FAR :
                    SouthConstants.FABRIC_INGRESS_SPGW_LOAD_TUNNEL_FAR;
            action = PiAction.builder()
                    .withId(actionId)
                    .withParameters(Arrays.asList(
                            new PiActionParam(SouthConstants.DROP, far.drops() ? 1 : 0),
                            new PiActionParam(SouthConstants.NOTIFY_CP, far.notifies() ? 1 : 0),
                            new PiActionParam(SouthConstants.TEID, far.teid()),
                            new PiActionParam(SouthConstants.TUNNEL_SRC_ADDR, far.tunnelSrc().toInt()),
                            new PiActionParam(SouthConstants.TUNNEL_DST_ADDR, far.tunnelDst().toInt()),
                            new PiActionParam(SouthConstants.TUNNEL_SRC_PORT, far.tunnel().srcPort())
                    ))
                    .build();
        }
        PiCriterion match = PiCriterion.builder()
                .matchExact(SouthConstants.HDR_FAR_ID, upfStore.globalFarIdOf(far.sessionId(), far.farId()))
                .build();
        return DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.FABRIC_INGRESS_SPGW_FARS)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(priority)
                .build();
    }

    /**
     * Translate a PacketDetectionRule to a FlowRule to be inserted into the fabric.p4 pipeline.
     * A side effect of calling this method is the PDR object's globalFarId is assigned if it was not already.
     *
     * @param pdr      The PDR to be translated
     * @param deviceId the ID of the device the FlowRule should be installed on
     * @param appId    the ID of the application that will insert the FlowRule
     * @param priority the FlowRule's priority
     * @return the FAR translated to a FlowRule
     * @throws UpfProgrammableException if the PDR to be translated is malformed
     */
    public FlowRule pdrToFabricEntry(PacketDetectionRule pdr, DeviceId deviceId, ApplicationId appId, int priority)
            throws UpfProgrammableException {
        PiCriterion match;
        PiTableId tableId;
        if (pdr.matchesEncapped()) {
            match = PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_TEID, pdr.teid().asArray())
                    .matchExact(SouthConstants.HDR_TUNNEL_IPV4_DST, pdr.tunnelDest().toInt())
                    .build();
            tableId = SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_PDRS;
        } else if (pdr.matchesUnencapped()) {
            match = PiCriterion.builder()
                    .matchExact(SouthConstants.HDR_UE_ADDR, pdr.ueAddress().toInt())
                    .build();
            tableId = SouthConstants.FABRIC_INGRESS_SPGW_DOWNLINK_PDRS;
        } else {
            throw new UpfProgrammableException("Flexible PDRs not yet supported! Cannot translate " + pdr.toString());
        }

        PiAction action = PiAction.builder()
                .withId(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_PDR)
                .withParameters(Arrays.asList(
                        new PiActionParam(SouthConstants.CTR_ID, pdr.counterId()),
                        new PiActionParam(SouthConstants.FAR_ID, upfStore.globalFarIdOf(pdr.sessionId(), pdr.farId())),
                        new PiActionParam(SouthConstants.NEEDS_GTPU_DECAP, pdr.matchesEncapped() ? 1 : 0)
                ))
                .build();

        return DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(tableId)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(priority)
                .build();
    }

    /**
     * Translate a UpfInterface to a FlowRule to be inserted into the fabric.p4 pipeline.
     *
     * @param upfInterface The interface to be translated
     * @param deviceId     the ID of the device the FlowRule should be installed on
     * @param appId        the ID of the application that will insert the FlowRule
     * @param priority     the FlowRule's priority
     * @return the UPF interface translated to a FlowRule
     * @throws UpfProgrammableException if the interface cannot be translated
     */
    public FlowRule interfaceToFabricEntry(UpfInterface upfInterface, DeviceId deviceId,
                                           ApplicationId appId, int priority) throws UpfProgrammableException {
        int interfaceTypeInt;
        int gtpuValidity;
        if (upfInterface.isDbufReceiver()) {
            interfaceTypeInt = SouthConstants.INTERFACE_DBUF;
            gtpuValidity = 1;
        } else if (upfInterface.isAccess()) {
            interfaceTypeInt = SouthConstants.INTERFACE_ACCESS;
            gtpuValidity = 1;
        } else {
            interfaceTypeInt = SouthConstants.INTERFACE_CORE;
            gtpuValidity = 0;
        }

        PiCriterion match = PiCriterion.builder()
                .matchLpm(SouthConstants.HDR_IPV4_DST_ADDR,
                        upfInterface.prefix().address().toInt(),
                        upfInterface.prefix().prefixLength())
                .matchExact(SouthConstants.HDR_GTPU_IS_VALID, gtpuValidity)
                .build();
        PiAction action = PiAction.builder()
                .withId(SouthConstants.FABRIC_INGRESS_SPGW_LOAD_IFACE)
                .withParameter(new PiActionParam(SouthConstants.SRC_IFACE, interfaceTypeInt))
                .build();
        return DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.FABRIC_INGRESS_SPGW_INTERFACES)
                .withSelector(DefaultTrafficSelector.builder().matchPi(match).build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(priority)
                .build();
    }

    /**
     * Builds FlowRules for the uplink recirculation table.
     *
     * @param deviceId the ID of the device the FlowRule should be installed on
     * @param appId    the ID of the application that will insert the FlowRule
     * @param src      the Ipv4 source prefix
     * @param dst      the Ipv4 destination prefix
     * @param allow    whether to allow or not (drop) recirculation
     * @param priority the FlowRule's priority
     * @return FlowRule for the uplink recirculation table
     */
    // FIXME: this method is specific to fabric-tna and might be removed once we create proper
    //   pipeconf behavior for fabric-v1model, unless we add the same uplink recirculation
    //   capability to that P4 program as well.
    public FlowRule buildFabricUplinkRecircEntry(DeviceId deviceId, ApplicationId appId,
                                                 Ip4Prefix src, Ip4Prefix dst,
                                                 boolean allow, int priority) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        if (src != null) {
            selectorBuilder.matchIPSrc(src);
        }
        if (dst != null) {
            selectorBuilder.matchIPDst(dst);
        }
        PiAction action = PiAction.builder()
                .withId(allow ? SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_RECIRC_ALLOW
                        : SouthConstants.FABRIC_INGRESS_FILTERING_DENY)
                .build();
        return DefaultFlowRule.builder()
                .forDevice(deviceId).fromApp(appId).makePermanent()
                .forTable(SouthConstants.FABRIC_INGRESS_SPGW_UPLINK_RECIRC_RULES)
                .withSelector(selectorBuilder.build())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build())
                .withPriority(priority)
                .build();
    }

}
