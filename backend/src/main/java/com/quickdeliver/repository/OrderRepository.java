package com.quickdeliver.repository;

import com.quickdeliver.model.Order;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByUser(ObjectId user, Sort sort);

    List<Order> findByAgent(ObjectId agent, Sort sort);

    /** Order.findOne({ _id, agent }) - an agent may only touch orders assigned to them. */
    Optional<Order> findByIdAndAgent(String id, ObjectId agent);

    List<Order> findByStatus(String status, Sort sort);

    long countByStatus(String status);

    long countByStatusIn(Collection<String> statuses);

    List<Order> findByPaymentStatus(String paymentStatus);

    List<Order> findTop8ByOrderByCreatedAtDesc();

    /** Orders whose delivery slot is on the given yyyy-MM-dd date. */
    List<Order> findByTimeSlot_Date(String date);

    @Query("{ 'feedback.rating': { $exists: true } }")
    List<Order> findAllWithFeedback(Sort sort);

    @Query("{ 'feedback.rating': { $exists: true } }")
    List<Order> findTopWithFeedback(Pageable pageable);

    @Query("{ 'notifications.0': { $exists: true } }")
    List<Order> findAllWithNotifications(Sort sort);

    @Query("{ 'user': ?0, 'status': 'delivered', 'timeSlot.slot': { $exists: true } }")
    List<Order> findDeliveredWithSlot(ObjectId user, Pageable pageable);

    @Query("{ 'timeSlot.slot': { $exists: true } }")
    List<Order> findAllWithSlot();

    @Query(value = "{ 'rescheduleHistory.0': { $exists: true } }", count = true)
    long countRescheduled();
}
