package kg.bakai.console.controller;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.GlobalExceptionHandler;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorRequest;
import kg.bakai.stopfactor.dto.StopFactorResponse;
import kg.bakai.stopfactor.service.StopFactorConsoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static kg.bakai.stopfactor.enums.ConditionOperator.GT;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.INTEGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Web tests of the stop factor endpoints: parameters, json and the error responses, with the service mocked. */
class StopFactorControllerTest {

    private static final String BASE = "/api/v1/admin/stop-factors";

    private final StopFactorConsoleService service = mock(StopFactorConsoleService.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new StopFactorConsoleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static StopFactorResponse response(long id) {
        return new StopFactorResponse(id, "Просрочка 30-60", "описание", true, ProductSegment.RETAIL,
                Map.of("periodFromMonth", 1),
                List.of(new ConditionDto(REJECTED, GT, "2", null, INTEGER)),
                new LinkedHashSet<>(List.of(1L, 2L)),
                new StopFactorResponse.MetricRef(10L, "Просроченная задолженность"),
                new StopFactorResponse.GroupRef(3L, 30L, 2, "Комитет", true, 1),
                "Просроченная задолженность: Период с (месяц) 1");
    }

    private static String factorJson() {
        return """
                {
                  "metricId": 10,
                  "name": "Просрочка 30-90",
                  "params": { "periodFromMonth": 1, "periodToMonth": 6 },
                  "conditions": [ { "result": "REJECTED", "operator": "GT", "value": "3", "valueType": "INTEGER" } ],
                  "segment": "RETAIL",
                  "productIds": [1, 2]
                }
                """;
    }

    @Nested
    @DisplayName("Reading")
    class Reading {

        @Test
        @DisplayName("list: the filters go to the service, the factors come back as json")
        void list() throws Exception {
            when(service.searchFactors(eq(3L), isNull(), eq(ProductSegment.RETAIL), isNull(), eq(true), eq("проср")))
                    .thenReturn(List.of(response(36)));

            mvc.perform(get(BASE)
                            .param("groupId", "3")
                            .param("segment", "RETAIL")
                            .param("active", "true")
                            .param("search", "проср"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(36))
                    .andExpect(jsonPath("$[0].name").value("Просрочка 30-60"))
                    .andExpect(jsonPath("$[0].segment").value("RETAIL"))
                    .andExpect(jsonPath("$[0].metric.id").value(10))
                    .andExpect(jsonPath("$[0].metric.title").value("Просроченная задолженность"))
                    .andExpect(jsonPath("$[0].group.groupId").value(3))
                    .andExpect(jsonPath("$[0].group.versionNumber").value(2))
                    .andExpect(jsonPath("$[0].group.groupActive").value(true))
                    .andExpect(jsonPath("$[0].conditions[0].operator").value("GT"));
        }

        @Test
        @DisplayName("get: one factor with its group and metric")
        void getOne() throws Exception {
            when(service.getFactor(36)).thenReturn(response(36));

            mvc.perform(get(BASE + "/36"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(36))
                    .andExpect(jsonPath("$.group.name").value("Комитет"))
                    .andExpect(jsonPath("$.productIds[0]").value(1))
                    .andExpect(jsonPath("$.summary").value("Просроченная задолженность: Период с (месяц) 1"));
        }

        @Test
        @DisplayName("get: not found - 404 with the message")
        void getNotFound() throws Exception {
            when(service.getFactor(999)).thenThrow(new NotFoundException("Стоп-фактор №999 не найден."));

            mvc.perform(get(BASE + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(containsString("не найден")));
        }
    }

    @Nested
    @DisplayName("Writing")
    class Writing {

        @Test
        @DisplayName("update: the body and the change reason go to the service")
        void update() throws Exception {
            when(service.updateFactor(anyLong(), any(StopFactorRequest.class), anyString()))
                    .thenAnswer(invocation -> response(invocation.getArgument(0)));

            mvc.perform(put(BASE + "/36")
                            .param("changeReason", "Новый лимит")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(factorJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(36));

            ArgumentCaptor<StopFactorRequest> captor = ArgumentCaptor.forClass(StopFactorRequest.class);
            verify(service).updateFactor(eq(36L), captor.capture(), eq("Новый лимит"));
            StopFactorRequest sent = captor.getValue();
            assertThat(sent.name()).isEqualTo("Просрочка 30-90");
            assertThat(sent.metricId()).isEqualTo(10);
            assertThat(sent.segment()).isEqualTo(ProductSegment.RETAIL);
            assertThat(sent.conditions()).hasSize(1);
        }

        @Test
        @DisplayName("update: an invalid body - 400 with the field errors")
        void updateInvalid() throws Exception {
            mvc.perform(put(BASE + "/36")
                            .param("changeReason", "причина")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors.length()").value(2));

            verify(service, never()).updateFactor(anyLong(), any(), anyString());
        }

        @Test
        @DisplayName("update: a business validation error - 422 with the errors")
        void updateBusinessError() throws Exception {
            when(service.updateFactor(anyLong(), any(), anyString()))
                    .thenThrow(new BusinessValidationException(List.of("Метрика №10 отключена.")));

            mvc.perform(put(BASE + "/36")
                            .param("changeReason", "попытка")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(factorJson()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0]").value("Метрика №10 отключена."));
        }

        @Test
        @DisplayName("status: on or off with the change reason")
        void changeStatus() throws Exception {
            when(service.changeFactorStatus(anyLong(), eq(false), anyString())).thenReturn(response(36));

            mvc.perform(patch(BASE + "/36/status")
                            .param("active", "false")
                            .param("changeReason", "Временно отключен"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(36));

            verify(service).changeFactorStatus(36L, false, "Временно отключен");
        }

        @Test
        @DisplayName("status without a change reason - 400")
        void changeStatusReasonRequired() throws Exception {
            mvc.perform(patch(BASE + "/36/status").param("active", "false"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("changeReason")));
        }
    }
}
