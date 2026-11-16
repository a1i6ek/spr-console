package kg.bakai.console.web;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * The reports of the console as a REST API: the SPR WebController's report pages, without pages, redirects or a server
 * session. Every call sends {@code Authorization: Basic base64(username:session)} (the answer of the login, see
 * {@link ConsoleAuthController}), which is passed on to SPR. The answer is the file; anything else is a ProblemDetail
 * whose {@code detail} is the message to show, with the WebController's wording.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/console/reports")
public class ReportController {

    /** The longest archive period, in days between its first and last day. */
    static final int ARCHIVE_MAX_DAYS = 3;

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SprReportClient client;

    /** The file of one check. */
    @GetMapping("/files/{scoringId}")
    public ResponseEntity<byte[]> file(@PathVariable long scoringId,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        ResponseEntity<byte[]> answer = client.file(scoringId, requireAuthorization(authorization));
        MediaType type = answer.getHeaders().getContentType();
        return file(answer, type != null ? type : MediaType.APPLICATION_OCTET_STREAM, "scoring_" + scoringId, false);
    }

    /** The files of every check of a period (at most 3 days), zipped. */
    @GetMapping("/files")
    public ResponseEntity<byte[]> files(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String auth = requireAuthorization(authorization);
        if (from.isAfter(to)) {
            throw badRequest("Начальная дата не может быть позже конечной");
        }
        if (ChronoUnit.DAYS.between(from, to) > ARCHIVE_MAX_DAYS) {
            throw badRequest("Период не должен превышать 3 календарных дня");
        }
        return file(client.files(from, to, auth), ZIP, "files--" + from + "--" + to + ".zip", false);
    }

    /** The general report of a month ("2026-11"), in Excel. */
    @GetMapping("/general")
    public ResponseEntity<byte[]> generalReport(@RequestParam String month,
                                                @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String auth = requireAuthorization(authorization);
        YearMonth period;
        try {
            period = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw badRequest("Неверный формат периода");
        }
        return file(client.generalReport(period.atDay(1), period.atEndOfMonth(), auth), XLSX,
                "scoring-report-" + month + ".xlsx", false);
    }

    /** The files of every check of a client, zipped. */
    @GetMapping("/inn/{inn}")
    public ResponseEntity<byte[]> filesByInn(@PathVariable String inn,
                                             @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String auth = requireAuthorization(authorization);
        if (!inn.matches("\\d{14}")) {
            throw badRequest("ИНН должен содержать ровно 14 цифр");
        }
        return file(client.filesByInn(inn, auth), ZIP, "SPR-" + inn + ".zip", true);
    }

    /** The file as SPR sent it, with its type and name; the name given here when SPR sends none (or always). */
    private static ResponseEntity<byte[]> file(ResponseEntity<byte[]> answer, MediaType type, String fileName,
                                               boolean alwaysThisName) {
        String disposition = answer.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (disposition == null || alwaysThisName) {
            disposition = ContentDisposition.attachment().filename(fileName).build().toString();
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(answer.getBody());
    }

    private static String requireAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw SprReportClient.problem(HttpStatus.UNAUTHORIZED, "Требуется авторизация");
        }
        return authorization;
    }

    private static ErrorResponseException badRequest(String detail) {
        return SprReportClient.problem(HttpStatus.BAD_REQUEST, detail);
    }
}
