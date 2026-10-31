package org.sps.backend.comment;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.sps.backend.TestcontainersConfiguration;
import org.sps.backend.user.User;
import org.sps.backend.user.UserRepository;
import org.sps.backend.video.TranscodeStatus;
import org.sps.backend.video.Video;
import org.sps.backend.video.VideoPolicy;
import org.sps.backend.video.VideoRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Commentaires : écrire, lister, modifier, supprimer, liker ; et qui a le droit de le faire. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CommentApiTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;
    private User alice;     // propriétaire de la vidéo
    private User bob;
    private User carol;
    private Video video;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alice = user("alice@sps.dev", "Alice");
        bob = user("bob@sps.dev", "Bob");
        carol = user("carol@sps.dev", "Carol");
        video = newVideo(VideoPolicy.PUBLIC);
    }

    @Test
    void anyoneReadsCommentsButOnlyMembersWrite() throws Exception {
        mvc.perform(post(commentsUrl()).contentType("application/json").content("{\"content\":\"Anonyme\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(commentsUrl()).with(as(bob)).contentType("application/json").content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        comment(bob, "  Première !  ");
        comment(carol, "Super vidéo");

        mvc.perform(get(commentsUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].content").value("Super vidéo"))      // plus récent d'abord
                .andExpect(jsonPath("$.items[1].content").value("Première !"))
                .andExpect(jsonPath("$.items[1].author.name").value("Bob"))
                .andExpect(jsonPath("$.items[1].editedAt").doesNotExist());
    }

    @Test
    void reactionsCanBeAddedChangedAndRemoved() throws Exception {
        String id = comment(bob, "Qui est là en 2026 ?");
        String reaction = "/api/comments/" + id + "/reaction";

        mvc.perform(put(reaction).with(as(carol)).contentType("application/json").content("{\"type\":\"LIKE\"}"))
                .andExpect(jsonPath("$.likes").value(1))
                .andExpect(jsonPath("$.myReaction").value("LIKE"));
        mvc.perform(put(reaction).with(as(alice)).contentType("application/json").content("{\"type\":\"LIKE\"}"))
                .andExpect(jsonPath("$.likes").value(2));
        mvc.perform(put(reaction).with(as(carol)).contentType("application/json").content("{\"type\":\"DISLIKE\"}"))
                .andExpect(jsonPath("$.likes").value(1))
                .andExpect(jsonPath("$.dislikes").value(1));

        mvc.perform(get(commentsUrl()).with(as(carol))).andExpect(jsonPath("$.items[0].myReaction").value("DISLIKE"));
        mvc.perform(get(commentsUrl())).andExpect(jsonPath("$.items[0].myReaction").doesNotExist());

        mvc.perform(delete(reaction).with(as(carol)))
                .andExpect(jsonPath("$.likes").value(1))
                .andExpect(jsonPath("$.dislikes").value(0))
                .andExpect(jsonPath("$.myReaction").doesNotExist());

        // Tri par popularité : le commentaire aimé passe devant un plus récent
        comment(carol, "Plus récent, mais pas aimé");
        mvc.perform(get(commentsUrl()).param("sort", "TOP")).andExpect(jsonPath("$.items[0].id").value(id));
    }

    @Test
    void onlyTheAuthorEditsAndTheAuthorOrVideoOwnerDeletes() throws Exception {
        String id = comment(bob, "Premier jet");

        mvc.perform(put("/api/comments/" + id).with(as(carol)).contentType("application/json").content("{\"content\":\"Piraté\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/comments/" + id).with(as(bob)).contentType("application/json").content("{\"content\":\"Version corrigée\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Version corrigée"))
                .andExpect(jsonPath("$.editedAt").exists());

        mvc.perform(delete("/api/comments/" + id).with(as(carol))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/" + id).with(as(bob))).andExpect(status().isNoContent());

        // La propriétaire de la vidéo modère les commentaires des autres
        String other = comment(carol, "Commentaire déplacé");
        mvc.perform(delete("/api/comments/" + other).with(as(alice))).andExpect(status().isNoContent());
        mvc.perform(get(commentsUrl())).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(delete("/api/comments/" + other).with(as(alice))).andExpect(status().isNotFound());
    }

    @Test
    void commentsOfAPrivateVideoAreHiddenLikeTheVideo() throws Exception {
        video = newVideo(VideoPolicy.PRIVATE);
        String id = comment(alice, "Note pour moi-même");

        mvc.perform(get(commentsUrl())).andExpect(status().isNotFound());
        mvc.perform(get(commentsUrl()).with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(post(commentsUrl()).with(as(bob)).contentType("application/json").content("{\"content\":\"Coucou\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/comments/" + id + "/reaction").with(as(bob)).contentType("application/json").content("{\"type\":\"LIKE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(commentsUrl()).with(as(alice))).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void repliesStayOnOneLevelAndMarkTheVideoOwner() throws Exception {
        String root = comment(bob, "Où est-ce tourné ?");
        String aliceReply = reply(root, alice, "Dans les Alpes !");
        reply(aliceReply, carol, "Magnifique");   // réponse à une réponse : rattachée au commentaire principal

        mvc.perform(post("/api/comments/" + root + "/replies").contentType("application/json").content("{\"content\":\"Anonyme\"}"))
                .andExpect(status().isUnauthorized());

        // La liste principale ne contient pas les réponses, mais l'en-tête les compte
        mvc.perform(get(commentsUrl()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalComments").value(3))
                .andExpect(jsonPath("$.items[0].replyCount").value(2))
                .andExpect(jsonPath("$.items[0].byVideoOwner").value(false));

        // Réponses lisibles sans compte, de la plus ancienne à la plus récente
        mvc.perform(get("/api/comments/" + root + "/replies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].content").value("Dans les Alpes !"))
                .andExpect(jsonPath("$.items[0].byVideoOwner").value(true))
                .andExpect(jsonPath("$.items[0].parentId").value(root))
                .andExpect(jsonPath("$.items[1].content").value("Magnifique"))
                .andExpect(jsonPath("$.items[1].parentId").value(root));

        // Supprimer une réponse décrémente le compteur ; supprimer le commentaire principal emporte ses réponses
        mvc.perform(delete("/api/comments/" + aliceReply).with(as(alice))).andExpect(status().isNoContent());
        mvc.perform(get(commentsUrl())).andExpect(jsonPath("$.items[0].replyCount").value(1));
        mvc.perform(delete("/api/comments/" + root).with(as(bob))).andExpect(status().isNoContent());
        mvc.perform(get(commentsUrl())).andExpect(jsonPath("$.totalComments").value(0));
    }

    // --- Outils ---

    private String reply(String commentId, User author, String content) throws Exception {
        String body = mvc.perform(post("/api/comments/" + commentId + "/replies").with(as(author))
                        .contentType("application/json").content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String commentsUrl() {
        return "/api/videos/" + video.getPublicId() + "/comments";
    }

    private String comment(User author, String content) throws Exception {
        String body = mvc.perform(post(commentsUrl()).with(as(author)).contentType("application/json")
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(content.strip()))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private User user(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode("motdepasse123"));
        return users.save(user);
    }

    private Video newVideo(VideoPolicy policy) {
        Video v = new Video();
        v.setOwner(alice);
        v.setTitle("Vidéo commentée");
        v.setPolicy(policy);
        v.setStatus(TranscodeStatus.READY);
        v.setPublishedAt(java.time.Instant.now());
        return videos.save(v);
    }

    private RequestPostProcessor as(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()));
    }
}
