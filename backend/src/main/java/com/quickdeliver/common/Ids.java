package com.quickdeliver.common;

import org.bson.types.ObjectId;

public final class Ids {
    private Ids() { }

    public static ObjectId oid(String hex) {
        if (hex == null || !ObjectId.isValid(hex)) {
            throw ApiException.badRequest("Invalid id");
        }
        return new ObjectId(hex);
    }

    public static boolean valid(String hex) {
        return hex != null && ObjectId.isValid(hex);
    }
}
