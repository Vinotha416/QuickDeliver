package com.quickdeliver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the Mongoose "Order" model (collection: orders).
 * <p>{@code user} and {@code agent} are stored as ObjectIds (exactly as Mongoose did) and are
 * never serialised directly - {@link com.quickdeliver.service.OrderView} emits them either as an id string
 * or as a "populated" sub-object, like Mongoose's {@code populate()}.</p>
 */
@Document(collection = "orders")
public class Order {

    @Id
    @JsonProperty("_id")
    private String id;

    @Indexed(unique = true, name = "orderNumber_1")
    private String orderNumber;

    @JsonIgnore
    private ObjectId user;

    @JsonIgnore
    private ObjectId agent;

    private List<Item> items = new ArrayList<>();

    private Double totalAmount;

    private String deliveryAddress;

    private String phone;

    private String email;

    private TimeSlot timeSlot;

    private String paymentMethod;

    private String paymentStatus = "pending";

    private PaymentDetails paymentDetails;

    private String status = "placed";

    private List<StatusEntry> statusHistory = new ArrayList<>();

    private List<Notification> notifications = new ArrayList<>();

    private List<RescheduleEntry> rescheduleHistory = new ArrayList<>();

    private Feedback feedback;

    private Instant deliveredAt;

    private AiSuggestedSlot aiSuggestedSlot;

    private DeliveryDensity deliveryDensity;

    private String weatherCondition = "clear";

    private String trafficLevel = "low";

    private Double routeDistance = 0.0;

    private Integer estimatedTime = 30;

    private Integer actualDeliveryTime;

    private CustomerPreference customerPreference;

    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public ObjectId getUser() { return user; }
    public void setUser(ObjectId user) { this.user = user; }

    public ObjectId getAgent() { return agent; }
    public void setAgent(ObjectId agent) { this.agent = agent; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public TimeSlot getTimeSlot() { return timeSlot; }
    public void setTimeSlot(TimeSlot timeSlot) { this.timeSlot = timeSlot; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public PaymentDetails getPaymentDetails() { return paymentDetails; }
    public void setPaymentDetails(PaymentDetails paymentDetails) { this.paymentDetails = paymentDetails; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<StatusEntry> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<StatusEntry> statusHistory) { this.statusHistory = statusHistory; }

    public List<Notification> getNotifications() { return notifications; }
    public void setNotifications(List<Notification> notifications) { this.notifications = notifications; }

    public List<RescheduleEntry> getRescheduleHistory() { return rescheduleHistory; }
    public void setRescheduleHistory(List<RescheduleEntry> rescheduleHistory) { this.rescheduleHistory = rescheduleHistory; }

    public Feedback getFeedback() { return feedback; }
    public void setFeedback(Feedback feedback) { this.feedback = feedback; }

    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }

    public AiSuggestedSlot getAiSuggestedSlot() { return aiSuggestedSlot; }
    public void setAiSuggestedSlot(AiSuggestedSlot aiSuggestedSlot) { this.aiSuggestedSlot = aiSuggestedSlot; }

    public DeliveryDensity getDeliveryDensity() { return deliveryDensity; }
    public void setDeliveryDensity(DeliveryDensity deliveryDensity) { this.deliveryDensity = deliveryDensity; }

    public String getWeatherCondition() { return weatherCondition; }
    public void setWeatherCondition(String weatherCondition) { this.weatherCondition = weatherCondition; }

    public String getTrafficLevel() { return trafficLevel; }
    public void setTrafficLevel(String trafficLevel) { this.trafficLevel = trafficLevel; }

    public Double getRouteDistance() { return routeDistance; }
    public void setRouteDistance(Double routeDistance) { this.routeDistance = routeDistance; }

    public Integer getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(Integer estimatedTime) { this.estimatedTime = estimatedTime; }

    public Integer getActualDeliveryTime() { return actualDeliveryTime; }
    public void setActualDeliveryTime(Integer actualDeliveryTime) { this.actualDeliveryTime = actualDeliveryTime; }

    public CustomerPreference getCustomerPreference() { return customerPreference; }
    public void setCustomerPreference(CustomerPreference customerPreference) { this.customerPreference = customerPreference; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }


    // ---------- embedded documents ----------

    public static class Item {

        @Field(targetType = FieldType.OBJECT_ID)
        private String product;

        private String name;

        private Double price;

        private Integer quantity;

        private String image;

        public String getProduct() { return product; }
        public void setProduct(String product) { this.product = product; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }

    }

    public static class TimeSlot {

        private String date;

        private String slot;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public String getSlot() { return slot; }
        public void setSlot(String slot) { this.slot = slot; }

    }

    public static class PaymentDetails {

        private String transactionId;

        private Instant paidAt;

        private String upiId;

        private String cardLast4;

        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

        public Instant getPaidAt() { return paidAt; }
        public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

        public String getUpiId() { return upiId; }
        public void setUpiId(String upiId) { this.upiId = upiId; }

        public String getCardLast4() { return cardLast4; }
        public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }

    }

