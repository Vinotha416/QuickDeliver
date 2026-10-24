package com.quickdeliver.common;

import java.util.LinkedHashMap;

/** Small fluent JSON object: ApiResponse.ok().with("order", order). */
public class ApiResponse extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    public static ApiResponse ok() {
        ApiResponse r = new ApiResponse();
        r.put("success", true);
        return r;
    }

    public static ApiResponse fail(String message) {
        ApiResponse r = new ApiResponse();
        r.put("success", false);
        r.put("message", message);
        return r;
    }

    /** A plain JSON object without the "success" flag. */
    public static ApiResponse map() {
        return new ApiResponse();
    }

    public ApiResponse with(String key, Object value) {
        put(key, value);
        return this;
    }
}
