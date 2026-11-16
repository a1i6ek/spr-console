package kg.bakai.console.auth;

/** Who logged in and the session the SPR services take with it (as SPR's kg.bakai.spr.core.platform.auth.dto). */
public record UsernameSessionResponse(String username, String session) {
}
