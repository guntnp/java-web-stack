package app.product;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import app.product.ProductRepository.Product;

/** Business rules for products. Controllers stay thin; anything that decides something lives here. */
@Service
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    public List<Product> list() {
        return products.findAll();
    }

    @Transactional
    public Product create(String name, BigDecimal price, long userId) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name_required");
        }
        long id = products.insert(trimmed, price, userId);
        return new Product(id, trimmed, price);
    }

    @Transactional
    public void delete(long id) {
        if (!products.delete(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product_not_found");
        }
    }
}
