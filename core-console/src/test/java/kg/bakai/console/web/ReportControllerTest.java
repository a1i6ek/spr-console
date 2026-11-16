package kg.bakai.console.web;

import kg.bakai.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** The report endpoints against a fake SPR: what is asked of SPR, and what the console gets back. */
class ReportControllerTest {

    private static final String SPR = "http://spr";
    private static final String REPORTS = "http://reports";
    private static final String AUTH = "Basic dXNlcjpzZXNzaW9u";   // user:session

    private MockRestServiceServer spr;
    private MockRestServiceServer reportService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        RestTemplate sprTemplate = new RestTemplate();
        RestTemplate reportTemplate = new RestTemplate();
        spr = MockRestServiceServer.bindTo(sprTemplate).build();
        reportService = MockRestServiceServer.bindTo(reportTemplate).build();
        SprReportClient client = new SprReportClient(sprTemplate, reportTemplate, SPR, REPORTS);
        mvc = standaloneSetup(new ReportController(client)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("file: SPR is asked with the user's Authorization; its file, type and name come back")
    void file() throws Exception {
        spr.expect(requestTo(SPR + "/spr/api/v1/file/1024")).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH))
                .andRespond(withSuccess("pdf".getBytes(), MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scoring_1024.pdf\""));

        mvc.perform(get("/api/v1/console/reports/files/1024").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().string("pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scoring_1024.pdf\""));
        spr.verify();
    }

    @Test
    @DisplayName("file: SPR's 404 and 403 become the WebController's messages")
    void fileErrors() throws Exception {
        spr.expect(requestTo(SPR + "/spr/api/v1/file/7")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/v1/console/reports/files/7").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Файл со scoringId 7 не найден"));

        spr.reset();
        spr.expect(requestTo(SPR + "/spr/api/v1/file/8")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        mvc.perform(get("/api/v1/console/reports/files/8").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Недостаточно прав"));
    }

    @Test
    @DisplayName("no Authorization: 401 without asking SPR; SPR's 401 is passed on")
    void unauthorized() throws Exception {
        mvc.perform(get("/api/v1/console/reports/files/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Требуется авторизация"));

        spr.expect(requestTo(SPR + "/spr/api/v1/file/1")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/v1/console/reports/files/1").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isUnauthorized());
        spr.verify();
    }

    @Test
    @DisplayName("files: at most 3 days, from before to; SPR's 400 shows its own text; the zip is named by the period")
    void files() throws Exception {
        mvc.perform(get("/api/v1/console/reports/files?from=2026-11-12&to=2026-11-16").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Период не должен превышать 3 календарных дня"));
        mvc.perform(get("/api/v1/console/reports/files?from=2026-11-16&to=2026-11-15").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Начальная дата не может быть позже конечной"));

        spr.expect(requestTo(SPR + "/spr/api/v1/file/all?from=2026-11-13&to=2026-11-16"))
                .andRespond(withSuccess("zip".getBytes(), MediaType.APPLICATION_OCTET_STREAM));
        spr.expect(requestTo(SPR + "/spr/api/v1/file/all?from=2026-01-01&to=2026-01-02"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("Нет файлов за период".getBytes()));
        mvc.perform(get("/api/v1/console/reports/files?from=2026-11-13&to=2026-11-16").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CONTENT_DISPOSITION, containsString("files--2026-11-13--2026-11-16.zip")));

        mvc.perform(get("/api/v1/console/reports/files?from=2026-01-01&to=2026-01-02").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Нет файлов за период"));
        spr.verify();
    }

    @Test
    @DisplayName("general: a month is its first and last day at the report service; a bad month is refused")
    void generalReport() throws Exception {
        mvc.perform(get("/api/v1/console/reports/general?month=2026-13").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Неверный формат периода"));

        reportService.expect(requestTo(REPORTS + "/api/export/excel?from=2026-02-01&to=2026-02-28"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTH))
                .andRespond(withSuccess("xlsx".getBytes(), MediaType.APPLICATION_OCTET_STREAM));
        mvc.perform(get("/api/v1/console/reports/general?month=2026-02").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CONTENT_DISPOSITION, containsString("scoring-report-2026-02.xlsx")));
        reportService.verify();
    }

    @Test
    @DisplayName("inn: 14 digits, asked by POST, always SPR-{inn}.zip; SPR down is 503")
    void filesByInn() throws Exception {
        mvc.perform(get("/api/v1/console/reports/inn/123").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("ИНН должен содержать ровно 14 цифр"));

        spr.expect(requestTo(SPR + "/spr/api/v1/file/all-by-inn?inn=12345678901234")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("zip".getBytes(), MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"other.zip\""));
        spr.expect(requestTo(SPR + "/spr/api/v1/file/all-by-inn?inn=00000000000000"))
                .andRespond(withException(new IOException("Connection refused")));
        mvc.perform(get("/api/v1/console/reports/inn/12345678901234").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.CONTENT_DISPOSITION, containsString("SPR-12345678901234.zip")));

        mvc.perform(get("/api/v1/console/reports/inn/00000000000000").header(HttpHeaders.AUTHORIZATION, AUTH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("Сервис недоступен")));
        spr.verify();
    }
}
