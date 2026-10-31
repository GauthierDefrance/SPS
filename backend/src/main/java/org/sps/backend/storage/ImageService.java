package org.sps.backend.storage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/*
 * Prépare les images envoyées par les utilisateurs (miniatures, photos de profil) : on ne stocke
 * jamais le fichier tel quel, on le redessine en JPEG à une taille fixe.
 *  - sécurité : seul un fichier réellement décodable comme image passe (pas un .exe renommé en .png),
 *    et ce qu'on sert ensuite est un JPEG produit par nous, sans métadonnées (EXIF : position GPS...) ;
 *  - poids : une photo de 8 Mo devient une image de quelques dizaines de Ko.
 *
 * Formats lus : ceux de Java (JPEG, PNG, GIF, BMP). Le WebP n'en fait pas partie.
 */
@Service
public class ImageService {

    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024;   // 10 Mo
    // Une image de 1 Ko peut annoncer 50 000 × 50 000 pixels et demander 10 Go de mémoire au décodage
    // ("bombe de décompression") : on lit d'abord les dimensions, avant de décoder quoi que ce soit.
    private static final long MAX_PIXELS = 40_000_000;            // 40 mégapixels
    private static final float JPEG_QUALITY = 0.85f;

    /**
     * L'image redimensionnée pour remplir exactement width × height (comme object-fit: cover) :
     * réduite puis recadrée au centre. Jamais agrandie au-delà de sa taille d'origine.
     */
    public byte[] toJpeg(MultipartFile file, int width, int height) {
        if (file.isEmpty()) {
            throw badRequest("Le fichier est vide");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("L'image dépasse 10 Mo");
        }
        BufferedImage source = read(file);
        return writeJpeg(cover(source, width, height));
    }

    private BufferedImage read(MultipartFile file) {
        try (InputStream in = file.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw badRequest("Format d'image non reconnu (JPEG, PNG, GIF ou BMP)");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw badRequest("Image trop grande (40 mégapixels au maximum)");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ResponseStatusException rse) {
                throw rse;
            }
            throw badRequest("Image illisible ou abîmée");
        }
    }

    private static BufferedImage cover(BufferedImage source, int width, int height) {
        // Partie de l'image source à garder : la plus grande zone au centre qui a le bon rapport largeur / hauteur
        double targetRatio = (double) width / height;
        int cropWidth = source.getWidth();
        int cropHeight = source.getHeight();
        if ((double) cropWidth / cropHeight > targetRatio) {
            cropWidth = (int) Math.round(cropHeight * targetRatio);    // trop large : on coupe les côtés
        } else {
            cropHeight = (int) Math.round(cropWidth / targetRatio);    // trop haute : on coupe en haut et en bas
        }
        int x = (source.getWidth() - cropWidth) / 2;
        int y = (source.getHeight() - cropHeight) / 2;

        // Pas d'agrandissement : une petite image reste petite (le navigateur l'étire si besoin)
        int outWidth = Math.min(width, cropWidth);
        int outHeight = Math.max(1, (int) Math.round(outWidth / targetRatio));

        // TYPE_INT_RGB : le JPEG n'a pas de transparence, on pose l'image sur un fond blanc
        BufferedImage out = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, outWidth, outHeight);
            g.drawImage(source, 0, 0, outWidth, outHeight, x, y, x + cropWidth, y + cropHeight, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Encodage JPEG impossible", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
