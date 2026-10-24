package com.quickdeliver.common;

/** Thrown by controllers; rendered as {"success":false,"message":...} with the given HTTP status. */
public class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }

    public static ApiException badRequest(String message)   { return new ApiException(400, message); }
    public static ApiException unauthorized(String message) { return new ApiException(401, message); }
    public static ApiException forbidden(String message)    { return new ApiException(403, message); }
    public static ApiException notFound(String message)     { return new ApiException(404, message); }
}
