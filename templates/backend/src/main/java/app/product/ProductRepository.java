package app.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** All SQL for the products table. Always bind values with :params, never concatenate input. */
@Repository
public class ProductRepository {

    public record Product(long id, String name, BigDecimal price) {
    }

    private final JdbcClient jdbc;

    public ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Product> findAll() {
        return jdbc.sql("SELECT id, name, price FROM products ORDER BY id").query(Product.class).list();
    }

    public Optional<Product> findById(long id) {
        return jdbc.sql("SELECT id, name, price FROM products WHERE id = :id")
                .param("id", id)
                .query(Product.class).optional();
    }

    public long insert(String name, BigDecimal price, long createdBy) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO products (name, price, created_by) VALUES (:name, :price, :createdBy)")
                .param("name", name)
                .param("price", price)
                .param("createdBy", createdBy)
                .update(key);
        return key.getKey().longValue();
    }

    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM products WHERE id = :id").param("id", id).update() > 0;
    }
}
