package com.quickdeliver.controller;

import com.quickdeliver.common.ApiException;
import com.quickdeliver.common.ApiResponse;
import com.quickdeliver.common.Constants;
import com.quickdeliver.common.Ids;
import com.quickdeliver.common.Num;
import com.quickdeliver.model.Order;
import com.quickdeliver.model.User;
import com.quickdeliver.repository.OrderRepository;
import com.quickdeliver.repository.UserRepository;
import com.quickdeliver.security.Auth;
import com.quickdeliver.security.AuthInterceptor;
import com.quickdeliver.service.OrderViewService;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Port of routes/agent.js - every endpoint requires the agent role. */
@RestController
@RequestMapping("/api/agent")
@Auth(roles = Constants.AGENT)
public class AgentController {

    public record StatusRequest(String status, String note) { }

    public record AlertRequest(String type, String message) { }

    public record RescheduleRequest(String newDate, String newSlot, String reason) { }

    public record AvailabilityRequest(String agentStatus) { }

    private static final List<String> ALERT_TYPES = List.of("weather", "traffic", "delay", "general");
    private static final Map<String, String> ALERT_ICONS = Map.of(
            "weather", "🌧️", "traffic", "🚦", "delay", "⏰", "general", "📢");
    private static final Map<String, String> STATUS_MESSAGES = Map.of(
            "picked_up", "🚴 Your order has been picked up and is on its way!",
            "in_transit", "📦 Your order is in transit!",
            "out_for_delivery", "🏃 Agent is nearby — delivery very soon!",
            "delivered", "✅ Your order has been delivered! Leave feedback ⭐");

    private final OrderRepository orders;
    private final UserRepository users;
    private final OrderViewService views;

    public AgentController(OrderRepository orders, UserRepository users, OrderViewService views) {
        this.orders = orders;
        this.users = users;
        this.views = views;
    }

    /** Orders assigned to me. */
    @GetMapping("/orders")
    public ApiResponse myOrders(@RequestAttribute(AuthInterceptor.CURRENT_USER) User agent) {
        List<Order> list = orders.findByAgent(Ids.oid(agent.getId()), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok().with("orders", views.populate(list, OrderViewService.NAME_EMAIL_PHONE, null));
    }

    /** Update delivery status (picked up / in transit / out for delivery / delivered). */
    @PutMapping("/orders/{id}/status")
    public ApiResponse updateStatus(@RequestAttribute(AuthInterceptor.CURRENT_USER) User agent,
                                    @PathVariable String id, @RequestBody StatusRequest body) {
        String status = body.status();
        if (status == null || !Constants.AGENT_ALLOWED_STATUSES.contains(status)) {
            throw ApiException.badRequest("Invalid status for agent");
        }
        Order order = assignedOrder(id, agent, "Order not found");

        order.setStatus(status);
        order.getStatusHistory().add(new Order.StatusEntry(
                status, Num.blank(body.note()) ? "Updated to " + status : body.note(), agent.getId()));
        order.getNotifications().add(new Order.Notification(STATUS_MESSAGES.get(status), "status", "agent"));
        if ("delivered".equals(status)) order.setDeliveredAt(Instant.now());
        return ApiResponse.ok().with("order", views.raw(orders.save(order)));
    }

    /** Agent sends an alert (weather / traffic / delay / general) to the customer. */
    @PostMapping("/orders/{id}/alert")
    public ApiResponse sendAlert(@RequestAttribute(AuthInterceptor.CURRENT_USER) User agent,
                                 @PathVariable String id, @RequestBody AlertRequest body) {
        if (body.type() == null || !ALERT_TYPES.contains(body.type())) {
            throw ApiException.badRequest("Invalid alert type");
        }
        if (Num.blank(body.message())) {
            throw ApiException.badRequest("Message is required");
        }
        Order order = assignedOrder(id, agent, "Order not assigned to you");
        if ("delivered".equals(order.getStatus()) || "cancelled".equals(order.getStatus())) {
            throw ApiException.badRequest("Cannot alert on completed order");
        }
        order.getNotifications().add(new Order.Notification(
                ALERT_ICONS.get(body.type()) + " " + body.message().trim(), body.type(), "agent"));
        orders.save(order);
        return ApiResponse.ok().with("message", "Alert sent to customer successfully");
    }

    /** Agent reschedules the delivery. */
    @PostMapping("/orders/{id}/reschedule")
    public ApiResponse reschedule(@RequestAttribute(AuthInterceptor.CURRENT_USER) User agent,
                                  @PathVariable String id, @RequestBody RescheduleRequest body) {
        if (Num.blank(body.newDate()) || Num.blank(body.newSlot()) || Num.blank(body.reason())) {
            throw ApiException.badRequest("Date, slot and reason required");
        }
        Order order = assignedOrder(id, agent, "Order not found");
        if ("delivered".equals(order.getStatus()) || "cancelled".equals(order.getStatus())) {
            throw ApiException.badRequest("Cannot reschedule this order");
        }

        Order.RescheduleEntry entry = new Order.RescheduleEntry();
        if (order.getTimeSlot() != null) {
            entry.setOldDate(order.getTimeSlot().getDate());
            entry.setOldSlot(order.getTimeSlot().getSlot());
        }
        entry.setNewDate(body.newDate());
        entry.setNewSlot(body.newSlot());
        entry.setReason(body.reason());
        entry.setReschedBy(agent.getId());
        order.getRescheduleHistory().add(entry);

        Order.TimeSlot slot = new Order.TimeSlot();
        slot.setDate(body.newDate());
        slot.setSlot(body.newSlot());
        order.setTimeSlot(slot);
        order.setStatus("rescheduled");

        order.getStatusHistory().add(new Order.StatusEntry("rescheduled",
                "Rescheduled to " + body.newDate() + " – " + body.newSlot() + ". Reason: " + body.reason(), agent.getId()));
        order.getNotifications().add(new Order.Notification(
                "🔄 Delivery rescheduled to " + body.newDate() + " – " + body.newSlot() + ". Reason: " + body.reason(),
                "reschedule", "agent"));
        return ApiResponse.ok().with("order", views.raw(orders.save(order)));
    }

    /** Update my availability (available / busy / offline). */
    @PutMapping("/my-status")
    public ApiResponse myStatus(@RequestAttribute(AuthInterceptor.CURRENT_USER) User current,
                                @RequestBody AvailabilityRequest body) {
        if (body.agentStatus() == null || !Constants.AGENT_STATUSES.contains(body.agentStatus())) {
            throw ApiException.badRequest("Invalid agent status");
        }
        User user = users.findById(current.getId()).orElseThrow(() -> ApiException.unauthorized("Account not found"));
        user.setAgentStatus(body.agentStatus());
        users.save(user);
        return ApiResponse.ok();
    }

    /** Order.findOne({ _id, agent: me }) */
    private Order assignedOrder(String id, User agent, String notFoundMessage) {
        ObjectId agentId = Ids.oid(agent.getId());
        return orders.findByIdAndAgent(id, agentId).orElseThrow(() -> ApiException.notFound(notFoundMessage));
    }
}
