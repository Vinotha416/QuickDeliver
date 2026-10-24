package com.quickdeliver.controller;

import com.quickdeliver.common.ApiException;
import com.quickdeliver.common.ApiResponse;
import com.quickdeliver.common.Constants;
import com.quickdeliver.common.Ids;
import com.quickdeliver.common.Num;
import com.quickdeliver.model.Order;
import com.quickdeliver.model.User;
import com.quickdeliver.repository.OrderRepository;
import com.quickdeliver.security.Auth;
import com.quickdeliver.security.AuthInterceptor;
import com.quickdeliver.service.OrderViewService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Port of routes/orders.js (customer-facing order endpoints). */
@RestController
@RequestMapping("/api/orders")
@Auth
public class OrderController {

    // ----- request bodies -----
    public record ItemRequest(String product, String name, Double price, Integer quantity, String image) { }

    public record TimeSlotRequest(String date, String slot) { }

    public record PaymentRequest(String transactionId, String upiId, String cardLast4) { }

    public record AiSlotRequest(String slot, String reason, Double confidence, Instant generatedAt) { }

    public record OrderRequest(List<ItemRequest> items, Double totalAmount, String deliveryAddress, String phone,
                               String email, TimeSlotRequest timeSlot, String paymentMethod,
                               PaymentRequest paymentDetails, AiSlotRequest aiSuggestedSlot,
                               String trafficLevel, String weatherCondition, Integer estimatedTime,
                               Double routeDistance) { }

    public record FeedbackRequest(Integer rating, String comment, Integer agentRating) { }

    private static final String BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final OrderViewService views;

    public OrderController(OrderRepository orders, OrderViewService views) {
        this.orders = orders;
        this.views = views;
    }

    /** Place order - customers only. */
    @PostMapping
    @Auth(roles = Constants.CUSTOMER)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse place(@RequestAttribute(AuthInterceptor.CURRENT_USER) User user,
                             @RequestBody OrderRequest req) {
        if (req.totalAmount() == null || Num.blank(req.deliveryAddress()) || Num.blank(req.phone()) || Num.blank(req.email())) {
            throw ApiException.badRequest("Total amount, delivery address, phone and email are required");
        }
        if (req.paymentMethod() == null || !Constants.PAYMENT_METHODS.contains(req.paymentMethod())) {
            throw ApiException.badRequest("Invalid payment method");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw ApiException.badRequest("Order must contain at least one item");
        }

        boolean cod = "cod".equals(req.paymentMethod());

        Order o = new Order();
        o.setOrderNumber("ORD-" + System.currentTimeMillis() + "-" + randomBase36(5));
        o.setUser(Ids.oid(user.getId()));
        o.setTotalAmount(req.totalAmount());
        o.setDeliveryAddress(req.deliveryAddress());
        o.setPhone(req.phone());
        o.setEmail(req.email());
        o.setPaymentMethod(req.paymentMethod());
        o.setPaymentStatus(cod ? "pending" : "paid");

        List<Order.Item> items = new ArrayList<>();
        for (ItemRequest ir : req.items()) {
            Order.Item item = new Order.Item();
            item.setProduct(Ids.valid(ir.product()) ? ir.product() : null);
            item.setName(ir.name());
            item.setPrice(ir.price());
            item.setQuantity(ir.quantity());
            item.setImage(ir.image());
            items.add(item);
        }
        o.setItems(items);

        if (req.timeSlot() != null) {
            Order.TimeSlot ts = new Order.TimeSlot();
            ts.setDate(req.timeSlot().date());
            ts.setSlot(req.timeSlot().slot());
            o.setTimeSlot(ts);
        }

        Order.PaymentDetails pd = new Order.PaymentDetails();
        if (req.paymentDetails() != null) {
            pd.setTransactionId(req.paymentDetails().transactionId());
            pd.setUpiId(req.paymentDetails().upiId());
            pd.setCardLast4(req.paymentDetails().cardLast4());
        }
        pd.setPaidAt(cod ? null : Instant.now());
        o.setPaymentDetails(pd);

        // AI / route context captured by the checkout page
        if (req.aiSuggestedSlot() != null) {
            Order.AiSuggestedSlot ai = new Order.AiSuggestedSlot();
            ai.setSlot(req.aiSuggestedSlot().slot());
            ai.setReason(req.aiSuggestedSlot().reason());
            ai.setConfidence(req.aiSuggestedSlot().confidence());
            ai.setGeneratedAt(req.aiSuggestedSlot().generatedAt());
            o.setAiSuggestedSlot(ai);
        }
        if (req.trafficLevel() != null && Constants.TRAFFIC_LEVELS.contains(req.trafficLevel())) {
            o.setTrafficLevel(req.trafficLevel());
        }
        if (!Num.blank(req.weatherCondition())) o.setWeatherCondition(req.weatherCondition());
        if (req.estimatedTime() != null) o.setEstimatedTime(req.estimatedTime());
        if (req.routeDistance() != null) o.setRouteDistance(req.routeDistance());

        o.getStatusHistory().add(new Order.StatusEntry("placed", "Order placed successfully", user.getId()));
        o.getNotifications().add(new Order.Notification("🎉 Your order has been placed successfully!", "status", "system"));

        return ApiResponse.ok().with("order", views.raw(orders.save(o)));
    }

