package com.quickdeliver.controller;

import com.quickdeliver.common.ApiException;
import com.quickdeliver.common.ApiResponse;
import com.quickdeliver.common.Constants;
import com.quickdeliver.common.Ids;
import com.quickdeliver.common.Num;
import com.quickdeliver.model.Order;
import com.quickdeliver.model.User;
import com.quickdeliver.repository.OrderRepository;
import com.quickdeliver.repository.ProductRepository;
import com.quickdeliver.repository.UserRepository;
import com.quickdeliver.security.Auth;
import com.quickdeliver.security.AuthInterceptor;
import com.quickdeliver.service.OrderViewService;
import com.quickdeliver.service.ProductService;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Port of routes/admin.js - every endpoint requires the admin role. */
@RestController
@RequestMapping("/api/admin")
@Auth(roles = Constants.ADMIN)
public class AdminController {

    public record StatusRequest(String status, String note) { }

    public record AssignRequest(String agentId) { }

    private static final Set<String> ALERT_TYPES = Set.of("weather", "traffic", "delay", "reschedule", "general");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final UserRepository users;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final OrderViewService views;
    private final ProductService productService;
    private final MongoTemplate mongo;

    public AdminController(UserRepository users, OrderRepository orders, ProductRepository products,
                           OrderViewService views, ProductService productService, MongoTemplate mongo) {
        this.users = users;
        this.orders = orders;
        this.products = products;
        this.views = views;
        this.productService = productService;
        this.mongo = mongo;
    }

