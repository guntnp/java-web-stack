package app.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Unit tests for the business rules: no Spring context and no database, so they run inside the Docker build. */
class ProductServiceTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductService service = new ProductService(repository);

    @Test
    void createTrimsTheName() {
        when(repository.insert("Tea", new BigDecimal("40.00"), 7L)).thenReturn(1L);

        var product = service.create("  Tea  ", new BigDecimal("40.00"), 7L);

        assertThat(product.name()).isEqualTo("Tea");
        assertThat(product.id()).isEqualTo(1L);
    }

    @Test
    void createRejectsANameOfOnlySpaces() {
        assertThatThrownBy(() -> service.create("   ", BigDecimal.ONE, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("name_required");
        verify(repository, never()).insert(any(), any(), anyLong());
    }

    @Test
    void deletingAMissingProductIsNotFound() {
        when(repository.delete(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("product_not_found");
    }
}
