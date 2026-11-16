package kg.bakai.console.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.function.Function;

/**
 * The SPR endpoints behind the reports (the ones the SPR WebController calls), with the user's Authorization passed
 * on. An answer that is not the file becomes an {@link ErrorResponseException}: Spring writes it as a ProblemDetail
 * whose detail is the message the WebController showed.
 *
 * <p>Its RestTemplates are its own, not beans: a project that already has a RestTemplate bean keeps injecting it.
 */
@Component
public class SprReportClient {

    private final RestTemplate restTemplate;
    /** The report service builds a month for minutes. */
    private final RestTemplate longRestTemplate;
    private final String apiBaseUrl;
    private final String reportUrl;

    @Autowired
    public SprReportClient(@Value("${server.base.url:http://localhost:8060}") String apiBaseUrl,
                           @Value("${report.url:http://localhost:8064}") String reportUrl) {
        this(restTemplate(Duration.ofMinutes(2)), restTemplate(Duration.ofMinutes(10)), apiBaseUrl, reportUrl);
    }

    /** For tests: RestTemplates bound to a fake server. */
    SprReportClient(RestTemplate restTemplate, RestTemplate longRestTemplate, String apiBaseUrl, String reportUrl) {
        this.restTemplate = restTemplate;
        this.longRestTemplate = longRestTemplate;
        this.apiBaseUrl = apiBaseUrl;
        this.reportUrl = reportUrl;
    }

    private static RestTemplate restTemplate(Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    /** The file of one check. */
    public ResponseEntity<byte[]> file(long scoringId, String authorization) {
        String url = apiBaseUrl + "/spr/api/v1/file/" + scoringId;
        return call(restTemplate, url, HttpMethod.GET, authorization, e -> switch (e.getStatusCode().value()) {
            case 404 -> problem(HttpStatus.NOT_FOUND, "Файл со scoringId " + scoringId + " не найден");
            case 403 -> problem(HttpStatus.FORBIDDEN, "Недостаточно прав");
            default -> fallback(e, "Не удалось получить файл: ");
        }, "Не удалось получить файл: ", HttpStatus.BAD_GATEWAY);
    }

    /** The files of every check of a period, zipped. */
    public ResponseEntity<byte[]> files(LocalDate from, LocalDate to, String authorization) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl + "/spr/api/v1/file/all")
                .queryParam("from", from).queryParam("to", to).toUriString();
        return call(restTemplate, url, HttpMethod.GET, authorization, e -> e.getStatusCode().value() == 400
                ? problem(HttpStatus.BAD_REQUEST, body(e, "Ошибка 400"))
                : fallback(e, "Не удалось получить файлы: "), "Не удалось получить файлы: ", HttpStatus.BAD_GATEWAY);
    }

    /** The general report in Excel, built by the report service. */
    public ResponseEntity<byte[]> generalReport(LocalDate from, LocalDate to, String authorization) {
        String url = UriComponentsBuilder.fromUriString(reportUrl + "/api/export/excel")
                .queryParam("from", from).queryParam("to", to).toUriString();
        return call(longRestTemplate, url, HttpMethod.GET, authorization,
                e -> fallback(e, "Не удалось получить отчёт: "), "Не удалось получить отчёт: ", HttpStatus.BAD_GATEWAY);
    }

    /** The files of every check of a client, zipped. The service's own answer is passed on, as the WebController did. */
    public ResponseEntity<byte[]> filesByInn(String inn, String authorization) {
        String url = UriComponentsBuilder.fromUriString(apiBaseUrl + "/spr/api/v1/file/all-by-inn")
                .queryParam("inn", inn).toUriString();
        return call(restTemplate, url, HttpMethod.POST, authorization,
                e -> problem(e.getStatusCode(), body(e, "Ошибка " + e.getStatusCode().value())),
                "Сервис недоступен: ", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static ResponseEntity<byte[]> call(RestTemplate template, String url, HttpMethod method, String authorization,
                                               Function<HttpStatusCodeException, ErrorResponseException> onError,
                                               String unreachable, HttpStatus unreachableStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        try {
            return template.exchange(url, method, new HttpEntity<>(headers), byte[].class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401) {
                throw problem(HttpStatus.UNAUTHORIZED, "Сессия истекла. Войдите снова.");
            }
            throw onError.apply(e);
        } catch (ResourceAccessException e) {
            throw problem(unreachableStatus, unreachable + e.getMessage());
        }
    }

    /** A client error as "Ошибка 409: Conflict"; a server error as a failure to get the file. */
    private static ErrorResponseException fallback(HttpStatusCodeException e, String failure) {
        return e.getStatusCode().is4xxClientError()
                ? problem(e.getStatusCode(), "Ошибка " + e.getStatusCode().value() + ": " + e.getStatusText())
                : problem(HttpStatus.BAD_GATEWAY, failure + "Ошибка " + e.getStatusCode().value() + ": " + e.getStatusText());
    }

    private static String body(HttpStatusCodeException e, String fallback) {
        String body = new String(e.getResponseBodyAsByteArray(), StandardCharsets.UTF_8).trim();
        return body.isEmpty() ? fallback : body;
    }

    static ErrorResponseException problem(HttpStatusCode status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
