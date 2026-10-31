package org.sps.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.sps.backend.user.User;
import org.sps.backend.user.UserRepository;
import org.sps.backend.video.TranscodeStatus;
import org.sps.backend.video.Video;
import org.sps.backend.video.VideoPolicy;
import org.sps.backend.video.VideoRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste l'API comme un client HTTP : MockMvc envoie de vraies requêtes aux contrôleurs
 * (sécurité comprise), sans démarrer de serveur. Base PostgreSQL jetable (Testcontainers).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserAndVideoApiTest {

    private static final String PASSWORD = "motdepasse123";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;
    private User alice;     // propriétaire des vidéos
    private User bob;       // spectateur
    private Video publicVideo;
    private Video privateVideo;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user("alice@sps.dev", "Alice");
        bob = user("bob@sps.dev", "Bob");
        publicVideo = video("Recette de crêpes", VideoPolicy.PUBLIC);
        privateVideo = video("Brouillon", VideoPolicy.PRIVATE);
    }

    @Test
    void anonymousCanBrowseButNotAct() throws Exception {
        mvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Recette de crêpes"));
        mvc.perform(get("/api/videos?q=CRÊPES")).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/videos/" + publicVideo.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicVideo.getPublicId()))
                .andExpect(jsonPath("$.owner.name").value("Alice"))
                .andExpect(jsonPath("$.owner.email").doesNotExist());
        mvc.perform(post("/api/videos/" + publicVideo.getPublicId() + "/view")).andExpect(status().isNoContent());

        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/videos/" + publicVideo.getPublicId() + "/reaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LIKE\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void privateVideoIsOnlyVisibleToItsOwner() throws Exception {
        String url = "/api/videos/" + privateVideo.getPublicId();
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(url).with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(get(url).with(as(alice))).andExpect(status().isOk());
        mvc.perform(get("/api/users/me/videos").with(as(alice))).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/users/" + alice.getId() + "/videos")).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void likesAndDislikes() throws Exception {
        String url = "/api/videos/" + publicVideo.getPublicId() + "/reaction";

        react(url, "LIKE").andExpect(jsonPath("$.likes").value(1)).andExpect(jsonPath("$.myReaction").value("LIKE"));
        react(url, "LIKE").andExpect(jsonPath("$.likes").value(1));          // liker deux fois ne compte qu'une fois
        react(url, "DISLIKE").andExpect(jsonPath("$.likes").value(0)).andExpect(jsonPath("$.dislikes").value(1));
        react(url, "LIKE");

        mvc.perform(get("/api/videos/" + publicVideo.getPublicId()).with(as(bob)))
                .andExpect(jsonPath("$.likes").value(1))
                .andExpect(jsonPath("$.myReaction").value("LIKE"));
        mvc.perform(get("/api/users/me/likes").with(as(bob)))
                .andExpect(jsonPath("$.items[0].publicId").value(publicVideo.getPublicId()));

        mvc.perform(delete(url).with(as(bob))).andExpect(jsonPath("$.likes").value(0));
        mvc.perform(get("/api/users/me/likes").with(as(bob))).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void historyAndResumePosition() throws Exception {
        String id = publicVideo.getPublicId();
        mvc.perform(post("/api/videos/" + id + "/view").with(as(bob))).andExpect(status().isNoContent());
        mvc.perform(put("/api/users/me/history/" + id).with(as(bob)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionSeconds\": 42.5}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/videos/" + id + "/view").with(as(bob)));   // revoir ne crée pas de doublon

        mvc.perform(get("/api/users/me/history").with(as(bob)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].positionSeconds").value(42.5));
        mvc.perform(get("/api/videos/" + id)).andExpect(jsonPath("$.views").value(2));

        mvc.perform(delete("/api/users/me/history").with(as(bob))).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me/history").with(as(bob))).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void subscriptions() throws Exception {
        String subscription = "/api/users/me/subscriptions/" + alice.getId();
        mvc.perform(put(subscription).with(as(bob))).andExpect(status().isNoContent());
        mvc.perform(put(subscription).with(as(bob))).andExpect(status().isNoContent());   // idempotent

        mvc.perform(get("/api/users/me/subscriptions").with(as(bob)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Alice"));
        mvc.perform(get("/api/users/" + alice.getId()))
                .andExpect(jsonPath("$.subscribers").value(1))
                .andExpect(jsonPath("$.videos").value(1));
        mvc.perform(get("/api/users/me").with(as(bob)))
                .andExpect(jsonPath("$.email").value("bob@sps.dev"))
                .andExpect(jsonPath("$.subscriptions").value(1));

        mvc.perform(put("/api/users/me/subscriptions/" + bob.getId()).with(as(bob))).andExpect(status().isBadRequest());
        mvc.perform(delete(subscription).with(as(bob))).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/" + alice.getId())).andExpect(jsonPath("$.subscribers").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions react(String url, String type) throws Exception {
        return mvc.perform(put(url).with(as(bob)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\"}")).andExpect(status().isOk());
    }

    /**
     * Simule une requête avec un token d'accès valide pour cet utilisateur ("sub" = son id).
     * Le vrai parcours login -> token -> requête est testé dans AuthApiTest.
     */
    private RequestPostProcessor as(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()));
    }

    private User user(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.save(user);
    }

    private Video video(String title, VideoPolicy policy) {
        Video video = new Video();
        video.setOwner(alice);
        video.setTitle(title);
        video.setPolicy(policy);
        video.setStatus(TranscodeStatus.READY);
        video.setPublishedAt(java.time.Instant.now());
        return videos.save(video);
    }
}
