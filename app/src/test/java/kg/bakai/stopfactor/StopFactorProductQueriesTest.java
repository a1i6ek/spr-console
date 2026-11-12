package kg.bakai.stopfactor;

import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.enums.CalculatorType;
import kg.bakai.stopfactor.enums.ConditionOperator;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Native product queries of the stop factor module (public.product read as ProductProjection, no core-online
 * classes) and product limits of console stop factors. Runs against the database, every test is rolled back. Test
 * products have negative ids so they never clash with real ones.
 */
@SpringBootTest
@Transactional
class StopFactorProductQueriesTest {

    private static final long PRODUCT_A = -1;
    private static final long PRODUCT_B = -2;
    private static final long UNKNOWN_PRODUCT = -999;

    @Autowired
    private StopFactorGroupRepository repository;
    @Autowired
    private StopFactorConsoleService stopFactorConsoleService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void products() {
        jdbcTemplate.update("INSERT INTO public.product (id, name, description, segment_type) "
                + "VALUES (?, 'Test A', 'Product A', 'RETAIL'), (?, 'Test B', 'Product B', 'BUSINESS')", PRODUCT_A, PRODUCT_B);
    }

    @Test
    void findProductsByIdsReturnsOnlyExistingProductsWithAllColumns() {
        List<ProductProjection> products = repository.findProductsByIds(List.of(PRODUCT_A, PRODUCT_B, UNKNOWN_PRODUCT));

        assertThat(products)
                .extracting(ProductProjection::getId, ProductProjection::getName, ProductProjection::getDescription, ProductProjection::getSegmentType)
                .containsExactlyInAnyOrder(
                        tuple(PRODUCT_A, "Test A", "Product A", "RETAIL"),
                        tuple(PRODUCT_B, "Test B", "Product B", "BUSINESS"));
    }

    @Test
    void findProductsByIdsWithoutExistingProductsIsEmpty() {
        assertThat(repository.findProductsByIds(List.of(UNKNOWN_PRODUCT))).isEmpty();
    }

    @Test
    void findProductByScoringIdReadsTheProductOfTheScoring() {
        jdbcTemplate.update("INSERT INTO public.scoring (id, product_id) VALUES (-500, ?), (-501, NULL)", PRODUCT_B);

        assertThat(repository.findProductByScoringId(-500))
                .get()
                .extracting(ProductProjection::getId, ProductProjection::getName, ProductProjection::getDescription)
                .containsExactly(PRODUCT_B, "Test B", "Product B");
        assertThat(repository.findProductByScoringId(-501)).isEmpty();   // scoring without product
        assertThat(repository.findProductByScoringId(-502)).isEmpty();   // unknown scoring (legacy)
    }

    @Test
    void stopFactorCanBeLimitedToExistingProducts() {
        StopFactorDto created = stopFactorConsoleService.create(request(Set.of(PRODUCT_A))).factors().getFirst();

        assertThat(created.productIds()).containsExactly(PRODUCT_A);
        assertThat(jdbcTemplate.queryForList("SELECT product_id FROM stop_factors.stop_factor_product WHERE stop_factor_id = ?",
                Long.class, created.id())).containsExactly(PRODUCT_A);
    }

    @Test
    void unknownProductIdIsRejectedByTheConsole() {
        assertThatThrownBy(() -> stopFactorConsoleService.create(request(Set.of(PRODUCT_A, UNKNOWN_PRODUCT))))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Стоп-фактор 1: продукт с id " + UNKNOWN_PRODUCT + " не найден.");
    }

    private static kg.bakai.stopfactor.dto.StopFactorGroupRequest request(Set<Long> productIds) {
        return TestGroups.group(TestGroups.disputes(null, productIds));
    }
}
