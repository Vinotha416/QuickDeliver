package com.quickdeliver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Serves the HTML pages (static/pages/*.html) on the same clean URLs the Express app used. */
@Controller
public class PageController {

    @GetMapping("/")
    public String home() { return "forward:/pages/home.html"; }

    @GetMapping("/login")
    public String login() { return "forward:/pages/login.html"; }

    @GetMapping("/register")
    public String register() { return "forward:/pages/register.html"; }

    @GetMapping("/products")
    public String products() { return "forward:/pages/products.html"; }

    @GetMapping("/cart")
    public String cart() { return "forward:/pages/cart.html"; }

    @GetMapping("/checkout")
    public String checkout() { return "forward:/pages/checkout.html"; }

    @GetMapping("/orders")
    public String orders() { return "forward:/pages/orders.html"; }

    @GetMapping("/order/{id}")
    public String order(@PathVariable String id) { return "forward:/pages/order-detail.html"; }

    @GetMapping("/dashboard")
    public String dashboard() { return "forward:/pages/dashboard.html"; }

    @GetMapping("/analytics")
    public String analytics() { return "forward:/pages/analytics.html"; }

    @GetMapping("/admin")
    public String admin() { return "forward:/pages/admin.html"; }

    @GetMapping("/agent")
    public String agent() { return "forward:/pages/agent.html"; }

    @GetMapping("/logout")
    public String logout() { return "forward:/pages/logout.html"; }
}
