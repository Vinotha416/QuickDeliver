package com.quickdeliver.common;

import java.util.List;

public final class Constants {
    private Constants() { }

    public static final String CUSTOMER = "customer";
    public static final String ADMIN = "admin";
    public static final String AGENT = "agent";

    public static final List<String> ORDER_STATUSES = List.of(
            "placed", "confirmed", "assigned", "picked_up", "in_transit",
            "out_for_delivery", "delivered", "cancelled", "rescheduled");

    /** Orders that still count as "pending" on the admin dashboard. */
    public static final List<String> PENDING_STATUSES = List.of(
            "placed", "confirmed", "assigned", "picked_up", "in_transit", "out_for_delivery");

    public static final List<String> AGENT_ALLOWED_STATUSES = List.of(
            "picked_up", "in_transit", "out_for_delivery", "delivered");

    public static final List<String> PAYMENT_METHODS = List.of("upi", "credit_card", "gpay", "cod");
    public static final List<String> AGENT_STATUSES = List.of("available", "busy", "offline");
    public static final List<String> TRAFFIC_LEVELS = List.of("low", "medium", "high");
}