    public static class Notification {

        private String message;

        private String type = "status";

        private String sentBy = "system";

        private Boolean isRead = false;

        private Instant sentAt = Instant.now();

        public Notification() { }

        public Notification(String message, String type, String sentBy) {
            this.message = message;
            this.type = type;
            this.sentBy = sentBy;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getSentBy() { return sentBy; }
        public void setSentBy(String sentBy) { this.sentBy = sentBy; }

        public Boolean getIsRead() { return isRead; }
        public void setIsRead(Boolean isRead) { this.isRead = isRead; }

        public Instant getSentAt() { return sentAt; }
        public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    }

    public static class StatusEntry {

        private String status;

        private String note;

        @Field(targetType = FieldType.OBJECT_ID)
        private String updatedBy;

        private Instant timestamp = Instant.now();

        public StatusEntry() { }

        public StatusEntry(String status, String note, String updatedBy) {
            this.status = status;
            this.note = note;
            this.updatedBy = updatedBy;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }

        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    }

    public static class RescheduleEntry {

        private String oldDate;

        private String oldSlot;

        private String newDate;

        private String newSlot;

        private String reason;

        @Field(targetType = FieldType.OBJECT_ID)
        private String reschedBy;

        private Instant at = Instant.now();

        public String getOldDate() { return oldDate; }
        public void setOldDate(String oldDate) { this.oldDate = oldDate; }

        public String getOldSlot() { return oldSlot; }
        public void setOldSlot(String oldSlot) { this.oldSlot = oldSlot; }

        public String getNewDate() { return newDate; }
        public void setNewDate(String newDate) { this.newDate = newDate; }

        public String getNewSlot() { return newSlot; }
        public void setNewSlot(String newSlot) { this.newSlot = newSlot; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getReschedBy() { return reschedBy; }
        public void setReschedBy(String reschedBy) { this.reschedBy = reschedBy; }

        public Instant getAt() { return at; }
        public void setAt(Instant at) { this.at = at; }

    }

    public static class Feedback {

        private Integer rating;

        private String comment;

        private Integer agentRating;

        private Instant submittedAt;

        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }

        public Integer getAgentRating() { return agentRating; }
        public void setAgentRating(Integer agentRating) { this.agentRating = agentRating; }

        public Instant getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    }

    public static class AiSuggestedSlot {

        private String slot;

        private String reason;

        private Double confidence;

        private Instant generatedAt;

        public String getSlot() { return slot; }
        public void setSlot(String slot) { this.slot = slot; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }

        public Instant getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    }

    public static class DeliveryDensity {

        private String date;

        private String slot;

        private Integer count;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public String getSlot() { return slot; }
        public void setSlot(String slot) { this.slot = slot; }

        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }

    }

    public static class CustomerPreference {

        private String preferredSlot;

        private List<String> preferredDays;

        private Double avgOrderValue;

        public String getPreferredSlot() { return preferredSlot; }
        public void setPreferredSlot(String preferredSlot) { this.preferredSlot = preferredSlot; }

        public List<String> getPreferredDays() { return preferredDays; }
        public void setPreferredDays(List<String> preferredDays) { this.preferredDays = preferredDays; }

        public Double getAvgOrderValue() { return avgOrderValue; }
        public void setAvgOrderValue(Double avgOrderValue) { this.avgOrderValue = avgOrderValue; }

    }
}