    // ------------------------------------------------------------------ dashboard
    @GetMapping("/dashboard")
    public ApiResponse dashboard() {
        ApiResponse stats = ApiResponse.map()
                .with("totalOrders", orders.count())
                .with("pendingOrders", orders.countByStatusIn(Constants.PENDING_STATUSES))
                .with("deliveredOrders", orders.countByStatus("delivered"))
                .with("totalCustomers", users.countByRole(Constants.CUSTOMER))
                .with("totalAgents", users.countByRole(Constants.AGENT))
                .with("totalProducts", products.count())
                .with("revenue", Num.clean(paidRevenue()));

        List<Order> recent = orders.findTop8ByOrderByCreatedAtDesc();
        List<Order> recentFeedback = orders.findTopWithFeedback(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "feedback.submittedAt")));

        return ApiResponse.ok()
                .with("stats", stats)
                .with("recentOrders", views.populate(recent, OrderViewService.NAME_EMAIL, OrderViewService.NAME))
                .with("recentFeedback", views.populate(recentFeedback, OrderViewService.NAME, OrderViewService.NAME));
    }

    /** SUM(totalAmount) over paid orders - the Mongo $group stage from the Node version. */
    private double paidRevenue() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("paymentStatus").is("paid")),
                Aggregation.group().sum("totalAmount").as("total"));
        AggregationResults<Document> result = mongo.aggregate(agg, "orders", Document.class);
        Document doc = result.getUniqueMappedResult();
        if (doc == null) return 0;
        Object total = doc.get("total");
        return total instanceof Number n ? n.doubleValue() : 0;
    }

    // ------------------------------------------------------------------ orders
    @GetMapping("/orders")
    public ApiResponse allOrders(@RequestParam(required = false) String status) {
        List<Order> list = (status != null && !status.isEmpty() && !"all".equals(status))
                ? orders.findByStatus(status, NEWEST_FIRST)
                : orders.findAll(NEWEST_FIRST);
        return ApiResponse.ok().with("orders",
                views.populate(list, OrderViewService.NAME_EMAIL_PHONE, OrderViewService.NAME_PHONE));
    }

    @PutMapping("/orders/{id}/status")
    public ApiResponse updateStatus(@RequestAttribute(AuthInterceptor.CURRENT_USER) User admin,
                                    @PathVariable String id, @RequestBody StatusRequest body) {
        Order order = orders.findById(id).orElseThrow(() -> ApiException.notFound("Not found"));
        String status = body.status();
        if (status == null || !Constants.ORDER_STATUSES.contains(status)) {
            throw ApiException.badRequest("Invalid status");
        }
        String note = Num.blank(body.note()) ? "Status updated to " + status : body.note();

        order.setStatus(status);
        order.getStatusHistory().add(new Order.StatusEntry(status, note, admin.getId()));
        order.getNotifications().add(new Order.Notification(
                "Your order " + order.getOrderNumber() + " status: " + status.replace('_', ' '), "status", "admin"));
        if ("delivered".equals(status)) order.setDeliveredAt(Instant.now());
        return ApiResponse.ok().with("order", views.raw(orders.save(order)));
    }

    @PutMapping("/orders/{id}/assign")
    public ApiResponse assign(@RequestAttribute(AuthInterceptor.CURRENT_USER) User admin,
                              @PathVariable String id, @RequestBody AssignRequest body) {
        Order order = orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        User agent = Ids.valid(body.agentId()) ? users.findById(body.agentId()).orElse(null) : null;
        if (agent == null || !Constants.AGENT.equals(agent.getRole())) {
            throw ApiException.badRequest("Invalid agent");
        }
        order.setAgent(Ids.oid(agent.getId()));
        order.setStatus("assigned");
        order.getStatusHistory().add(new Order.StatusEntry("assigned", "Assigned to " + agent.getName(), admin.getId()));
        order.getNotifications().add(new Order.Notification(
                "Agent " + agent.getName() + " will deliver your order " + order.getOrderNumber(), "status", "admin"));
        return ApiResponse.ok().with("order", views.raw(orders.save(order)));
    }

    // ------------------------------------------------------------------ read-only lists
    /** Alerts sent by agents (weather / traffic / delay / ...) - read-only for the admin. */
    @GetMapping("/agent-alerts")
    public ApiResponse agentAlerts() {
        List<Order> list = orders.findAllWithNotifications(NEWEST_FIRST);
        Map<String, User> lookup = views.usersFor(list);

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Order o : list) {
            User customer = o.getUser() == null ? null : lookup.get(o.getUser().toHexString());
            User agent = o.getAgent() == null ? null : lookup.get(o.getAgent().toHexString());
            for (Order.Notification n : o.getNotifications()) {
                if (!ALERT_TYPES.contains(n.getType()) || !"agent".equals(n.getSentBy())) continue;
                Map<String, Object> alert = OrderViewService.notificationMap(n, o);
                alert.put("customerName", customer == null ? null : customer.getName());
                alert.put("customerEmail", customer == null ? null : customer.getEmail());
                alert.put("agentName", agent != null && !Num.blank(agent.getName()) ? agent.getName() : "Unknown Agent");
                alerts.add(alert);
            }
        }
        alerts.sort(Comparator.comparing(
                (Map<String, Object> m) -> (Instant) m.get("sentAt"),
                Comparator.nullsLast(Comparator.<Instant>naturalOrder())).reversed());
        return ApiResponse.ok().with("alerts", alerts);
    }

    @GetMapping("/agents")
    public ApiResponse agents() {
        return ApiResponse.ok().with("agents", users.findByRole(Constants.AGENT));
    }

    @GetMapping("/customers")
    public ApiResponse customers() {
        return ApiResponse.ok().with("customers", users.findByRole(Constants.CUSTOMER));
    }

    @GetMapping("/feedback")
    public ApiResponse feedback() {
        List<Order> list = orders.findAllWithFeedback(Sort.by(Sort.Direction.DESC, "feedback.submittedAt"));
        return ApiResponse.ok().with("orders", views.populate(list, OrderViewService.NAME_EMAIL, OrderViewService.NAME));
    }

    @GetMapping("/payments")
    public ApiResponse payments() {
        List<Order> list = orders.findAll(NEWEST_FIRST);
        return ApiResponse.ok().with("orders", views.populate(list, OrderViewService.NAME_EMAIL, null));
    }

    // ------------------------------------------------------------------ product management
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse createProduct(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok().with("product", productService.create(body));
    }

    @PutMapping("/products/{id}")
    public ApiResponse updateProduct(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().with("product", productService.update(id, body));
    }

    @DeleteMapping("/products/{id}")
    public ApiResponse deleteProduct(@PathVariable String id) {
        productService.delete(id);
        return ApiResponse.ok();
    }
}
