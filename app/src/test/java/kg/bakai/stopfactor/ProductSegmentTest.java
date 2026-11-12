package kg.bakai.stopfactor;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.product.dto.ProductDto;
import kg.bakai.product.service.ProductService;
import kg.bakai.stopfactor.dto.StopFactorGroupDto;
import kg.bakai.stopfactor.dto.StopFactorGroupRequest;
import kg.bakai.stopfactor.service.StopFactorConsoleService;
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
 * Products are read only: their segment_type belongs to SPR (RETAIL, BUSINESS, IFC, ...), here set by SQL. Business
 * products get the MSB stop factors, every other product the RETAIL ones. Runs against the migrated database, every
 * test is rolled back. Test products have negative ids.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductSegmentTest {

    private static final long PRODUCT = -21;

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
        jdbcTemplate.update("INSERT INTO public.product (id, name, description, segment_type) VALUES (?, 'Тест сегмента', 'Тест', 'BUSINESS')", PRODUCT);
    }

    @Test
    void productIsReadWithSprsSegmentAndItsStopFactorSegment() {
        ProductDto product = productService.get(PRODUCT);

        assertThat(product.name()).isEqualTo("Тест сегмента");
        assertThat(product.segmentType()).isEqualTo("BUSINESS");
        assertThat(product.segment()).isEqualTo(MSB);
        assertThat(productService.getAll()).extracting(ProductDto::id).contains(PRODUCT);
    }

    @Test
    void ifcAndProductsWithoutSegmentAreRetail() {
        jdbcTemplate.update("INSERT INTO public.product (id, name, description, segment_type) VALUES (-22, 'ИФЦ', 'Тест', 'IFC'), "
                + "(-23, 'Без сегмента', 'Тест', NULL)");

        assertThat(productService.get(-22).segment()).isEqualTo(RETAIL);
        assertThat(productService.get(-23).segment()).isEqualTo(RETAIL);
    }

    @Test
    void theCheckNeverChangesSprsSegmentType() {
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT segment_type FROM public.product WHERE id > 0", String.class))
                .doesNotContain("MSB");
    }

    @Test
    void unknownProductIsNotFound() {
        assertThatThrownBy(() -> productService.get(-999)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void consoleOnlyReadsProducts() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/{id}", PRODUCT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentType").value("BUSINESS"))
                .andExpect(jsonPath("$.segment").value("MSB"));
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

    @Test
    void changingTheSegmentChangesWhichStopFactorsCheckTheProduct() {
        StopFactorGroupDto retailOnly = stopFactorConsoleService.create(stopFactor(RETAIL, Set.of()));
        StopFactorGroupDto msbOnly = stopFactorConsoleService.create(stopFactor(MSB, Set.of()));
        StopFactorGroupDto common = stopFactorConsoleService.create(stopFactor(null, Set.of()));

        assertThat(ids(stopFactorConsoleService.getAll(null, PRODUCT, null, null)))
                .contains(msbOnly.groupId(), common.groupId())
                .doesNotContain(retailOnly.groupId());

        jdbcTemplate.update("UPDATE public.product SET segment_type = 'IFC' WHERE id = ?", PRODUCT);

        assertThat(ids(stopFactorConsoleService.getAll(null, PRODUCT, null, null)))
                .contains(retailOnly.groupId(), common.groupId())
                .doesNotContain(msbOnly.groupId());
    }

    @Test
    void consoleFiltersStopFactorsBySegment() {
        StopFactorGroupDto retailOnly = stopFactorConsoleService.create(stopFactor(RETAIL, Set.of()));
        StopFactorGroupDto msbOnly = stopFactorConsoleService.create(stopFactor(MSB, Set.of()));
        StopFactorGroupDto common = stopFactorConsoleService.create(stopFactor(null, Set.of()));

        assertThat(ids(stopFactorConsoleService.getAll(null, null, MSB, null)))
                .contains(msbOnly.groupId(), common.groupId())
                .doesNotContain(retailOnly.groupId());
    }

    @Test
    void aStopFactorCannotBeLimitedToAProductOfAnotherSegment() {
        assertThatThrownBy(() -> stopFactorConsoleService.create(stopFactor(RETAIL, Set.of(PRODUCT))))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Стоп-фактор 1: продукт «Тест сегмента» (id " + PRODUCT + ") относится к сегменту MSB, а стоп-фактор к сегменту RETAIL.");
    }

    @Test
    void aStopFactorLimitedToProductsWithoutSegmentGetsTheirSegment() {
        StopFactorGroupDto created = stopFactorConsoleService.create(stopFactor(null, Set.of(PRODUCT)));

        assertThat(created.factors().getFirst().segment()).isEqualTo(MSB);
    }

    private static List<Long> ids(List<StopFactorGroupDto> groups) {
        return groups.stream().map(StopFactorGroupDto::groupId).toList();
    }

    private static StopFactorGroupRequest stopFactor(ProductSegment segment, Set<Long> productIds) {
        return TestGroups.group(TestGroups.disputes(segment, productIds));
    }
}
