package org.omecproject.up4.impl;

import org.omecproject.up4.ForwardingActionRule;
import org.omecproject.up4.NorthConstants;
import org.omecproject.up4.PacketDetectionRule;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiMatchType;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiRangeFieldMatch;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;

import java.util.Optional;

public final class Up4Translator {

    protected static final ImmutableByteSequence ZERO_SEQ = ImmutableByteSequence.ofZeros(4);

    /**
     * Hidden constructor for utility class.
     */
    private Up4Translator() {
    }

    protected static ImmutableByteSequence getFieldValue(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslationException {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(fieldId);
        if (optField.isEmpty()) {
            throw new Up4TranslationException(
                    String.format("Unable to find field %s where expected!", fieldId.toString()));
        }
        PiFieldMatch field = optField.get();
        if (field.type() == PiMatchType.EXACT) {
            return ((PiExactFieldMatch) field).value();
        } else if (field.type() == PiMatchType.LPM) {
            return ((PiLpmFieldMatch) field).value();
        } else if (field.type() == PiMatchType.TERNARY) {
            return ((PiTernaryFieldMatch) field).value();
        } else if (field.type() == PiMatchType.RANGE) {
            return ((PiRangeFieldMatch) field).lowValue();
        } else {
            throw new Up4TranslationException(
                    String.format("Field %s has unknown match type: %s", fieldId.toString(), field.type().toString()));
        }
    }

    protected static ImmutableByteSequence getParamValue(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslationException {
        PiAction action = (PiAction) entry.action();

        for (PiActionParam param : action.parameters()) {
            if (param.id().equals(paramId)) {
                return param.value();
            }
        }
        throw new Up4TranslationException(
                String.format("Unable to find parameter %s where expected!", paramId.toString()));
    }

    protected static int getFieldInt(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslationException {
        return byteSeqToInt(getFieldValue(entry, fieldId));
    }

    protected static int getParamInt(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslationException {
        return byteSeqToInt(getParamValue(entry, paramId));
    }

    protected static Ip4Address getParamAddress(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslationException {
        return Ip4Address.valueOf(getParamValue(entry, paramId).asArray());
    }

    protected static Ip4Prefix getFieldPrefix(PiTableEntry entry, PiMatchFieldId fieldId) {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(fieldId);
        if (optField.isEmpty()) {
            return null;
        }
        PiLpmFieldMatch field = (PiLpmFieldMatch) optField.get();
        Ip4Address address = Ip4Address.valueOf(field.value().asArray());
        return Ip4Prefix.valueOf(address, field.prefixLength());
    }

    protected static Ip4Address getFieldAddress(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslationException {
        return Ip4Address.valueOf(getFieldValue(entry, fieldId).asArray());
    }

    protected static boolean fieldMaskIsZero(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslationException {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(fieldId);
        if (optField.isEmpty()) {
            return true;
        }
        PiFieldMatch field = optField.get();
        if (field.type() != PiMatchType.TERNARY) {
            throw new Up4TranslationException(
                    String.format("Attempting to check mask for non-ternary field: %s", fieldId.toString()));
        }
        for (byte b : ((PiTernaryFieldMatch) field).mask().asArray()) {
            if (b != (byte) 0) {
                return false;
            }
        }
        return true;
    }

    protected static int byteSeqToInt(ImmutableByteSequence sequence) {
        try {
            return sequence.fit(32).asReadOnlyBuffer().getInt();
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            throw new IllegalArgumentException("Attempted to convert a >4 byte wide sequence to an integer!");
        }
    }

    public static boolean isPdr(PiTableEntry entry) {
        return entry.table().equals(NorthConstants.PDR_TBL);
    }

    public static boolean isFar(PiTableEntry entry) {
        return entry.table().equals(NorthConstants.FAR_TBL);
    }

    public static boolean isInterface(PiTableEntry entry) {
        return entry.table().equals(NorthConstants.IFACE_TBL);
    }

    public static boolean isS1uInterface(PiTableEntry entry) {
        if (!isInterface(entry)) {
            return false;
        }
        int direction;
        try {
            direction = byteSeqToInt(getParamValue(entry, NorthConstants.DIRECTION));
        } catch (Up4TranslationException e) {
            return false;
        }
        return direction == NorthConstants.DIRECTION_UPLINK;
    }

    public static boolean isUePool(PiTableEntry entry) {
        if (!isInterface(entry)) {
            return false;
        }
        int direction;
        try {
            direction = byteSeqToInt(getParamValue(entry, NorthConstants.DIRECTION));
        } catch (Up4TranslationException e) {
            return false;
        }
        return direction == NorthConstants.DIRECTION_DOWNLINK;
    }

    public static PacketDetectionRule piEntryToPdr(PiTableEntry entry)
            throws Up4TranslationException {
        var pdrBuilder = PacketDetectionRule.builder();
        // Uplink and downlink both have a UE address key
        pdrBuilder.withUeAddr(getFieldAddress(entry, NorthConstants.UE_ADDR_KEY));

        int srcInterface = getFieldInt(entry, NorthConstants.SRC_IFACE_KEY);
        if (srcInterface == NorthConstants.IFACE_ACCESS) {
            // uplink entries will also have a F-TEID key (tunnel destination address + TEID)
            pdrBuilder.withTunnel(getFieldValue(entry, NorthConstants.TEID_KEY),
                    getFieldAddress(entry, NorthConstants.TUNNEL_DST_KEY));
        } else if (srcInterface != NorthConstants.IFACE_CORE) {
            throw new Up4TranslationException("Flexible PDRs not yet supported.");
        }

        // Now get the action parameters, if they are present (entries from delete writes don't have parameters)
        PiAction action = (PiAction) entry.action();
        PiActionId actionId = action.id();
        if (actionId.equals(NorthConstants.LOAD_PDR) && !action.parameters().isEmpty()) {
            pdrBuilder.withSessionId(getParamValue(entry, NorthConstants.SESSION_ID_PARAM))
                    .withCounterId(getParamInt(entry, NorthConstants.CTR_ID))
                    .withFarId(getParamInt(entry, NorthConstants.FAR_ID_PARAM));
        }

        return pdrBuilder.build();
    }

    public static ForwardingActionRule piEntryToFar(PiTableEntry entry)
            throws Up4TranslationException {
        // First get the match keys
        var farBuilder = ForwardingActionRule.builder()
                .withFarId(byteSeqToInt(getFieldValue(entry, NorthConstants.FAR_ID_KEY)))
                .withSessionId(getFieldValue(entry, NorthConstants.SESSION_ID_KEY));

        // Now get the action parameters, if they are present (entries from delete writes don't have parameters)
        PiAction action = (PiAction) entry.action();
        PiActionId actionId = action.id();
        if (!action.parameters().isEmpty()) {
            // Parameters that both types of FAR have
            farBuilder.withDropFlag(getParamInt(entry, NorthConstants.DROP_FLAG) > 0)
                    .withNotifyFlag(getParamInt(entry, NorthConstants.NOTIFY_FLAG) > 0);
            if (actionId.equals(NorthConstants.LOAD_FAR_TUNNEL)) {
                // Parameters exclusive to a downlink FAR
                farBuilder.withTunnel(
                        getParamAddress(entry, NorthConstants.TUNNEL_SRC_PARAM),
                        getParamAddress(entry, NorthConstants.TUNNEL_DST_PARAM),
                        getParamValue(entry, NorthConstants.TEID_PARAM));
            }
        }

        return farBuilder.build();
    }

    public static Ip4Prefix piEntryToInterfacePrefix(PiTableEntry entry) {
        return getFieldPrefix(entry, NorthConstants.IFACE_DST_PREFIX_KEY);
    }

    static class Up4TranslationException extends Exception {
        /**
         * Creates a new exception for the given message.
         *
         * @param message message
         */
        public Up4TranslationException(String message) {
            super(message);
        }
    }
}
