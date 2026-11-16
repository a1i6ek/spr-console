package kg.bakai.console.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import kg.bakai.console.auth.AuthService;
import kg.bakai.console.auth.UsernameSessionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login of the console, without a server session: the answer is the username and the session, and every later call
 * sends them back as {@code Authorization: Basic base64(username:session)} (see {@link ReportController}).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/console/auth")
public class ConsoleAuthController {

    private final AuthService authService;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    @PostMapping("/login")
    public UsernameSessionResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            return authService.authenticateAndGetUsernameAndSession(request.username().trim(), request.password());
        } catch (RuntimeException e) {
            log.info("Вход не выполнен для {}: {}", request.username(), e.getMessage());
            String message = e.getMessage() != null ? e.getMessage() : "Неверный логин или пароль";
            throw new ErrorResponseException(HttpStatus.UNAUTHORIZED,
                    ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message), e);
        }
    }
}
