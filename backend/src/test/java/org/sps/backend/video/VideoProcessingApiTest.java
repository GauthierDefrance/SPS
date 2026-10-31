package org.sps.backend.video;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.sps.backend.TestcontainersConfiguration;
import org.sps.backend.processing.ProcessingMessaging;
import org.sps.backend.processing.TranscodeEvent;
import org.sps.backend.processing.TranscodeRequest;
import org.sps.backend.storage.S3StorageService;
import org.sps.backend.user.User;
import org.sps.backend.user.UserRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Le parcours d'une vidéo côté backend, avec un vrai RabbitMQ (Testcontainers) :
 * upload -> message pour les workers -> événements des workers -> base à jour -> lecture HLS.
 * Le stockage S3 est simulé (@MockitoBean) et le worker aussi : on lui "répond" en appelant
 * directement le listener d'événements.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class VideoProcessingApiTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private VideoProcessingListener processingListener;

    @MockitoBean
    private S3StorageService storage;

    private MockMvc mvc;
    private User alice;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        rabbitAdmin.purgeQueue(ProcessingMessaging.TRANSCODE_QUEUE, false);
        alice = new User();
        alice.setEmail("alice@sps.dev");
        alice.setName("Alice");
        alice.setPasswordHash(passwordEncoder.encode("motdepasse123"));
        alice = users.save(alice);
    }

    @Test
    void uploadStoresTheFileAndQueuesAJob() throws Exception {
        String id = upload("Mon voyage", "PUBLIC");

        verify(storage).upload(eq("videos-raw"), eq(id + "/original"), any(), eq(12L), eq("video/mp4"));
        TranscodeRequest job = rabbit.receiveAndConvert(ProcessingMessaging.TRANSCODE_QUEUE, 5_000,
                new ParameterizedTypeReference<>() { });
        assertThat(job).isNotNull();
        assertThat(job.videoId()).isEqualTo(id);
        assertThat(job.rawKey()).isEqualTo(id + "/original");
    }

    @Test
    void uploadRequiresAccountAndAVideoFile() throws Exception {
        mvc.perform(multipart("/api/videos").file(videoFile()).param("title", "Anonyme"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/videos").with(as(alice)).param("title", "Pas une vidéo")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "bonjour".getBytes())))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/videos").file(videoFile()).with(as(alice)))   // titre manquant
                .andExpect(status().isBadRequest());
    }

    @Test
    void workerEventsUpdateTheVideoState() throws Exception {
        String id = upload("Tutoriel", "PUBLIC");

        processingListener.onEvent(progress(id, 40, "ENCODAGE 720p"));
        mvc.perform(get("/api/videos/" + id).with(as(alice)))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.processing.progress").value(40))
                .andExpect(jsonPath("$.processing.step").value("ENCODAGE 720p"))
                .andExpect(jsonPath("$.hlsUrl").doesNotExist());

        processingListener.onEvent(new TranscodeEvent(id, TranscodeEvent.Type.READY, 100, "TERMINÉ",
                List.of(144, 360, 720), 62.5, 3, null, "worker-1", Instant.now()));
        processingListener.onEvent(progress(id, 95, "ENVOI"));   // message en retard : ignoré

        mvc.perform(get("/api/videos/" + id).with(as(alice)))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.processing.progress").value(100))
                .andExpect(jsonPath("$.qualities[0]").value("144p"))
                .andExpect(jsonPath("$.qualities[2]").value("720p"))
                .andExpect(jsonPath("$.duration").value(62.5))
                .andExpect(jsonPath("$.hlsUrl").value("/api/videos/" + id + "/hls/master.m3u8"));
        assertThat(videos.findByPublicId(id).orElseThrow().getProcessedBy()).isEqualTo("worker-1");
    }

    @Test
    void uploadedVideoStaysADraftUntilItsOwnerPublishesIt() throws Exception {
        String id = upload("Brouillon à publier", "PUBLIC");
        User bob = bob();
        mvc.perform(get("/api/videos/" + id).with(as(alice))).andExpect(jsonPath("$.publishedAt").doesNotExist());

        // Pas encore traitée : impossible de la publier
        mvc.perform(post("/api/videos/" + id + "/publish").with(as(alice))).andExpect(status().isConflict());

        processingListener.onEvent(new TranscodeEvent(id, TranscodeEvent.Type.READY, 100, "TERMINÉ",
                List.of(360), 10.0, 3, null, "worker-1", Instant.now()));

        // Prête mais pas publiée : invisible pour les autres, absente du catalogue et de la chaîne
        mvc.perform(get("/api/videos/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/videos/" + id).with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(post("/api/videos/" + id + "/publish").with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/videos")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/users/" + alice.getId() + "/videos")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/users/" + alice.getId())).andExpect(jsonPath("$.videos").value(0));
        mvc.perform(get("/api/users/me/videos").with(as(alice)))
                .andExpect(jsonPath("$.items[0].publishedAt").doesNotExist());

        String published = mvc.perform(post("/api/videos/" + id + "/publish").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String publishedAt = JsonPath.read(published, "$.publishedAt");

        mvc.perform(get("/api/videos/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/videos"))
                .andExpect(jsonPath("$.items[0].publicId").value(id))
                .andExpect(jsonPath("$.items[0].publishedAt").value(publishedAt));
        mvc.perform(get("/api/users/" + alice.getId() + "/videos")).andExpect(jsonPath("$.items[0].publicId").value(id));
        mvc.perform(get("/api/users/" + alice.getId())).andExpect(jsonPath("$.videos").value(1));

        // Republier ne change pas la date de publication
        mvc.perform(post("/api/videos/" + id + "/publish").with(as(alice)))
                .andExpect(jsonPath("$.publishedAt").value(publishedAt));
    }

    @Test
    void hlsFilesAreServedOnlyWhenReadyAndVisible() throws Exception {
        String publicVideo = upload("Publique", "PUBLIC");
        String privateVideo = upload("Privée", "PRIVATE");
        when(storage.read(eq("videos-hls"), anyString()))
                .thenAnswer(call -> new ByteArrayInputStream("#EXTM3U".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/videos/" + publicVideo + "/hls/master.m3u8").with(as(alice))).andExpect(status().isConflict());

        for (String id : List.of(publicVideo, privateVideo)) {
            processingListener.onEvent(new TranscodeEvent(id, TranscodeEvent.Type.READY, 100, "TERMINÉ",
                    List.of(360), 10.0, 3, null, "worker-1", Instant.now()));
            mvc.perform(post("/api/videos/" + id + "/publish").with(as(alice))).andExpect(status().isOk());
        }

        mvc.perform(asyncDispatch(mvc.perform(get("/api/videos/" + publicVideo + "/hls/master.m3u8")).andReturn()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.apple.mpegurl"))
                .andExpect(content().string("#EXTM3U"));
        mvc.perform(get("/api/videos/" + publicVideo + "/hls/360p/seg_000.ts")).andExpect(request().asyncStarted());
        verify(storage).read("videos-hls", publicVideo + "/360p/seg_000.ts");

        // Vidéo privée : invisible pour un anonyme, lisible par sa propriétaire
        mvc.perform(get("/api/videos/" + privateVideo + "/hls/master.m3u8")).andExpect(status().isNotFound());
        mvc.perform(get("/api/videos/" + privateVideo + "/hls/master.m3u8").with(as(alice)))
                .andExpect(request().asyncStarted());

        // Chemins inattendus refusés par la route elle-même
        mvc.perform(get("/api/videos/" + publicVideo + "/hls/360p/..%2F..%2Fsecret.ts")).andExpect(status().is4xxClientError());
    }

    @Test
    void failedVideoCanBeRetriedByItsOwner() throws Exception {
        String id = upload("Fichier abîmé", "PUBLIC");
        processingListener.onEvent(new TranscodeEvent(id, TranscodeEvent.Type.FAILED, 5, "ÉCHEC",
                null, null, null, "Fichier vidéo illisible", "worker-2", Instant.now()));

        mvc.perform(get("/api/videos/" + id).with(as(alice)))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.processing.error").value("Fichier vidéo illisible"));

        rabbitAdmin.purgeQueue(ProcessingMessaging.TRANSCODE_QUEUE, false);
        mvc.perform(post("/api/videos/" + id + "/retry").with(as(alice)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.processing.error").doesNotExist());
        assertThat(rabbit.receive(ProcessingMessaging.TRANSCODE_QUEUE, 5_000)).isNotNull();

        mvc.perform(post("/api/videos/" + id + "/retry").with(as(alice))).andExpect(status().isConflict());
    }

    @Test
    void onlyTheOwnerCanEditOrDeleteAVideo() throws Exception {
        String id = upload("Vidéo publiée", "PUBLIC");
        User bob = bob();
        processingListener.onEvent(new TranscodeEvent(id, TranscodeEvent.Type.READY, 100, "TERMINÉ",
                List.of(360), 10.0, 3, null, "worker-1", Instant.now()));
        mvc.perform(post("/api/videos/" + id + "/publish").with(as(alice))).andExpect(status().isOk());

        mvc.perform(patch("/api/videos/" + id).contentType("application/json").content("{\"title\":\"Piratée\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/videos/" + id).with(as(bob)).contentType("application/json").content("{\"title\":\"Piratée\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/videos/" + id).with(as(alice)).contentType("application/json").content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // Seuls les champs envoyés changent
        mvc.perform(patch("/api/videos/" + id).with(as(alice)).contentType("application/json")
                        .content("{\"title\":\" Mon voyage au ski \",\"policy\":\"UNLISTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mon voyage au ski"))
                .andExpect(jsonPath("$.policy").value("UNLISTED"));
        mvc.perform(patch("/api/videos/" + id).with(as(alice)).contentType("application/json")
                        .content("{\"description\":\"Trois jours dans les Alpes\"}"))
                .andExpect(jsonPath("$.title").value("Mon voyage au ski"))
                .andExpect(jsonPath("$.description").value("Trois jours dans les Alpes"));

        mvc.perform(delete("/api/videos/" + id).with(as(bob))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/videos/" + id).with(as(alice))).andExpect(status().isNoContent());
        mvc.perform(get("/api/videos/" + id)).andExpect(status().isNotFound());
        verify(storage).delete("videos-raw", id + "/original");
        verify(storage).deletePrefix("videos-hls", id + "/");
    }

    @Test
    void filesOfAVideoDeletedDuringProcessingAreCleanedUp() throws Exception {
        String id = upload("Supprimée trop tôt", "PUBLIC");
        mvc.perform(delete("/api/videos/" + id).with(as(alice))).andExpect(status().isNoContent());

        processingListener.onEvent(new TranscodeEvent(id, TranscodeEvent.Type.READY, 100, "TERMINÉ",
                List.of(360), 10.0, 3, null, "worker-1", Instant.now()));
        verify(storage, times(2)).deletePrefix("videos-hls", id + "/");   // à la suppression, puis au READY
    }

    @Test
    void ownerChoosesOrUploadsTheThumbnail() throws Exception {
        String id = upload("Miniatures", "PUBLIC");
        processingListener.onEvent(new TranscodeEvent(id, TranscodeEvent.Type.READY, 100, "TERMINÉ",
                List.of(360), 10.0, 2, null, "worker-1", Instant.now()));   // 2 images candidates
        mvc.perform(post("/api/videos/" + id + "/publish").with(as(alice))).andExpect(status().isOk());
        when(storage.read(eq("videos-hls"), anyString()))
                .thenAnswer(call -> new ByteArrayInputStream(new byte[] {(byte) 0xFF, (byte) 0xD8}));

        mvc.perform(get("/api/videos/" + id).with(as(alice)))
                .andExpect(jsonPath("$.thumbnail.selected").value("AUTO"))
                .andExpect(jsonPath("$.thumbnail.options.length()").value(3))   // AUTO, AUTO_1, AUTO_2
                .andExpect(jsonPath("$.thumbnail.options[2]").value("AUTO_2"));

        // Choisir une image proposée par le worker
        mvc.perform(put("/api/videos/" + id + "/thumbnail").with(as(alice)).contentType("application/json").content("{\"choice\":\"AUTO_3\"}"))
                .andExpect(status().isBadRequest());   // seulement 2 candidates
        String etag = mvc.perform(get("/api/videos/" + id + "/thumbnail.jpg"))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
        mvc.perform(put("/api/videos/" + id + "/thumbnail").with(as(alice)).contentType("application/json").content("{\"choice\":\"AUTO_2\"}"))
                .andExpect(jsonPath("$.thumbnail.selected").value("AUTO_2"));

        // Le cache du navigateur : même ETag -> 304 sans lire le stockage ; nouveau choix -> nouvelle image
        mvc.perform(asyncDispatch(mvc.perform(get("/api/videos/" + id + "/thumbnail.jpg")).andReturn()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, private"));
        verify(storage).read("videos-hls", id + "/thumbnails/auto_2.jpg");
        String newEtag = mvc.perform(get("/api/videos/" + id + "/thumbnail.jpg")).andReturn().getResponse().getHeader("ETag");
        assertThat(newEtag).isNotEqualTo(etag);
        mvc.perform(get("/api/videos/" + id + "/thumbnail.jpg").header("If-None-Match", newEtag))
                .andExpect(status().isNotModified());

        // Envoyer sa miniature : un vrai fichier image, redessiné en JPEG 1280x720
        mvc.perform(multipart("/api/videos/" + id + "/thumbnail").with(as(alice))
                        .file(new MockMultipartFile("file", "faux.png", "image/png", "pas une image".getBytes())))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/videos/" + id + "/thumbnail").with(as(alice)).file(png("ma-miniature.png", 1920, 1200)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thumbnail.selected").value("CUSTOM"))
                .andExpect(jsonPath("$.thumbnail.options[3]").value("CUSTOM"));
        verify(storage).upload(eq("videos-hls"), eq(id + "/thumbnails/custom.jpg"), any(), anyLong(), eq("image/jpeg"));
        mvc.perform(get("/api/videos/" + id + "/thumbnails/custom.jpg")).andExpect(request().asyncStarted());

        // Seul le propriétaire choisit
        User bob = new User();
        bob.setEmail("bob@sps.dev");
        bob.setPasswordHash("x");
        bob = users.save(bob);
        mvc.perform(put("/api/videos/" + id + "/thumbnail").with(as(bob)).contentType("application/json").content("{\"choice\":\"AUTO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/videos/" + id + "/thumbnail").with(as(bob)).file(png("x.png", 64, 64)))
                .andExpect(status().isForbidden());
    }

    @Test
    void avatarIsResizedStoredAndServed() throws Exception {
        when(storage.read(eq("images"), anyString()))
                .thenAnswer(call -> new ByteArrayInputStream(new byte[] {(byte) 0xFF, (byte) 0xD8}));
        mvc.perform(get("/api/users/" + alice.getId() + "/avatar.jpg")).andExpect(status().isNotFound());

        String body = mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/avatar").with(as(alice)).file(png("moi.png", 800, 600)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String avatarUrl = JsonPath.read(body, "$.avatarUrl");
        assertThat(avatarUrl).startsWith("/api/users/" + alice.getId() + "/avatar.jpg?v=");
        verify(storage).upload(eq("images"), eq("avatars/" + alice.getId() + ".jpg"), any(), anyLong(), eq("image/jpeg"));

        // La photo apparaît partout où l'utilisateur est cité (ici, propriétaire d'une vidéo)
        String id = upload("Avec photo", "PUBLIC");
        mvc.perform(get("/api/videos/" + id).with(as(alice))).andExpect(jsonPath("$.owner.avatarUrl").value(avatarUrl));
        mvc.perform(get("/api/users/" + alice.getId())).andExpect(jsonPath("$.avatarUrl").value(avatarUrl));
        mvc.perform(get("/api/users/" + alice.getId() + "/avatar.jpg"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));

        mvc.perform(delete("/api/users/me/avatar").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
        verify(storage).delete("images", "avatars/" + alice.getId() + ".jpg");
        mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/avatar").file(png("anonyme.png", 10, 10)))
                .andExpect(status().isUnauthorized());
    }

    // --- Outils ---

    /** Une vraie image PNG de la taille demandée, fabriquée en mémoire. */
    private static MockMultipartFile png(String name, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("file", name, "image/png", bytes.toByteArray());
    }

    private String upload(String title, String policy) throws Exception {
        String body = mvc.perform(multipart("/api/videos").file(videoFile()).with(as(alice))
                        .param("title", title).param("policy", policy))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.processing.step").value("EN ATTENTE"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private User bob() {
        User bob = new User();
        bob.setEmail("bob@sps.dev");
        bob.setPasswordHash(passwordEncoder.encode("motdepasse123"));
        return users.save(bob);
    }

    private static MockMultipartFile videoFile() {
        return new MockMultipartFile("file", "voyage.mp4", "video/mp4", "fausse vidéo".getBytes(StandardCharsets.US_ASCII));
    }

    private static TranscodeEvent progress(String id, int percent, String step) {
        return new TranscodeEvent(id, TranscodeEvent.Type.PROGRESS, percent, step, null, null, null, null, "worker-1", Instant.now());
    }

    private RequestPostProcessor as(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()));
    }
}
