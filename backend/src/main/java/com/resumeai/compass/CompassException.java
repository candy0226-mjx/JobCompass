package com.resumeai.compass;

public class CompassException extends RuntimeException {
    public final int status;
    public final String code;
    public CompassException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public static CompassException bad(String message) { return new CompassException(400, "INVALID_INPUT", message); }
    public static CompassException missing(String message) { return new CompassException(404, "NOT_FOUND", message); }
    public static CompassException conflict(String message) { return new CompassException(409, "CONFLICT", message); }
}
