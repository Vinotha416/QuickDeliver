package com.quickdeliver.repository;

import com.quickdeliver.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    long countByRole(String role);

    List<User> findByRole(String role);
}
