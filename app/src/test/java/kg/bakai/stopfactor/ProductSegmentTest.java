package kg.bakai.stopfactor;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.product.dto.ProductDto;
import kg.bakai.product.service.ProductService;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.enums.StopFactorStatus;
import kg.bakai.stopfactor.enums.StopFactorValueTypes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Products are read only in the console: their segment (public.product.segment_type) is set by SPR, here by SQL.
 * Checks the product console and which stop factors a product's segment gives it. Runs against the migrated
 * database, every test is rolled back. Test products have negative ids.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductSegmentTest {

    private static final long PRODUCT = -21;
    private static final long ACTIVE_DISPUTES_METRIC = 2;

    @Autowired
    private ProductService productService;
    @Autowired
    private StopFactorConsoleService stopFactorConsoleService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void product() {
        jdbcTemplate.update("INSERT INTO public.product (id, name, description, segment_type) VALUES (?, 'Тест сегмента', 'Тест', 'MSB')", PRODUCT);
    }

    // ------------------------------------------------------------------------------------------------
    // Product console (read only)
    // ------------------------------------------------------------------------------------------------

    @Test
    void productIsReadWithItsSegment() {
        ProductDto product = productService.get(PRODUCT);

        assertThat(product.name()).isEqualTo("Тест сегмента");
        assertThat(product.segmentType()).isEqualTo(MSB);
        assertThat(productService.getAll()).extracting(ProductDto::id).contains(PRODUCT);
    }

    @Test
    void productAddedWithoutSegmentIsRetail() {
        jdbcTemplate.update("INSERT INTO public.product (id, name, description) VALUES (-22, 'Без сегмента', 'Тест')");

        assertThat(productService.get(-22).segmentType()).isEqualTo(RETAIL);
    }

    @Test
    void unknownProductIsNotFound() {
        assertThatThrownBy(() -> productService.get(-999)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void consoleOnlyReadsProducts() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/{id}", PRODUCT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentType").value("MSB"));
        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/products/{id}", -999))
                .andExpect(status().isNotFound());

        String body = "{ \"name\": \"Новый\", \"description\": \"Новый\", \"segmentType\": \"RETAIL\" }";
        mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/admin/products/{id}", PRODUCT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/admin/products/{id}", PRODUCT))
                .andExpect(status().isMethodNotAllowed());
    }

    // ------------------------------------------------------------------------------------------------
    // Segment -> stop factors
    // ------------------------------------------------------------------------------------------------

    @Test
    void changingTheSegmentChangesWhichStopFactorsCheckTheProduct() {
        StopFactorResponse retailOnly = stopFactorConsoleService.create(stopFactor(RETAIL, "создание"));
        StopFactorResponse msbOnly = stopFactorConsoleService.create(stopFactor(MSB, "создание"));
        StopFactorResponse common = stopFactorConsoleService.create(stopFactor(null, "создание"));

        assertThat(ids(stopFactorConsoleService.getAll(null, null, PRODUCT, null, null)))
                .contains(msbOnly.groupId(), common.groupId())
                .doesNotContain(retailOnly.groupId());

        jdbcTemplate.update("UPDATE public.product SET segment_type = 'RETAIL' WHERE id = ?", PRODUCT);

        assertThat(ids(stopFactorConsoleService.getAll(null, null, PRODUCT, null, null)))
                .contains(retailOnly.groupId(), common.groupId())
                .doesNotContain(msbOnly.groupId());
    }

    @Test
    void consoleFiltersStopFactorsBySegment() {
        StopFactorResponse retailOnly = stopFactorConsoleService.create(stopFactor(RETAIL, "создание"));
        StopFactorResponse msbOnly = stopFactorConsoleService.create(stopFactor(MSB, "создание"));
        StopFactorResponse common = stopFactorConsoleService.create(stopFactor(null, "создание"));

        assertThat(ids(stopFactorConsoleService.getAll(null, null, null, MSB, null)))
                .contains(msbOnly.groupId(), common.groupId())
                .doesNotContain(retailOnly.groupId());
    }

    @Test
    void segmentOfAStopFactorIsPartOfItsVersion() {
        StopFactorResponse created = stopFactorConsoleService.create(stopFactor(MSB, "создание"));
        assertThat(segmentOf(created)).isEqualTo(MSB);

        StopFactorResponse unchanged = stopFactorConsoleService.update(created.groupId(), stopFactor(MSB, "без изменений"));
        assertThat(unchanged.versionNumber()).isEqualTo(1);

        StopFactorResponse forEverySegment = stopFactorConsoleService.update(created.groupId(), stopFactor(null, "для всех сегментов"));
        assertThat(forEverySegment.versionNumber()).isEqualTo(2);
        assertThat(segmentOf(forEverySegment)).isNull();
        assertThat(stopFactorConsoleService.getVersions(created.groupId()))
                .extracting(ProductSegmentTest::segmentOf)
                .containsExactly(null, MSB);
    }

    @Test
    void theSameParamsMayBeCheckedForEverySegmentWithinOneGroup() {
        assertThatThrownBy(() -> stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(factor(Map.of(), MSB), factor(Map.of(), MSB)), "создание")))
                .hasMessageContaining("Стоп-фактор 2 повторяет стоп-фактор 1");
    }

    @Test
    void aGroupIsListedForTheSegmentOfAnyOfItsStopFactors() {
        StopFactorResponse mixed = stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(factor(Map.of(), RETAIL), factor(Map.of(), MSB)), "создание"));

        assertThat(mixed.factors()).extracting(StopFactorDto::segment).containsExactly(RETAIL, MSB);
        assertThat(ids(stopFactorConsoleService.getAll(null, null, null, MSB, null))).contains(mixed.groupId());
        assertThat(ids(stopFactorConsoleService.getAll(null, null, null, RETAIL, null))).contains(mixed.groupId());
        assertThat(ids(stopFactorConsoleService.getAll(null, null, PRODUCT, null, null))).contains(mixed.groupId());
    }

    @Test
    void aStopFactorCannotBeLimitedToAProductOfAnotherSegment() {
        assertThatThrownBy(() -> stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(factor(Set.of(PRODUCT), RETAIL)), "создание")))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Стоп-фактор 1: продукт «Тест сегмента» (id " + PRODUCT + ") относится к сегменту MSB");
    }

    @Test
    void aStopFactorLimitedToProductsWithoutSegmentGetsTheirSegment() {
        StopFactorResponse created = stopFactorConsoleService.create(new StopFactorRequest(null, null, true,
                List.of(factor(Set.of(PRODUCT), null)), "создание"));

        assertThat(segmentOf(created)).isEqualTo(MSB);
        // the same request again changes nothing: the segment is derived the same way
        StopFactorResponse unchanged = stopFactorConsoleService.update(created.groupId(), new StopFactorRequest(null, created.orderNumber(), true, List.of(factor(Set.of(PRODUCT), null)), "без изменений"));
        assertThat(unchanged.versionNumber()).isEqualTo(1);
    }

    /** Segment of the only stop factor. */
    private static ProductSegment segmentOf(StopFactorResponse group) {
        return group.factors().getFirst().segment();
    }

    private static List<Long> ids(List<StopFactorResponse> responses) {
        return responses.stream().map(StopFactorResponse::groupId).toList();
    }

    private static StopFactorRequest stopFactor(ProductSegment segment, String changeReason) {
        return new StopFactorRequest(null, null, true, List.of(factor(Map.of(), segment)), changeReason);
    }

    private static StopFactorDto factor(Set<Long> productIds, ProductSegment segment) {
        return new StopFactorDto(null, ACTIVE_DISPUTES_METRIC, null, null, null, Map.of(),
                List.of(new ConditionDto(null, StopFactorStatus.REJECTED, StopFactorValueTypes.INTEGER, "0")), null, productIds, segment);
    }

    private static StopFactorDto factor(Map<String, Object> params, ProductSegment segment) {
        return new StopFactorDto(null, ACTIVE_DISPUTES_METRIC, null, null, null, params,
                List.of(new ConditionDto(null, StopFactorStatus.REJECTED, StopFactorValueTypes.INTEGER, "0")), null, Set.of(), segment);
    }
}
