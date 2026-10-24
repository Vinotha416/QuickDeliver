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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Port of routes/ai.js - the "smart" features. They are rule/statistics based (no ML), exactly as in the
 * Node version: slot suggestion from order history, simulated traffic/weather, customer analytics.
 */
@RestController
@RequestMapping("/api/ai")
@Auth
public class AiController {

    private static final List<String> SLOTS = List.of("Morning", "Afternoon", "Evening");
    private static final List<String> DAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
    private static final Map<String, String> SLOT_EMOJI = Map.of("Morning", "🌅", "Afternoon", "☀️", "Evening", "🌆");
    private static final List<String> WEATHER_CYCLE =
            List.of("clear", "cloudy", "light_rain", "clear", "clear", "clear", "cloudy"); // index 0 = Sunday
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH);

    private final OrderRepository orders;
    private final UserRepository users;
    private final ZoneId zone;

    public AiController(OrderRepository orders, UserRepository users, @Value("${app.zone:Asia/Kolkata}") String zone) {
        this.orders = orders;
        this.users = users;
        this.zone = ZoneId.of(zone);
    }

    // ------------------------------------------------------------------ smart slot suggestion
    @GetMapping("/suggest-slot")
    public ApiResponse suggestSlot(@RequestAttribute(AuthInterceptor.CURRENT_USER) User user) {
        List<Order> past = orders.findDeliveredWithSlot(Ids.oid(user.getId()),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<String, Integer> slotCount = new LinkedHashMap<>();
        for (String s : SLOTS) slotCount.put(s, 0);
        Map<String, Integer> dayCount = new LinkedHashMap<>();
        for (String d : DAYS) dayCount.put(d, 0);
        long totalTime = 0;
        int timeCount = 0;
        double valueSum = 0;

        for (Order o : past) {
            String slot = slotText(o);
            for (String s : SLOTS) {
                if (slot.contains(s)) slotCount.merge(s, 1, Integer::sum);
            }
            if (o.getTimeSlot() != null && o.getTimeSlot().getDate() != null) {
                String day = dayName(o.getTimeSlot().getDate());
                if (day != null && dayCount.containsKey(day)) dayCount.merge(day, 1, Integer::sum);
            }
            Integer t = o.getActualDeliveryTime();
            if (t != null && t != 0) {
                totalTime += t;
                timeCount++;
            }
            valueSum += o.getTotalAmount() == null ? 0 : o.getTotalAmount();
        }

        int avgOrderValue = past.isEmpty() ? 0 : (int) Math.round(valueSum / past.size());
        int avgTime = timeCount > 0 ? (int) Math.round((double) totalTime / timeCount) : 35;

        String bestSlot = "Morning";
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : slotCount.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                bestSlot = e.getKey();
            }
        }
        String bestDay = "Any";
        int bestDayCount = 0;
        for (Map.Entry<String, Integer> e : dayCount.entrySet()) {
            if (e.getValue() > bestDayCount) {
                bestDayCount = e.getValue();
                bestDay = e.getKey();
            }
        }

        int n = past.size();
        int confidence = n == 0 ? 60 : n < 3 ? 70 : n < 6 ? 80 : 92;

        String reason;
        if (n == 0) {
            reason = "No past orders found. Morning slot recommended as default for freshest delivery.";
            bestSlot = "Morning";
        } else {
            reason = "Based on your " + n + " past orders, you prefer " + SLOT_EMOJI.get(bestSlot) + " " + bestSlot
                    + " deliveries" + (!"Any".equals(bestDay) ? " on " + bestDay + "s" : "")
                    + ". Avg delivery time: " + avgTime + " min. Avg order: ₹" + avgOrderValue + ".";
        }

        // how busy is each slot today (all customers, cancelled orders excluded)
        Map<String, Integer> load = new LinkedHashMap<>();
        for (Order o : orders.findByTimeSlot_Date(today())) {
            if ("cancelled".equals(o.getStatus())) continue;
            load.merge(slotKey(slotText(o)), 1, Integer::sum);
        }

        List<Map<String, Object>> availability = new ArrayList<>();
        availability.add(slotAvailability("Morning", "7AM – 11AM", "🌅", load, bestSlot));
        availability.add(slotAvailability("Afternoon", "12PM – 4PM", "☀️", load, bestSlot));
        availability.add(slotAvailability("Evening", "5PM – 9PM", "🌆", load, bestSlot));

        return ApiResponse.ok().with("suggestion", ApiResponse.map()
                .with("bestSlot", bestSlot)
                .with("reason", reason)
                .with("confidence", confidence)
                .with("pastOrderCount", n)
                .with("avgDeliveryTime", avgTime)
                .with("avgOrderValue", avgOrderValue)
                .with("preferredDay", bestDay)
                .with("slotHistory", slotCount)
                .with("slotAvailability", availability)
                .with("generatedAt", Instant.now()));
    }

    private static Map<String, Object> slotAvailability(String slot, String time, String emoji,
                                                        Map<String, Integer> load, String bestSlot) {
        int l = load.getOrDefault(slot, 0);
        return ApiResponse.map()
                .with("slot", slot)
                .with("time", time)
                .with("emoji", emoji)
                .with("load", l)
                .with("recommended", slot.equals(bestSlot))
                .with("availability", l < 5 ? "Available" : l < 10 ? "Filling Up" : "Busy")
                .with("availabilityColor", l < 5 ? "green" : l < 10 ? "yellow" : "red");
    }

    // ------------------------------------------------------------------ traffic / weather / density
    @GetMapping("/route-info")
    public ApiResponse routeInfo() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        int hour = now.getHour();
        String traffic = (hour >= 8 && hour <= 10) ? "high"
                : (hour >= 17 && hour <= 20) ? "high"
                : (hour >= 11 && hour <= 16) ? "medium" : "low";

        Map<String, Object> trafficInfo;
        switch (traffic) {
            case "high":
                trafficInfo = info("label", "Heavy Traffic", "emoji", "🔴", "eta", 60, "desc", "Peak hours. Delivery may take longer.");
                break;
            case "medium":
                trafficInfo = info("label", "Moderate Traffic", "emoji", "🟡", "eta", 40, "desc", "Some congestion on main routes.");
                break;
            default:
                trafficInfo = info("label", "Light Traffic", "emoji", "🟢", "eta", 25, "desc", "Roads are clear. Fast delivery expected.");
                break;
        }

        // simulated weather: rotate by day of week (JS getDay(): Sunday = 0)
        String weather = WEATHER_CYCLE.get(now.getDayOfWeek().getValue() % 7);
        Map<String, Object> weatherInfo;
        switch (weather) {
            case "cloudy":
                weatherInfo = info("label", "Cloudy", "emoji", "⛅", "impact", "Minimal impact expected");
                break;
            case "light_rain":
                weatherInfo = info("label", "Light Rain", "emoji", "🌧️", "impact", "Add 10-15 min buffer time");
                break;
            default:
                weatherInfo = info("label", "Clear", "emoji", "☀️", "impact", "No impact on delivery");
                break;
        }

        // orders per slot today
        Map<String, Integer> density = new LinkedHashMap<>();
        for (Order o : orders.findByTimeSlot_Date(today())) {
            String slot = o.getTimeSlot() == null ? null : o.getTimeSlot().getSlot();
            density.merge(slot, 1, Integer::sum);
        }

        return ApiResponse.ok().with("routeInfo", ApiResponse.map()
                .with("traffic", trafficInfo)
                .with("trafficLevel", traffic)
                .with("weather", weatherInfo)
                .with("weatherCondition", weather)
                .with("deliveryDensity", groupList(density))
                .with("timestamp", Instant.now()));
    }

    // ------------------------------------------------------------------ customer analytics
    @GetMapping("/analytics")
    public ApiResponse analytics(@RequestAttribute(AuthInterceptor.CURRENT_USER) User user) {
        List<Order> all = orders.findByUser(Ids.oid(user.getId()), Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Order> delivered = all.stream().filter(o -> "delivered".equals(o.getStatus())).toList();
        long cancelled = all.stream().filter(o -> "cancelled".equals(o.getStatus())).count();
        long rescheduled = all.stream()
                .filter(o -> o.getRescheduleHistory() != null && !o.getRescheduleHistory().isEmpty()).count();

        Map<String, Integer> slotDist = new LinkedHashMap<>();
        for (String s : SLOTS) slotDist.put(s, 0);
        for (Order o : delivered) {
            String s = slotText(o);
            if (s.contains("Morning")) slotDist.merge("Morning", 1, Integer::sum);
            else if (s.contains("Afternoon")) slotDist.merge("Afternoon", 1, Integer::sum);
            else if (s.contains("Evening")) slotDist.merge("Evening", 1, Integer::sum);
        }

        double totalSpent = 0;
        double totalAll = 0;
        Map<String, Double> monthly = new LinkedHashMap<>();
        for (Order o : all) {
            double amount = o.getTotalAmount() == null ? 0 : o.getTotalAmount();
            totalAll += amount;
            if ("paid".equals(o.getPaymentStatus())) {
                totalSpent += amount;
                if (o.getCreatedAt() != null) {
                    monthly.merge(MONTH_LABEL.withZone(zone).format(o.getCreatedAt()), amount, Double::sum);
                }
            }
        }
        Map<String, Object> monthlySpend = new LinkedHashMap<>();
        monthly.forEach((k, v) -> monthlySpend.put(k, Num.clean(v)));

        double timeSum = 0;
        int timeN = 0;
        for (Order o : delivered) {
            if (o.getActualDeliveryTime() != null && o.getActualDeliveryTime() != 0) {
                timeSum += o.getActualDeliveryTime();
                timeN++;
            }
        }
        long avgTimeRounded = timeN == 0 ? 0 : Math.round(timeSum / timeN);

        Map<String, Integer> prodCount = new LinkedHashMap<>();
        for (Order o : all) {
            for (Order.Item i : o.getItems()) {
                if (i.getName() != null) {
                    prodCount.merge(i.getName(), i.getQuantity() == null ? 0 : i.getQuantity(), Integer::sum);
                }
            }
        }
        List<Object> topProducts = new ArrayList<>(prodCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> (Object) ApiResponse.map().with("name", e.getKey()).with("count", e.getValue()))
                .toList());

        int total = all.size();
        return ApiResponse.ok().with("analytics", ApiResponse.map()
                .with("totalOrders", total)
                .with("delivered", delivered.size())
                .with("cancelled", cancelled)
                .with("rescheduled", rescheduled)
                .with("totalSpent", Num.clean(totalSpent))
                .with("avgOrderValue", total == 0 ? 0 : Math.round(totalAll / total))
                .with("avgDeliveryTime", avgTimeRounded == 0 ? 35 : avgTimeRounded)
                .with("slotDistribution", slotDist)
                .with("monthlySpend", monthlySpend)
                .with("topProducts", topProducts)
                .with("successRate", total == 0 ? 0 : Math.round((double) delivered.size() / total * 100)));
    }

    // ------------------------------------------------------------------ admin system stats
    @GetMapping("/system-stats")
    public ApiResponse systemStats(@RequestAttribute(AuthInterceptor.CURRENT_USER) User user) {
        if (!Constants.ADMIN.equals(user.getRole())) {
            throw ApiException.forbidden("Admin only");
        }
        long totalOrders = orders.count();
        long delivered = orders.countByStatus("delivered");
        long cancelled = orders.countByStatus("cancelled");
        long rescheduled = orders.countRescheduled();
        long totalUsers = users.countByRole(Constants.CUSTOMER);
        long totalAgents = users.countByRole(Constants.AGENT);

        // paid revenue per month (UTC, like Mongo's $dateToString), oldest first, first 6 months
        TreeMap<String, double[]> byMonth = new TreeMap<>();
        for (Order o : orders.findByPaymentStatus("paid")) {
            if (o.getCreatedAt() == null) continue;
            double[] acc = byMonth.computeIfAbsent(MONTH_KEY.withZone(ZoneOffset.UTC).format(o.getCreatedAt()), k -> new double[2]);
            acc[0] += o.getTotalAmount() == null ? 0 : o.getTotalAmount();
            acc[1] += 1;
        }
        List<Object> revenueData = new ArrayList<>();
        for (Map.Entry<String, double[]> e : byMonth.entrySet()) {
            if (revenueData.size() == 6) break;
            revenueData.add(ApiResponse.map()
                    .with("_id", e.getKey())
                    .with("total", Num.clean(e.getValue()[0]))
                    .with("count", (long) e.getValue()[1]));
        }

        Map<String, Integer> slots = new LinkedHashMap<>();
        for (Order o : orders.findAllWithSlot()) {
            slots.merge(o.getTimeSlot() == null ? null : o.getTimeSlot().getSlot(), 1, Integer::sum);
        }

        return ApiResponse.ok().with("systemStats", ApiResponse.map()
                .with("totalOrders", totalOrders)
                .with("delivered", delivered)
                .with("cancelled", cancelled)
                .with("rescheduled", rescheduled)
                .with("totalUsers", totalUsers)
                .with("totalAgents", totalAgents)
                .with("successRate", totalOrders == 0 ? 0 : Math.round((double) delivered / totalOrders * 100))
                .with("revenueData", revenueData)
                .with("slotData", groupList(slots)));
    }

    // ------------------------------------------------------------------ helpers
    private String today() {
        return LocalDate.now(zone).toString();
    }

    private static String slotText(Order o) {
        return (o.getTimeSlot() != null && o.getTimeSlot().getSlot() != null) ? o.getTimeSlot().getSlot() : "";
    }

    /** Morning / Afternoon, anything else counts as Evening (as in the Node version). */
    private static String slotKey(String slot) {
        if (slot.contains("Morning")) return "Morning";
        if (slot.contains("Afternoon")) return "Afternoon";
        return "Evening";
    }

    private static String dayName(String isoDate) {
        try {
            return LocalDate.parse(isoDate).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** [{_id: key, count: n}, ...] - the shape Mongo's $group produced. */
    private static List<Object> groupList(Map<String, Integer> counts) {
        List<Object> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            list.add(ApiResponse.map().with("_id", e.getKey()).with("count", e.getValue()));
        }
        return list;
    }

    private static Map<String, Object> info(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
