package com.quickdeliver.service;

import com.quickdeliver.model.Order;
import com.quickdeliver.model.User;
import com.quickdeliver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Java stand-in for Mongoose {@code .populate('user','name email')}. */
@Service
public class OrderViewService {

    public static final List<String> NAME = List.of("name");
    public static final List<String> NAME_EMAIL = List.of("name", "email");
    public static final List<String> NAME_PHONE = List.of("name", "phone");
    public static final List<String> NAME_PHONE_STATUS = List.of("name", "phone", "agentStatus");
    public static final List<String> NAME_EMAIL_PHONE = List.of("name", "email", "phone");

    private final UserRepository users;

    public OrderViewService(UserRepository users) {
        this.users = users;
    }

    /** user/agent left as plain id strings (what Mongoose returns without populate). */
    public OrderView raw(Order order) {
        return view(order, new HashMap<>(), null, null);
    }

    public OrderView populate(Order order, List<String> userFields, List<String> agentFields) {
        return view(order, usersFor(List.of(order)), userFields, agentFields);
    }

    public List<OrderView> populate(List<Order> orders, List<String> userFields, List<String> agentFields) {
        Map<String, User> lookup = usersFor(orders);
        return orders.stream().map(o -> view(o, lookup, userFields, agentFields)).toList();
    }

    /** One query for every user/agent referenced by the given orders, keyed by id. */
    public Map<String, User> usersFor(Collection<Order> orders) {
        Set<String> ids = new LinkedHashSet<>();
        for (Order o : orders) {
            if (o.getUser() != null) ids.add(o.getUser().toHexString());
            if (o.getAgent() != null) ids.add(o.getAgent().toHexString());
        }
        Map<String, User> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (User u : users.findAllById(ids)) {
                map.put(u.getId(), u);
            }
        }
        return map;
    }

    /** Null fields = leave the reference as an id string. */
    private OrderView view(Order o, Map<String, User> lookup, List<String> userFields, List<String> agentFields) {
        Object user = null;
        if (o.getUser() != null) {
            String id = o.getUser().toHexString();
            user = (userFields == null) ? id : mini(lookup.get(id), userFields);
        }
        Object agent = null;
        if (o.getAgent() != null) {
            String id = o.getAgent().toHexString();
            agent = (agentFields == null) ? id : mini(lookup.get(id), agentFields);
        }
        return new OrderView(o, user, agent);
    }

    private static Map<String, Object> mini(User u, List<String> fields) {
        if (u == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", u.getId());
        for (String f : fields) {
            switch (f) {
                case "name": m.put("name", u.getName()); break;
                case "email": m.put("email", u.getEmail()); break;
                case "phone": m.put("phone", u.getPhone()); break;
                case "agentStatus": m.put("agentStatus", u.getAgentStatus()); break;
                default: break;
            }
        }
        return m;
    }

    /** A notification as the frontend expects it, optionally enriched with the order it belongs to. */
    public static Map<String, Object> notificationMap(Order.Notification n, Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", n.getMessage());
        m.put("type", n.getType());
        m.put("sentBy", n.getSentBy());
        m.put("isRead", n.getIsRead());
        m.put("sentAt", n.getSentAt());
        if (o != null) {
            m.put("orderNumber", o.getOrderNumber());
            m.put("orderId", o.getId());
        }
        return m;
    }
}
