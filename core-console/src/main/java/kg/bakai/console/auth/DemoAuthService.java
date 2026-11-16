package kg.bakai.console.auth;

import org.springframework.stereotype.Service;

import java.util.UUID;

/** Demo login: any username with any non-empty password gets a new session. SPR has the real one. */
@Service
public class DemoAuthService implements AuthService {

    @Override
    public UsernameSessionResponse authenticateAndGetUsernameAndSession(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Неверный логин или пароль");
        }
        return new UsernameSessionResponse(username.trim(), UUID.randomUUID().toString());
    }
}
