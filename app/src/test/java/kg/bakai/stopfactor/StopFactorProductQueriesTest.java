package kg.bakai.stopfactor;

import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;

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

/**
 * Native product queries of the stop factor module (public.product read as ProductProjection, no core-online
 * classes) and product limits of console stop factors. Runs against the database, every test is rolled back. Test products have negative ids so they never clash with real ones.
 */
@SpringBootTest
@Transactional
class StopFactorProductQueriesTest {

    private static final long PRODUCT_A = -1;
    private static final long PRODUCT_B = -2;
    private static final long UNKNOWN_PRODUCT = -999;
    private static final long ACTIVE_DISPUTES_METRIC = 2;

    @Autowired
    private StopFactorGroupRepository groupRepository;
    @Autowired
    private StopFactorConsoleService stopFactorConsoleService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void products() {
        jdbcTemplate.update("INSERT INTO public.product (id, name, description) VALUES (?, 'Test A', 'Product A'), (?, 'Test B', 'Product B')",
                PRODUCT_A, PRODUCT_B);
    }

    // ------------------------------------------------------------------------------------------------
    // StopFactorGroupRepository native queries
    // ------------------------------------------------------------------------------------------------

    @Test
    void findProductsByIdsReturnsOnlyExistingProductsWithAllColumns() {
        List<ProductProjection> products = groupRepository.findProductsByIds(List.of(PRODUCT_A, PRODUCT_B, UNKNOWN_PRODUCT));

        assertThat(products)
                .extracting(ProductProjection::getId, ProductProjection::getName, ProductProjection::getDescription)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_A, "Test A", "Product A"),
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_B, "Test B", "Product B"));
    }

    @Test
    void findProductsByIdsWithoutExistingProductsIsEmpty() {
        assertThat(groupRepository.findProductsByIds(List.of(UNKNOWN_PRODUCT))).isEmpty();
    }

    @Test
    void findProductByScoringIdReadsTheProductOfTheScoring() {
        jdbcTemplate.update("INSERT INTO public.scoring (id, product_id) VALUES (-500, ?), (-501, NULL)", PRODUCT_B);

        assertThat(groupRepository.findProductByScoringId(-500))
                .get()
                .extracting(ProductProjection::getId, ProductProjection::getName, ProductProjection::getDescription)
                .containsExactly(PRODUCT_B, "Test B", "Product B");
        assertThat(groupRepository.findProductByScoringId(-501)).isEmpty();   // scoring without product
        assertThat(groupRepository.findProductByScoringId(-502)).isEmpty();   // unknown scoring (legacy)
    }

    // ------------------------------------------------------------------------------------------------
    // Console validation of product ids (StopFactorDefinitionValidator uses findProductsByIds)
    // ------------------------------------------------------------------------------------------------

    @Test
    void stopFactorCanBeLimitedToExistingProducts() {
        StopFactorResponse created = stopFactorConsoleService.create(stopFactorRequest(Set.of(PRODUCT_A, PRODUCT_B)), "test");

        assertThat(created.productIds()).containsExactlyInAnyOrder(PRODUCT_A, PRODUCT_B);
    }

    @Test
    void unknownProductIdIsRejectedByTheConsole() {
        assertThatThrownBy(() -> stopFactorConsoleService.create(stopFactorRequest(Set.of(PRODUCT_A, UNKNOWN_PRODUCT)), "test"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Продукт с id " + UNKNOWN_PRODUCT + " не найден")
                .hasMessageNotContaining("продукт " + PRODUCT_A + " ");
    }

    private static StopFactorRequest stopFactorRequest(Set<Long> productIds) {
        return new StopFactorRequest(ACTIVE_DISPUTES_METRIC, null, null, true,
                productIds, null,
                List.of(new StopFactorDto(Map.of(), List.of(new ConditionDto(null, StopFactorStatus.REJECTED, StopFactorValueTypes.INTEGER, "0")))),
                null);
    }
}
