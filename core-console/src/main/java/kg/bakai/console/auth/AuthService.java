package kg.bakai.console.auth;

/**
 * Login against SPR. The same contract as SPR's kg.bakai.spr.core.platform.auth.AuthService: moved to SPR, the
 * console controllers use that one and this interface and {@link DemoAuthService} go away.
 */
public interface AuthService {

    /** The username and a session for these credentials; throws with a message the user can read when they are wrong. */
    UsernameSessionResponse authenticateAndGetUsernameAndSession(String username, String password);
}
