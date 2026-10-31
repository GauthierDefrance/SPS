package org.sps.backend.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.sps.backend.TestcontainersConfiguration;
import org.sps.backend.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Le parcours complet : inscription, connexion, requêtes avec le token, refresh, déconnexion. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuthApiTest {

    private static final String REFRESH_COOKIE = AuthRestController.REFRESH_COOKIE;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // --- Inscription ---

    @Test
    void registerStoresAHashedPassword() throws Exception {
        register("""
                {"email": "Ada@SPS.dev", "password": "motdepasse123", "name": "Ada", "surname": "Lovelace"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@sps.dev"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = users.findByEmailIgnoreCase("ada@sps.dev").orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2").isNotEqualTo("motdepasse123");
    }

    @Test
    void emailCanOnlyBeUsedOnce() throws Exception {
        register("""
                {"email": "bob@sps.dev", "password": "motdepasse123", "name": "Bob"}""")
                .andExpect(status().isCreated());
        register("""
                {"email": "BOB@sps.dev", "password": "autremotdepasse", "name": "Bob 2"}""")
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationsAreRejected() throws Exception {
        register("""
                {"email": "pas-un-email", "password": "motdepasse123", "name": "Eve"}""").andExpect(status().isBadRequest());
        register("""
                {"email": "eve@sps.dev", "password": "court", "name": "Eve"}""").andExpect(status().isBadRequest());
        register("""
                {"email": "eve@sps.dev", "password": "motdepasse123"}""").andExpect(status().isBadRequest());
        assertThat(users.existsByEmailIgnoreCase("eve@sps.dev")).isFalse();
    }

    // --- Connexion et tokens ---

    @Test
    void loginGivesAnAccessTokenAndASecureRefreshCookie() throws Exception {
        registerAda();

        MvcResult login = login("ADA@sps.dev", "motdepasse123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())   // jamais dans le JSON
                .andReturn();

        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(REFRESH_COOKIE + "=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");

        // Le token d'accès suffit pour les requêtes suivantes, sans renvoyer le mot de passe
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@sps.dev"));
    }

    @Test
    void badCredentialsAndBadTokensAreRejected() throws Exception {
        registerAda();
        login("ada@sps.dev", "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        login("inconnu@sps.dev", "motdepasse123").andExpect(status().isUnauthorized());

        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer pas.un.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheTokenAndLogoutRevokesIt() throws Exception {
        registerAda();
        Cookie firstRefresh = refreshCookie(login("ada@sps.dev", "motdepasse123").andReturn());

        // Refresh : nouveau token d'accès et nouveau cookie
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(firstRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie secondRefresh = refreshCookie(refreshed);
        assertThat(secondRefresh.getValue()).isNotEqualTo(firstRefresh.getValue());

        // L'ancien refresh token a été consommé : il ne marche plus
        mvc.perform(post("/api/auth/refresh").cookie(firstRefresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());

        // Déconnexion : cookie effacé, refresh token révoqué
        mvc.perform(post("/api/auth/logout").cookie(secondRefresh))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/api/auth/refresh").cookie(secondRefresh)).andExpect(status().isUnauthorized());
    }

    // --- Outils ---

    private void registerAda() throws Exception {
        register("""
                {"email": "ada@sps.dev", "password": "motdepasse123", "name": "Ada"}""").andExpect(status().isCreated());
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    private static String accessToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
