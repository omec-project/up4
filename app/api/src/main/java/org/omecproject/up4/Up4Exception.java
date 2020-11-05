package org.omecproject.up4;

public class Up4Exception extends Exception {
    public enum Type {
        UNKNOWN,
        INVALID_COUNTER_INDEX,
        INVALID_ENTITY,
        INVALID_PDR,
        INVALID_FAR,
        INVALID_INTERFACE,
        UNSUPPORTED_PDR,
        SWITCH_UNAVAILABLE,
        CONFIG_UNAVAILABLE,
        P4INFO_UNAVAILABLE,
        INVALID_ACTION,
        INVALID_TABLE,
        INVALID_COUNTER,
        ENTRY_NOT_FOUND
    }

    private final Type type;

    /**
     * Get the type of this exception.
     *
     * @return exception type
     */
    public Type getType() {
        return type;
    }

    /**
     * Creates a new UP4 exception for the given message, with an unknown type.
     *
     * @param message message
     */
    public Up4Exception(String message) {
        super(message);
        this.type = Type.UNKNOWN;
    }

    /**
     * Creates a new UP4 exception for the given message.
     *
     * @param type    the exception type
     * @param message message
     */
    public Up4Exception(Type type, String message) {
        super(message);
        this.type = type;
    }

}
