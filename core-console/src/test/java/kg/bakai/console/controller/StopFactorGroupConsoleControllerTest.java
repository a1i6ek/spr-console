package kg.bakai.console.controller;

import kg.bakai.common.enums.ProductSegment;
import kg.bakai.common.exception.BusinessValidationException;
import kg.bakai.common.exception.GlobalExceptionHandler;
import kg.bakai.common.exception.NotFoundException;
import kg.bakai.stopfactor.dto.ConditionDto;
import kg.bakai.stopfactor.dto.StopFactorDto;
import kg.bakai.stopfactor.dto.StopFactorGroupDto;
import kg.bakai.stopfactor.dto.StopFactorGroupRequest;
import kg.bakai.stopfactor.service.StopFactorConsoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kg.bakai.stopfactor.enums.ConditionOperator.GT;
import static kg.bakai.stopfactor.enums.StopFactorStatus.REJECTED;
import static kg.bakai.stopfactor.enums.StopFactorValueTypes.INTEGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Web tests of the group endpoints: parameters, json and the error responses, with the service mocked. */
class StopFactorGroupControllerTest {

    private static final String BASE = "/api/v1/admin/stop-factor-groups";

    private final StopFactorConsoleService service = mock(StopFactorConsoleService.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new StopFactorGroupConsoleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static StopFactorGroupDto group(long groupId, long versionId, int versionNumber, boolean current) {
        StopFactorDto factor = new StopFactorDto(36L, 10L, "Просроченная задолженность", "Просрочка 30-60",
                null, true, ProductSegment.RETAIL, Map.of("periodFromMonth", 1),
                List.of(new ConditionDto(REJECTED, GT, "2", null, INTEGER)), Set.of(1L),
                "Просроченная задолженность: Период с (месяц) 1");
        return new StopFactorGroupDto(groupId, versionId, versionNumber, current, "Комитет", "описание",
                true, 1, List.of(factor), LocalDateTime.of(2026, 11, 1, 10, 0), null, "причина");
    }

    private static String groupJson() {
        return """
                {
                  "name": "Кредитный комитет",
                  "factors": [
                    {
                      "metricId": 10,
                      "name": "Просрочка 30-60",
                      "params": { "periodFromMonth": 1 },
                      "conditions": [ { "result": "REJECTED", "operator": "GT", "value": "2", "valueType": "INTEGER" } ]
                    }
                  ],
                  "changeReason": "первая версия"
                }
                """;
    }

    @Nested
    @DisplayName("Reading")
    class Reading {

        @Test
        @DisplayName("list: the filters go to the service")
        void list() throws Exception {
            when(service.getAll(eq(false), eq(5L), eq(ProductSegment.RETAIL), eq(8L)))
                    .thenReturn(List.of(group(3, 30, 2, true)));

            mvc.perform(get(BASE)
                            .param("active", "false")
                            .param("productId", "5")
                            .param("segment", "RETAIL")
                            .param("metricId", "8"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].groupId").value(3))
                    .andExpect(jsonPath("$[0].versionId").value(30))
                    .andExpect(jsonPath("$[0].versionNumber").value(2))
                    .andExpect(jsonPath("$[0].current").value(true))
                    .andExpect(jsonPath("$[0].orderNumber").value(1))
                    .andExpect(jsonPath("$[0].factors[0].id").value(36))
                    .andExpect(jsonPath("$[0].factors[0].metricTitle").value("Просроченная задолженность"))
                    .andExpect(jsonPath("$[0].factors[0].summary").value("Просроченная задолженность: Период с (месяц) 1"))
                    .andExpect(jsonPath("$[0].changeReason").value("причина"));
        }

        @Test
        @DisplayName("get: one group")
        void getOne() throws Exception {
            when(service.get(3)).thenReturn(group(3, 30, 2, true));

            mvc.perform(get(BASE + "/3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.groupId").value(3))
                    .andExpect(jsonPath("$.factors[0].conditions[0].valueType").value("INTEGER"));
        }

        @Test
        @DisplayName("get: not found - 404")
        void getNotFound() throws Exception {
            when(service.get(999)).thenThrow(new NotFoundException("Группа стоп-факторов №999 не найдена."));

            mvc.perform(get(BASE + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(containsString("не найдена")));
        }

        @Test
        @DisplayName("versions: every version, newest first")
        void versions() throws Exception {
            when(service.getVersions(3)).thenReturn(List.of(group(3, 31, 3, true), group(3, 30, 2, false)));

            mvc.perform(get(BASE + "/3/versions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].versionNumber").value(3))
                    .andExpect(jsonPath("$[1].versionNumber").value(2))
                    .andExpect(jsonPath("$[1].current").value(false));
        }
    }

    @Nested
    @DisplayName("Writing")
    class Writing {

        @Test
        @DisplayName("create: 201, the group goes to the service")
        void create() throws Exception {
            when(service.create(any(StopFactorGroupRequest.class))).thenReturn(group(3, 30, 1, true));

            mvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(groupJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.groupId").value(3))
                    .andExpect(jsonPath("$.versionNumber").value(1));

            ArgumentCaptor<StopFactorGroupRequest> captor = ArgumentCaptor.forClass(StopFactorGroupRequest.class);
            verify(service).create(captor.capture());
            StopFactorGroupRequest sent = captor.getValue();
            assertThat(sent.name()).isEqualTo("Кредитный комитет");
            assertThat(sent.changeReason()).isEqualTo("первая версия");
            assertThat(sent.factors()).hasSize(1);
            assertThat(sent.factors().get(0).metricId()).isEqualTo(10);
        }

        @Test
        @DisplayName("create: an invalid group - 400 with the field errors")
        void createInvalid() throws Exception {
            mvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray());
        }

        @Test
        @DisplayName("update: the group id, the body and the change reason go to the service")
        void update() throws Exception {
            when(service.update(anyLong(), any(StopFactorGroupRequest.class))).thenReturn(group(3, 31, 2, true));

            mvc.perform(put(BASE + "/3")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(groupJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.versionNumber").value(2));

            verify(service).update(eq(3L), any(StopFactorGroupRequest.class));
        }

        @Test
        @DisplayName("status: on or off with the change reason")
        void changeStatus() throws Exception {
            StopFactorGroupDto off = new StopFactorGroupDto(3, 31, 2, true, "Комитет", "описание",
                    false, 1, List.of(), LocalDateTime.of(2026, 11, 1, 10, 0), null, "Временно отключена");
            when(service.changeStatus(anyLong(), eq(false), anyString())).thenReturn(off);

            mvc.perform(patch(BASE + "/3/status")
                            .param("active", "false")
                            .param("changeReason", "Временно отключена"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false))
                    .andExpect(jsonPath("$.changeReason").value("Временно отключена"));

            verify(service).changeStatus(3L, false, "Временно отключена");
        }

        @Test
        @DisplayName("order: every group id in the new order")
        void reorder() throws Exception {
            when(service.reorder(anyList())).thenReturn(List.of(group(2, 20, 1, true), group(1, 10, 1, true)));

            mvc.perform(put(BASE + "/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[2, 1]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].groupId").value(2))
                    .andExpect(jsonPath("$[1].groupId").value(1));

            verify(service).reorder(List.of(2L, 1L));
        }

        @Test
        @DisplayName("delete: no content")
        void deleteGroup() throws Exception {
            mvc.perform(delete(BASE + "/3").param("changeReason", "Больше не используется"))
                    .andExpect(status().isNoContent());

            verify(service).delete(3L, "Больше не используется");
        }

        @Test
        @DisplayName("a business validation error - 422 with the errors")
        void businessError() throws Exception {
            when(service.changeStatus(anyLong(), eq(false), anyString()))
                    .thenThrow(new BusinessValidationException(List.of("Укажите причину изменения.")));

            mvc.perform(patch(BASE + "/3/status")
                            .param("active", "false")
                            .param("changeReason", "x"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errors[0]").value("Укажите причину изменения."));
        }

        @Test
        @DisplayName("delete without a change reason - 400")
        void deleteReasonRequired() throws Exception {
            mvc.perform(delete(BASE + "/3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("changeReason")));
        }
    }

    @Nested
    @DisplayName("Factors through the group endpoints")
    class GroupFactors {

        @Test
        @DisplayName("addFactor: the factor and the change reason go to the service")
        void addFactor() throws Exception {
            when(service.addFactor(anyLong(), any(StopFactorDto.class), anyString()))
                    .thenReturn(group(3, 31, 2, true));

            mvc.perform(post(BASE + "/3/stop-factors")
                            .param("changeReason", "Добавлен фактор")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "metricId": 10, "name": "Новый фактор",
                                      "conditions": [ { "result": "REJECTED", "operator": "GT", "value": "2", "valueType": "INTEGER" } ] }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.versionNumber").value(2));

            verify(service).addFactor(eq(3L), any(StopFactorDto.class), eq("Добавлен фактор"));
        }

        @Test
        @DisplayName("updateFactor / deleteFactor: the ids and the change reason go to the service")
        void changeFactor() throws Exception {
            when(service.updateFactor(anyLong(), anyLong(), any(StopFactorDto.class), anyString()))
                    .thenReturn(group(3, 31, 2, true));
            when(service.deleteFactor(anyLong(), anyLong(), anyString()))
                    .thenReturn(group(3, 32, 3, true));

            mvc.perform(put(BASE + "/3/stop-factors/36")
                            .param("changeReason", "Новый лимит")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"metricId\": 10, \"name\": \"Фактор (новый)\" }"))
                    .andExpect(status().isOk());
            verify(service).updateFactor(eq(3L), eq(36L), any(StopFactorDto.class), eq("Новый лимит"));

            mvc.perform(delete(BASE + "/3/stop-factors/36").param("changeReason", "Больше не нужен"))
                    .andExpect(status().isOk());
            verify(service).deleteFactor(3L, 36L, "Больше не нужен");
        }
    }
}
