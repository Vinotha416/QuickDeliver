package com.quickdeliver.controller;

import com.quickdeliver.common.ApiException;
import com.quickdeliver.common.ApiResponse;
import com.quickdeliver.common.Constants;
import com.quickdeliver.model.Product;
import com.quickdeliver.repository.ProductRepository;
import com.quickdeliver.security.Auth;
import com.quickdeliver.service.ProductService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

/** Port of routes/products.js */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final MongoTemplate mongo;
    private final ProductRepository products;
    private final ProductService productService;

    public ProductController(MongoTemplate mongo, ProductRepository products, ProductService productService) {
        this.mongo = mongo;
        this.products = products;
        this.productService = productService;
    }

    /** Public. Out-of-stock products are included so the UI can show an "Out of Stock" label. */
    @GetMapping
    public ApiResponse list(@RequestParam(required = false) String category,
                            @RequestParam(required = false) String search) {
        Query q = new Query();
        if (category != null && !category.isBlank() && !"all".equals(category)) {
            q.addCriteria(Criteria.where("category").is(category));
        }
        if (search != null && !search.isBlank()) {
            // quoted: the search box is plain text, not a regular expression
            q.addCriteria(Criteria.where("name").regex(Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE)));
        }
        q.with(Sort.by(Sort.Order.desc("isAvailable"), Sort.Order.desc("createdAt")));
        return ApiResponse.ok().with("products", mongo.find(q, Product.class));
    }

    @GetMapping("/{id}")
    public ApiResponse one(@PathVariable String id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Not found"));
        return ApiResponse.ok().with("product", p);
    }

    @PostMapping
    @Auth(roles = Constants.ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok().with("product", productService.create(body));
    }

    @PutMapping("/{id}")
    @Auth(roles = Constants.ADMIN)
    public ApiResponse update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok().with("product", productService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @Auth(roles = Constants.ADMIN)
    public ApiResponse delete(@PathVariable String id) {
        productService.delete(id);
        return ApiResponse.ok();
    }
}
