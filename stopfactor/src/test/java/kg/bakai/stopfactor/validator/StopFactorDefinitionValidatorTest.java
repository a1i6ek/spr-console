package kg.bakai.stopfactor.validator;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.stopfactor.calculator.CalculatorRegistry;
import kg.bakai.stopfactor.repository.StopFactorGroupRepository;
import kg.bakai.stopfactor.repository.projection.ProductProjection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kg.bakai.common.enums.ProductSegment.MSB;
import static kg.bakai.common.enums.ProductSegment.RETAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StopFactorDefinitionValidatorTest {

    private static final Map<Long, ProductProjection> PRODUCTS = Map.of(
            1L, product(1, "Online кредит", RETAIL),
            2L, product(2, "Тумар", RETAIL),
            5L, product(5, "Бизнес лимит", MSB));

    private final StopFactorGroupRepository groupRepository = mock(StopFactorGroupRepository.class);
    private final StopFactorDefinitionValidator validator =
            new StopFactorDefinitionValidator(mock(CalculatorRegistry.class), groupRepository);

    private final List<String> errors = new ArrayList<>();

    @Nested
    class Products {

        @Test
        void withoutProductsTheSegmentIsKeptAndNothingIsRead() {
            assertThat(validator.validateProducts(Set.of(), MSB, errors)).isEqualTo(MSB);
            assertThat(validator.validateProducts(null, null, errors)).isNull();
            assertThat(errors).isEmpty();
            verifyNoInteractions(groupRepository);
        }

        @Test
        void productsOfTheSegmentAreAccepted() {
            productsExist();

            assertThat(validator.validateProducts(Set.of(1L, 2L), RETAIL, errors)).isEqualTo(RETAIL);
            assertThat(errors).isEmpty();
        }

        @Test
        void aProductOfAnotherSegmentIsRejectedByName() {
            productsExist();

            validator.validateProducts(Set.of(1L, 5L), RETAIL, errors);

            assertThat(errors).containsExactly("Продукт «Бизнес лимит» (id 5) относится к сегменту MSB, а стоп-фактор "
                    + "к сегменту RETAIL. Выберите продукты сегмента RETAIL или другой сегмент.");
        }

        @Test
        void withoutSegmentItIsTheSegmentAllProductsShare() {
            productsExist();

            assertThat(validator.validateProducts(Set.of(1L, 2L), null, errors)).isEqualTo(RETAIL);
            assertThat(validator.validateProducts(Set.of(5L), null, errors)).isEqualTo(MSB);
            assertThat(errors).isEmpty();
        }

        @Test
        void productsOfDifferentSegmentsWithoutSegmentStayForEverySegment() {
            productsExist();

            assertThat(validator.validateProducts(Set.of(1L, 5L), null, errors)).isNull();
            assertThat(errors).isEmpty();
        }

        @Test
        void unknownProductsAreReportedInOrder() {
            productsExist();

            validator.validateProducts(Set.of(1L, 99L, 42L), RETAIL, errors);

            assertThat(errors).containsExactly("Продукт с id 42 не найден.", "Продукт с id 99 не найден.");
        }
    }

    // ------------------------------------------------------------------------------------------------

    /** findProductsByIds returns the known products among the requested ids, as the native query does. */
    @SuppressWarnings("unchecked")
    private void productsExist() {
        when(groupRepository.findProductsByIds(anyCollection())).thenAnswer(invocation ->
                ((Collection<Long>) invocation.getArgument(0)).stream()
                        .filter(PRODUCTS::containsKey)
                        .map(PRODUCTS::get)
                        .toList());
    }

    private static ProductProjection product(long id, String name, ProductSegment segment) {
        return new ProductProjection() {
            public Long getId() { return id; }
            public String getName() { return name; }
            public String getDescription() { return name; }
            public String getSegmentType() { return segment.name(); }
        };
    }
}
