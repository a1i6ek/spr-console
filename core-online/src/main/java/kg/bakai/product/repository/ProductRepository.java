package kg.bakai.product.repository;

import kg.bakai.product.entity.Product;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Read-only: exposes no save or delete, products belong to SPR.
 */
public interface ProductRepository extends Repository<Product, Long> {

    @Query(value = "SELECT p.* FROM public.product p ORDER BY p.id", nativeQuery = true)
    List<Product> findAllOrderById();

    @Query(value = "SELECT p.* FROM public.product p WHERE p.id = :id", nativeQuery = true)
    Optional<Product> findById(long id);
}
