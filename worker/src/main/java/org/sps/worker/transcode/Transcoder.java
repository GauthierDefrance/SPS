package org.sps.worker.transcode;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.sps.worker.messaging.ProgressReporter;
import org.sps.worker.storage.S3Storage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleConsumer;
import java.util.stream.Stream;

/**
 * Traite UNE vidéo de bout en bout :
 * <pre>
 *  1. TÉLÉCHARGEMENT  original du bucket raw vers un dossier temporaire       0 →  5 %
 *  2. ANALYSE         ffprobe : dimensions, rotation, durée                         5 %
 *  3. ENCODAGE        une passe FFmpeg par qualité, en segments HLS de 4 s    5 → 90 %
 *  4. ENVOI           playlists et segments vers le bucket hls               90 → 99 %
 * </pre>
 * Résultat dans le stockage :
 * <pre>
 *  {videoId}/master.m3u8        liste des qualités (ce que charge le lecteur)
 *  {videoId}/720p/index.m3u8    liste des segments de la qualité 720p
 *  {videoId}/720p/seg_000.ts    segments de 4 secondes
 *  {videoId}/thumbnail.jpg      miniature par défaut (au tiers de la vidéo)
 *  {videoId}/thumbnails/auto_1.jpg ... auto_3.jpg   autres images proposées au propriétaire
 * </pre>
 * Refaire le traitement d'une même vidéo réécrit les mêmes fichiers : c'est sans danger
 * (utile si RabbitMQ redonne la vidéo à un autre worker après un plantage).
 */
@Service
public class Transcoder {

    private static final Logger log = LoggerFactory.getLogger(Transcoder.class);

    private final S3Storage storage;
    private final TranscodeProperties props;

    /** Processus FFmpeg en cours, pour les arrêter si le worker s'éteint. */
    private final Set<Process> running = ConcurrentHashMap.newKeySet();

    public Transcoder(S3Storage storage, TranscodeProperties props) {
        this.storage = storage;
        this.props = props;
    }

    /** Positions des miniatures candidates, en fraction de la durée (début, milieu, fin). */
    static final double[] THUMBNAIL_CANDIDATES = {0.15, 0.5, 0.85};

    public record Result(List<Integer> renditions, double durationSeconds, int thumbnailCandidates) { }

