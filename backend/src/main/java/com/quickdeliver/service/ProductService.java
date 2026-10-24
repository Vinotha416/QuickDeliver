package com.quickdeliver.service;

import com.quickdeliver.common.ApiException;
import com.quickdeliver.common.Num;
import com.quickdeliver.model.Product;
import com.quickdeliver.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Product CRUD shared by /api/products and /api/admin/products. */
@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    public Product create(Map<String, Object> body) {
        Product p = new Product();
        apply(p, body);
        if (Num.blank(p.getName()) || Num.blank(p.getDescription()) || p.getPrice() == null || Num.blank(p.getCategory())) {
            throw ApiException.badRequest("Name, description, price and category are required");
        }
        return repo.save(p);
    }

    public Product update(String id, Map<String, Object> body) {
        Product p = repo.findById(id).orElseThrow(() -> ApiException.notFound("Not found"));
        apply(p, body);
        return repo.save(p);
    }

    public void delete(String id) {
        repo.deleteById(id);
    }

    /** Copies only the whitelisted fields that are present in the request (no mass assignment). */
    private static void apply(Product p, Map<String, Object> b) {
        if (b == null) return;
        if (b.get("name") != null)        p.setName(b.get("name").toString());
        if (b.get("description") != null) p.setDescription(b.get("description").toString());
        if (b.get("category") != null)    p.setCategory(b.get("category").toString());
        if (b.get("image") != null)       p.setImage(b.get("image").toString());
        if (b.get("price") != null)       p.setPrice(Num.toDouble(b.get("price")));
        if (b.get("discount") != null)    p.setDiscount(Num.toDouble(b.get("discount")));
        if (b.get("rating") != null)      p.setRating(Num.toDouble(b.get("rating")));
        if (b.get("stock") != null)       p.setStock(Num.toInt(b.get("stock")));
        if (b.get("isAvailable") != null) p.setIsAvailable(Num.toBool(b.get("isAvailable")));
    }
}