    /** My orders (customer). */
    @GetMapping("/my")
    public ApiResponse myOrders(@RequestAttribute(AuthInterceptor.CURRENT_USER) User user) {
        List<Order> list = orders.findByUser(Ids.oid(user.getId()), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok().with("orders", views.populate(list, null, OrderViewService.NAME_PHONE));
    }

    /** My notifications: every notification of every order I own, newest first, max 50. */
    @GetMapping("/notifications/my")
    public ApiResponse myNotifications(@RequestAttribute(AuthInterceptor.CURRENT_USER) User user) {
        List<Order> list = orders.findByUser(Ids.oid(user.getId()), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> notifications = new ArrayList<>();
        for (Order o : list) {
            for (Order.Notification n : o.getNotifications()) {
                notifications.add(OrderViewService.notificationMap(n, o));
            }
        }
        notifications.sort(Comparator.comparing(
                (Map<String, Object> m) -> (Instant) m.get("sentAt"),
                Comparator.nullsLast(Comparator.<Instant>naturalOrder())).reversed());
        if (notifications.size() > 50) {
            notifications = new ArrayList<>(notifications.subList(0, 50));
        }
        return ApiResponse.ok().with("notifications", notifications);
    }

    @GetMapping("/{id}")
    public ApiResponse one(@RequestAttribute(AuthInterceptor.CURRENT_USER) User user, @PathVariable String id) {
        Order o = orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        // customers can only see their own orders
        if (Constants.CUSTOMER.equals(user.getRole())
                && (o.getUser() == null || !o.getUser().toHexString().equals(user.getId()))) {
            throw ApiException.forbidden("Access denied");
        }
        return ApiResponse.ok().with("order",
                views.populate(o, OrderViewService.NAME_EMAIL_PHONE, OrderViewService.NAME_PHONE_STATUS));
    }

    /** Feedback - customers only, once, after delivery. */
    @PostMapping("/{id}/feedback")
    @Auth(roles = Constants.CUSTOMER)
    public ApiResponse feedback(@RequestAttribute(AuthInterceptor.CURRENT_USER) User user,
                                @PathVariable String id, @RequestBody FeedbackRequest body) {
        Order o = orders.findById(id).orElseThrow(() -> ApiException.notFound("Not found"));
        if (o.getUser() == null || !o.getUser().toHexString().equals(user.getId())) {
            throw ApiException.forbidden("Access denied");
        }
        if (!"delivered".equals(o.getStatus())) {
            throw ApiException.badRequest("Feedback only available after delivery");
        }
        if (o.getFeedback() != null && o.getFeedback().getRating() != null && o.getFeedback().getRating() > 0) {
            throw ApiException.badRequest("Feedback already submitted");
        }
        if (body.rating() == null || body.rating() < 1 || body.rating() > 5) {
            throw ApiException.badRequest("Rating must be between 1 and 5");
        }
        if (body.agentRating() != null && (body.agentRating() < 0 || body.agentRating() > 5)) {
            throw ApiException.badRequest("Agent rating must be between 0 and 5");
        }

        Order.Feedback fb = new Order.Feedback();
        fb.setRating(body.rating());
        fb.setComment(body.comment());
        fb.setAgentRating(body.agentRating());
        fb.setSubmittedAt(Instant.now());
        o.setFeedback(fb);
        orders.save(o);
        return ApiResponse.ok().with("message", "Thank you for your feedback!");
    }

    private static String randomBase36(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE36.charAt(RANDOM.nextInt(BASE36.length())));
        }
        return sb.toString();
    }
}
