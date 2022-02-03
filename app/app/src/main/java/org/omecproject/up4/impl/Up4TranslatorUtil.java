/*
 SPDX-License-Identifier: Apache-2.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.impl;

import com.google.common.collect.Range;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiMatchType;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiOptionalFieldMatch;
import org.onosproject.net.pi.runtime.PiRangeFieldMatch;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;

import java.util.Optional;

/**
 * Utility class for manipulation of FlowRules and PiTableEntry objects.
 */
// FIXME: this has many methods in common with FabricUpfTranslatorUtil.
//  could we reuse one of the two? can we make FabriUpfTranslatorUtil public?
final class Up4TranslatorUtil {

    private Up4TranslatorUtil() {
    }

    static ImmutableByteSequence getFieldValue(PiFieldMatch field, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        if (field == null) {
            throw new Up4TranslatorImpl.Up4TranslationException(
                    String.format("Unable to find field %s where expected!", fieldId.toString()));
        }
        if (field.type() == PiMatchType.EXACT) {
            return ((PiExactFieldMatch) field).value();
        } else if (field.type() == PiMatchType.LPM) {
            return ((PiLpmFieldMatch) field).value();
        } else if (field.type() == PiMatchType.TERNARY) {
            return ((PiTernaryFieldMatch) field).value();
        } else if (field.type() == PiMatchType.RANGE) {
            return ((PiRangeFieldMatch) field).lowValue();
        } else if (field.type() == PiMatchType.OPTIONAL) {
            return ((PiOptionalFieldMatch) field).value();
        } else {
            throw new Up4TranslatorImpl.Up4TranslationException(
                    String.format("Field %s has unknown match type: %s", fieldId.toString(), field.type().toString()));
        }
    }

    static ImmutableByteSequence getFieldValue(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return getFieldValue(entry.matchKey().fieldMatch(fieldId).orElse(null), fieldId);
    }

    static ImmutableByteSequence getParamValue(PiAction action, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {

        for (PiActionParam param : action.parameters()) {
            if (param.id().equals(paramId)) {
                return param.value();
            }
        }
        throw new Up4TranslatorImpl.Up4TranslationException(
                String.format("Unable to find parameter %s where expected!", paramId.toString()));
    }

    static ImmutableByteSequence getParamValue(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return getParamValue((PiAction) entry.action(), paramId);
    }

    static boolean fieldIsPresent(PiTableEntry entry, PiMatchFieldId fieldId) {
        return entry.matchKey().fieldMatch(fieldId).isPresent();
    }

    static int getFieldInt(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToInt(getFieldValue(entry, fieldId));
    }

    static byte getFieldByte(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToByte(getFieldValue(entry, fieldId));
    }

    static short getFieldShort(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToShort(getFieldValue(entry, fieldId));
    }

    static int getParamInt(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToInt(getParamValue(entry, paramId));
    }

    static short getParamShort(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToShort(getParamValue(entry, paramId));
    }

    static byte getParamByte(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToByte(getParamValue(entry, paramId));
    }

    static Ip4Address getParamAddress(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return Ip4Address.valueOf(getParamValue(entry, paramId).asArray());
    }

    static Ip4Prefix getFieldPrefix(PiTableEntry entry, PiMatchFieldId fieldId) {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(fieldId);
        if (optField.isEmpty()) {
            return null;
        }
        PiLpmFieldMatch field = (PiLpmFieldMatch) optField.get();
        Ip4Address address = Ip4Address.valueOf(field.value().asArray());
        return Ip4Prefix.valueOf(address, field.prefixLength());
    }

    static Range<Short> getFieldRangeShort(PiTableEntry entry, PiMatchFieldId fieldId) {
        Optional<PiFieldMatch> optField = entry.matchKey().fieldMatch(fieldId);
        if (optField.isEmpty()) {
            return null;
        }
        PiRangeFieldMatch field = (PiRangeFieldMatch) optField.get();
        return Range.closed(byteSeqToShort(field.lowValue()), byteSeqToShort(field.highValue()));
    }

    static int getPriority(PiTableEntry entry) {
        return entry.priority().orElseThrow();
    }

    static Ip4Address getFieldAddress(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return Ip4Address.valueOf(getFieldValue(entry, fieldId).asArray());
    }

    static int byteSeqToInt(ImmutableByteSequence sequence) {
        try {
            return sequence.fit(32).asReadOnlyBuffer().getInt();
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            throw new IllegalArgumentException("Attempted to convert a >4 byte wide sequence to an integer!");
        }
    }

    static byte byteSeqToByte(ImmutableByteSequence sequence) {
        try {
            return sequence.fit(8).asReadOnlyBuffer().get();
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            throw new IllegalArgumentException("Attempted to convert a >1 byte wide sequence to an byte!");
        }
    }

    static short byteSeqToShort(ImmutableByteSequence sequence) {
        try {
            return sequence.fit(16).asReadOnlyBuffer().getShort();
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            throw new IllegalArgumentException("Attempted to convert a >1 byte wide sequence to an byte!");
        }
    }
}