    public Result transcode(String videoId, String rawKey, ProgressReporter progress)
            throws VideoProcessingException, IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("sps-" + videoId + "-");
        try {
            progress.step("TÉLÉCHARGEMENT", 0);
            Path source = workDir.resolve("source");
            storage.downloadRaw(rawKey, source);

            progress.step("ANALYSE", 5);
            SourceInfo info = probe(source);
            List<Rendition> ladder = Rendition.ladderFor(info.shortSide());
            log.info("{} : {}x{}, {} s -> {}", videoId, info.width(), info.height(), Math.round(info.durationSeconds()),
                    ladder.stream().map(Rendition::name).toList());

            // L'encodage occupe 5 → 90 %. Chaque qualité compte selon son nombre de pixels :
            // le 1080p prend bien plus de temps que le 144p.
            Path hlsDir = workDir.resolve("hls");
            double totalWeight = ladder.stream().mapToDouble(r -> (double) r.height() * r.height()).sum();
            double done = 0;
            for (Rendition rendition : ladder) {
                double start = 5 + 85 * done / totalWeight;
                double span = 85 * rendition.height() * rendition.height() / totalWeight;
                progress.step("ENCODAGE " + rendition.name(), (int) start);
                encode(source, hlsDir, rendition, info, fraction -> progress.progress((int) (start + span * fraction)));
                done += (double) rendition.height() * rendition.height();
            }
            writeMasterPlaylist(hlsDir, ladder, info);
            createThumbnail(source, hlsDir, "thumbnail.jpg", info, 1.0 / 3);
            int candidates = 0;
            for (double at : THUMBNAIL_CANDIDATES) {
                // Numérotées sans trou : si l'une échoue, les suivantes prennent sa place
                if (createThumbnail(source, hlsDir, "thumbnails/auto_" + (candidates + 1) + ".jpg", info, at)) {
                    candidates++;
                }
            }

            progress.step("ENVOI", 90);
            uploadDirectory(hlsDir, videoId, fraction -> progress.progress((int) (90 + 9 * fraction)));

            return new Result(ladder.stream().map(Rendition::height).toList(), info.durationSeconds(), candidates);
        } finally {
            deleteRecursively(workDir);
        }
    }

    /** Dimensions affichées (rotation des vidéos de téléphone comprise) et durée. */
    private SourceInfo probe(Path source) throws VideoProcessingException, IOException, InterruptedException {
        List<String> lines = new ArrayList<>();
        int exit = run(List.of(props.ffprobe(), "-v", "error",
                        "-select_streams", "v:0",
                        "-show_entries", "stream=width,height:stream_side_data=rotation:format=duration",
                        "-of", "default=noprint_wrappers=1",
                        source.toString()),
                lines::add, null);
        if (exit != 0) {
            throw new VideoProcessingException("Fichier vidéo illisible ou format non pris en charge");
        }

        int width = 0, height = 0, rotation = 0;
        double duration = 0;
        for (String line : lines) {
            String[] kv = line.trim().split("=", 2);
            if (kv.length != 2 || kv[1].equals("N/A")) {
                continue;
            }
            switch (kv[0]) {
                case "width" -> width = Integer.parseInt(kv[1]);
                case "height" -> height = Integer.parseInt(kv[1]);
                case "rotation" -> rotation = (int) Double.parseDouble(kv[1]);
                case "duration" -> duration = Double.parseDouble(kv[1]);
                default -> { }
            }
        }
        if (width == 0 || height == 0) {
            throw new VideoProcessingException("Aucune piste vidéo trouvée dans le fichier");
        }
        // FFmpeg applique la rotation au décodage : on raisonne sur l'image telle qu'affichée
        return Math.abs(rotation) % 180 == 90
                ? new SourceInfo(height, width, duration)
                : new SourceInfo(width, height, duration);
    }

    private void encode(Path source, Path hlsDir, Rendition r, SourceInfo info, DoubleConsumer onProgress)
            throws VideoProcessingException, IOException, InterruptedException {
        Path outDir = Files.createDirectories(hlsDir.resolve(r.name()));
        int seg = props.segmentSeconds();
        List<String> command = List.of(props.ffmpeg(), "-hide_banner", "-y",
                "-i", source.toString(),
                "-map", "0:v:0", "-map", "0:a:0?",                 // première piste audio, si elle existe
                "-vf", info.scaleFilter(r),
                "-c:v", "libx264", "-preset", props.preset(), "-profile:v", "main", "-pix_fmt", "yuv420p",
                "-b:v", r.videoKbps() + "k",
                "-maxrate", (int) (r.videoKbps() * 1.07) + "k",
                "-bufsize", (int) (r.videoKbps() * 1.5) + "k",
                // Une image clé toutes les N secondes dans chaque qualité : les segments sont alignés
                // et le lecteur peut changer de qualité à chaque frontière de segment
                "-force_key_frames", "expr:gte(t,n_forced*" + seg + ")",
                "-sc_threshold", "0",
                "-c:a", "aac", "-b:a", r.audioKbps() + "k", "-ac", "2",
                "-f", "hls",
                "-hls_time", String.valueOf(seg),
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", outDir.resolve("seg_%03d.ts").toString(),
                // Progression lisible par un programme sur la sortie standard ("out_time_us=...")
                "-progress", "pipe:1", "-nostats",
                outDir.resolve("index.m3u8").toString());

        Path ffmpegLog = hlsDir.resolveSibling("ffmpeg-" + r.name() + ".log");
        double durationUs = info.durationSeconds() * 1_000_000;
        int exit = run(command, line -> {
            if (line.startsWith("out_time_us=") && durationUs > 0) {
                try {
                    onProgress.accept(Math.clamp(Long.parseLong(line.substring(12)) / durationUs, 0, 1));
                } catch (NumberFormatException ignored) {
                    // "out_time_us=N/A" au tout début
                }
            }
        }, ffmpegLog);
        if (exit != 0) {
            log.error("FFmpeg a échoué ({}) :\n{}", r.name(), tail(ffmpegLog, 15));
            throw new VideoProcessingException("L'encodage en " + r.name() + " a échoué");
        }
    }

    private void writeMasterPlaylist(Path hlsDir, List<Rendition> ladder, SourceInfo info) throws IOException {
        StringBuilder m3u8 = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (Rendition r : ladder) {
            m3u8.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(r.bandwidth())
                    .append(",RESOLUTION=").append(info.resolutionFor(r))
                    .append(",NAME=\"").append(r.name()).append("\"\n")
                    .append(r.name()).append("/index.m3u8\n");
        }
        Files.writeString(hlsDir.resolve("master.m3u8"), m3u8.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Miniature : une image prise à la position "fraction" de la vidéo (1/3 par défaut : rarement
     * noire, contrairement au début), réduite à 720 pixels sur son petit côté pour rester nette en
     * grand. Une miniature ratée ne fait pas échouer la vidéo : renvoie false.
     */
    private boolean createThumbnail(Path source, Path hlsDir, String name, SourceInfo info, double fraction)
            throws InterruptedException {
        Path target = hlsDir.resolve(name);
        Rendition size = new Rendition(Math.min(720, info.shortSide() & ~1), 0, 0);
        double at = info.durationSeconds() > 0 ? info.durationSeconds() * fraction : 0;
        try {
            Files.createDirectories(target.getParent());
            int exit = run(List.of(props.ffmpeg(), "-hide_banner", "-y",
                            "-ss", String.format(java.util.Locale.ROOT, "%.2f", at),   // se placer AVANT -i : rapide
                            "-i", source.toString(),
                            "-frames:v", "1", "-vf", info.scaleFilter(size), "-q:v", "3",
                            target.toString()),
                    line -> { }, hlsDir.resolveSibling("ffmpeg-thumbnail.log"));   // hors de hls/ : pas envoyé
            if (exit != 0 || !Files.exists(target)) {
                log.warn("Miniature {} non générée (code {})", name, exit);
                return false;
            }
            return true;
        } catch (IOException e) {
            log.warn("Miniature {} non générée", name, e);
            return false;
        }
    }

    /** Envoie les segments d'abord et master.m3u8 en dernier : la vidéo n'est "visible" qu'une fois complète. */
    private void uploadDirectory(Path hlsDir, String videoId, DoubleConsumer onProgress) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(hlsDir)) {
            files = walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString().equals("master.m3u8")))
                    .toList();
        }
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String key = videoId + "/" + hlsDir.relativize(file).toString().replace('\\', '/');
            String name = file.getFileName().toString();
            String type = name.endsWith(".m3u8") ? "application/vnd.apple.mpegurl"
                    : name.endsWith(".jpg") ? "image/jpeg"
                    : "video/mp2t";
            storage.uploadHls(key, file, type);
            onProgress.accept((i + 1.0) / files.size());
        }
    }

    /**
     * Lance une commande et lit sa sortie standard ligne par ligne. Les erreurs (stderr, très
     * bavard chez FFmpeg) vont dans errorLog, ou sont mêlées à la sortie si errorLog est null.
     */
    private int run(List<String> command, java.util.function.Consumer<String> onLine, Path errorLog)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (errorLog != null) {
            builder.redirectError(errorLog.toFile());
        } else {
            builder.redirectErrorStream(true);
        }
        Process process = builder.start();
        running.add(process);
        try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = out.readLine()) != null) {
                onLine.accept(line);
            }
            return process.waitFor();
        } finally {
            running.remove(process);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** Arrêt du worker : on ne laisse pas de FFmpeg orphelin. La vidéo, non confirmée, retourne dans la file. */
    @PreDestroy
    void killRunningProcesses() {
        running.forEach(Process::destroyForcibly);
    }

    private static String tail(Path file, int lines) {
        try {
            List<String> all = Files.readAllLines(file);
            return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
        } catch (IOException e) {
            return "(journal illisible)";
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Impossible de supprimer le dossier temporaire {}", dir, e);
        }
    }
}
