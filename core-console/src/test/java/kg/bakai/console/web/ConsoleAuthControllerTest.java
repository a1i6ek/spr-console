package kg.bakai.console.web;

import kg.bakai.common.exception.GlobalExceptionHandler;
import kg.bakai.console.auth.DemoAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ConsoleAuthControllerTest {

    private final MockMvc mvc = standaloneSetup(new ConsoleAuthController(new DemoAuthService()))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void loginAnswersTheUsernameAndASession() throws Exception {
        mvc.perform(post("/api/v1/console/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \" aaltynbek \", \"password\": \"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("aaltynbek"))
                .andExpect(jsonPath("$.session").isNotEmpty());
    }

    @Test
    void missingCredentialsAreRefused() throws Exception {
        mvc.perform(post("/api/v1/console/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"aaltynbek\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
