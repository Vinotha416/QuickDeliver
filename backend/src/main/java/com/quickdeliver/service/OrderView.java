package com.quickdeliver.service;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.quickdeliver.model.Order;

/**
 * JSON shape of an order: all order fields plus {@code user} / {@code agent}, which are either an id string
 * or a populated sub-object (see {@link OrderViewService}).
 */
public class OrderView {

    private final Order order;
    private final Object user;
    private final Object agent;

    public OrderView(Order order, Object user, Object agent) {
        this.order = order;
        this.user = user;
        this.agent = agent;
    }

    @JsonUnwrapped
    public Order getOrder() { return order; }

    public Object getUser() { return user; }

    public Object getAgent() { return agent; }
}
