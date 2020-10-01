package org.omecproject.up4;

import org.onlab.util.ImmutableByteSequence;

import java.util.Objects;

/**
 * Wrapper for identifying information of FARs and PDRs.
 */
public final class UpfRuleIdentifier {
    private final int sessionlocalId;
    private final ImmutableByteSequence pfcpSessionId;

    /**
     * A PDR or FAR can be globally uniquely identified by the combination of the ID of the PFCP session that
     * produced it, and the ID that the rule was assigned in that PFCP session.
     *
     * @param pfcpSessionId  The PFCP session that produced the rule ID
     * @param sessionlocalId The rule ID
     */
    public UpfRuleIdentifier(ImmutableByteSequence pfcpSessionId, int sessionlocalId) {
        this.pfcpSessionId = pfcpSessionId;
        this.sessionlocalId = sessionlocalId;
    }

    public static UpfRuleIdentifier of(ImmutableByteSequence pfcpSessionId, int sessionlocalId) {
        return new UpfRuleIdentifier(pfcpSessionId, sessionlocalId);
    }

    public int getSessionlocalId() {
        return sessionlocalId;
    }

    public ImmutableByteSequence getPfcpSessionId() {
        return pfcpSessionId;
    }

    @Override
    public String toString() {
        return "RuleIdentifier{" +
                "sessionlocalId=" + sessionlocalId +
                ", pfcpSessionId=" + pfcpSessionId +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        UpfRuleIdentifier that = (UpfRuleIdentifier) obj;
        return (this.sessionlocalId == that.sessionlocalId) && (this.pfcpSessionId.equals(that.pfcpSessionId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.sessionlocalId, this.pfcpSessionId);
    }
}
