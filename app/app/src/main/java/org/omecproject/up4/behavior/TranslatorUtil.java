/*
 SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
 SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
 */
package org.omecproject.up4.behavior;

import org.apache.commons.lang3.tuple.Pair;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.PiInstruction;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiMatchType;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiExactFieldMatch;
import org.onosproject.net.pi.runtime.PiFieldMatch;
import org.onosproject.net.pi.runtime.PiLpmFieldMatch;
import org.onosproject.net.pi.runtime.PiRangeFieldMatch;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.onosproject.net.pi.runtime.PiTableEntry;
import org.onosproject.net.pi.runtime.PiTernaryFieldMatch;

import java.util.Optional;

/**
 * Utility class for manipulation of FlowRules and PiTableEntry objects.
 */
final class TranslatorUtil {

    private TranslatorUtil() {
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
        } else {
            throw new Up4TranslatorImpl.Up4TranslationException(
                    String.format("Field %s has unknown match type: %s", fieldId.toString(), field.type().toString()));
        }
    }

    static ImmutableByteSequence getFieldValue(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return getFieldValue(entry.matchKey().fieldMatch(fieldId).orElse(null), fieldId);
    }

    static ImmutableByteSequence getFieldValue(PiCriterion criterion, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return getFieldValue(criterion.fieldMatch(fieldId).orElse(null), fieldId);
    }

    static boolean paramIsPresent(PiAction action, PiActionParamId paramId) {
        for (PiActionParam param : action.parameters()) {
            if (param.id().equals(paramId)) {
                return true;
            }
        }
        return false;
    }

    static boolean paramIsPresent(PiTableEntry entry, PiActionParamId paramId) {
        return paramIsPresent((PiAction) entry.action(), paramId);
    }

    static boolean fieldIsPresent(PiCriterion criterion, PiMatchFieldId fieldId) {
        return criterion.fieldMatch(fieldId).isPresent();
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

    static int getFieldInt(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToInt(getFieldValue(entry, fieldId));
    }

    static int getFieldInt(PiCriterion criterion, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToInt(getFieldValue(criterion, fieldId));
    }

    static int getParamInt(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToInt(getParamValue(entry, paramId));
    }

    static int getParamInt(PiAction action, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return byteSeqToInt(getParamValue(action, paramId));
    }

    static Ip4Address getParamAddress(PiTableEntry entry, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return Ip4Address.valueOf(getParamValue(entry, paramId).asArray());
    }

    static Ip4Address getParamAddress(PiAction action, PiActionParamId paramId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return Ip4Address.valueOf(getParamValue(action, paramId).asArray());
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

    static Ip4Prefix getFieldPrefix(PiCriterion criterion, PiMatchFieldId fieldId) {
        Optional<PiFieldMatch> optField = criterion.fieldMatch(fieldId);
        if (optField.isEmpty()) {
            return null;
        }
        PiLpmFieldMatch field = (PiLpmFieldMatch) optField.get();
        Ip4Address address = Ip4Address.valueOf(field.value().asArray());
        return Ip4Prefix.valueOf(address, field.prefixLength());
    }

    static Ip4Address getFieldAddress(PiTableEntry entry, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return Ip4Address.valueOf(getFieldValue(entry, fieldId).asArray());
    }

    static Ip4Address getFieldAddress(PiCriterion criterion, PiMatchFieldId fieldId)
            throws Up4TranslatorImpl.Up4TranslationException {
        return Ip4Address.valueOf(getFieldValue(criterion, fieldId).asArray());
    }

    static int byteSeqToInt(ImmutableByteSequence sequence) {
        try {
            return sequence.fit(32).asReadOnlyBuffer().getInt();
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            throw new IllegalArgumentException("Attempted to convert a >4 byte wide sequence to an integer!");
        }
    }

    static Pair<PiCriterion, PiTableAction> fabricEntryToPiPair(FlowRule entry) {
        PiCriterion match = (PiCriterion) entry.selector().getCriterion(Criterion.Type.PROTOCOL_INDEPENDENT);
        PiTableAction action = null;
        for (Instruction instruction : entry.treatment().allInstructions()) {
            if (instruction.type() == Instruction.Type.PROTOCOL_INDEPENDENT) {
                PiInstruction piInstruction = (PiInstruction) instruction;
                action = piInstruction.action();
                break;
            }
        }
        return Pair.of(match, action);
    }
}
