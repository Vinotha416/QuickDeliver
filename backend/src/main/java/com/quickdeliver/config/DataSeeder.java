package com.quickdeliver.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickdeliver.model.Product;
import com.quickdeliver.model.User;
import com.quickdeliver.repository.ProductRepository;
import com.quickdeliver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Port of seed.js. Run it with the "seed" profile:
 * <pre>mvn spring-boot:run -Dspring-boot.run.profiles=seed</pre>
 * Creates the admin account (from ADMIN_EMAIL / ADMIN_PASSWORD), the sample delivery agents and replaces
 * all products with the sample catalogue from src/main/resources/seed/. Then the process exits.
 */
@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    public record SeedAgent(String name, String email, String password, String phone, String agentStatus) { }

    private final UserRepository users;
    private final ProductRepository products;
    private final PasswordEncoder encoder;
    private final ObjectMapper mapper;
    private final ApplicationContext context;
    private final String adminEmail;
    private final String adminPassword;

    public DataSeeder(UserRepository users, ProductRepository products, PasswordEncoder encoder,
                      ObjectMapper mapper, ApplicationContext context,
                      @Value("${app.admin.email:}") String adminEmail,
                      @Value("${app.admin.password:}") String adminPassword) {
        this.users = users;
        this.products = products;
        this.encoder = encoder;
        this.mapper = mapper;
        this.context = context;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Connected to MongoDB");

        // Admin
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            System.out.println("ADMIN_EMAIL / ADMIN_PASSWORD not set - skipping admin account");
        } else if (users.findByEmail(adminEmail.trim().toLowerCase()).isEmpty()) {
            createUser("Admin", adminEmail, adminPassword, "9999999999", "admin", null);
            System.out.println("Admin created: " + adminEmail);
        } else {
            System.out.println("Admin already exists: " + adminEmail);
        }

        // Sample delivery agents
        for (SeedAgent a : read("seed/agents.json", new TypeReference<List<SeedAgent>>() { })) {
            if (users.findByEmail(a.email().toLowerCase()).isEmpty()) {
                createUser(a.name(), a.email(), a.password(), a.phone(), "agent", a.agentStatus());
                System.out.println("Agent created: " + a.name());
            }
        }

        // Products: replace the whole catalogue
        products.deleteAll();
        List<Product> catalogue = read("seed/products.json", new TypeReference<List<Product>>() { });
        products.saveAll(catalogue);
        System.out.println(catalogue.size() + " products inserted");
        System.out.println("Seed complete.");

        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private void createUser(String name, String email, String rawPassword, String phone, String role, String agentStatus) {
        User u = new User();
        u.setName(name);
        u.setEmail(email.trim().toLowerCase());
        u.setPassword(encoder.encode(rawPassword));
        u.setPhone(phone);
        u.setRole(role);
        if (agentStatus != null) u.setAgentStatus(agentStatus);
        users.save(u);
    }

    private <T> T read(String resource, TypeReference<T> type) throws IOException {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return mapper.readValue(in, type);
        }
    }
}
