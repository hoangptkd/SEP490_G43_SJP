package com.sjp.recruitment.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private final Path cvUploadRoot;
    private final Path avatarUploadRoot;
    private final Cloudinary cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    public LocalStorageService(
            @Value("${app.storage.cv-upload-dir}") String cvUploadDir,
            @Value("${app.storage.avatar-upload-dir}") String avatarUploadDir,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            Cloudinary cloudinary
    ) {
        this.cvUploadRoot = privateRoot(cvUploadDir, "cvs");
        this.avatarUploadRoot = privateRoot(avatarUploadDir, "avatars");
        this.cloudName = cloudName == null ? "" : cloudName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.cloudinary = cloudinary;
    }

    @Override
    public StoredFile storeCandidateCv(UUID candidateId, MultipartFile file) throws IOException {
        Files.createDirectories(cvUploadRoot);
        String fileName = candidateId + "-" + UUID.randomUUID() + ".pdf";
        Path target = cvUploadRoot.resolve(fileName).normalize();
        if (!target.startsWith(cvUploadRoot)) {
            throw new IOException("Invalid CV path");
        }
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile(fileName, file.getSize(), "application/pdf");
    }

    @Override
    public Resource loadCandidateCv(String storageKey) throws IOException {
        if (storageKey != null && (storageKey.startsWith("https://") || storageKey.startsWith("http://"))) {
            Resource legacyRemoteResource = new UrlResource(storageKey);
            if (!legacyRemoteResource.exists() || !legacyRemoteResource.isReadable()) {
                throw new IOException("Legacy CV file not found");
            }
            return legacyRemoteResource;
        }
        Path target = cvUploadRoot.resolve(storageKey == null ? "" : storageKey).normalize();
        if (!target.startsWith(cvUploadRoot) || !Files.isRegularFile(target)) {
            throw new IOException("CV file not found");
        }
        return new UrlResource(target.toUri());
    }

    @Override
    public StoredFile storeUserAvatar(UUID userId, MultipartFile file) throws IOException {
        byte[] sanitizedBytes = sanitizeAvatar(file);
        if (isCloudinaryConfigured()) {
            try {
                Map uploadResult = cloudinary.uploader().upload(sanitizedBytes, ObjectUtils.asMap(
                        "folder", "sjp/avatars",
                        "resource_type", "image"
                ));
                String fileUrl = (String) uploadResult.get("secure_url");
                return new StoredFile(fileUrl, sanitizedBytes.length, file.getContentType());
            } catch (Exception ignored) {
                // Fall back to local storage so profile updates still work in dev/test environments.
            }
        }
        Files.createDirectories(avatarUploadRoot);
        String fileName = userId + "-" + UUID.randomUUID() + avatarExtension(file);
        Path target = avatarUploadRoot.resolve(fileName).normalize();
        if (!target.startsWith(avatarUploadRoot)) {
            throw new IOException("Invalid avatar path");
        }
        Files.write(target, sanitizedBytes);
        return new StoredFile("/api/public/avatars/" + fileName, sanitizedBytes.length, file.getContentType());
    }

    private boolean isCloudinaryConfigured() {
        return !cloudName.isBlank()
                && !"demo".equalsIgnoreCase(cloudName)
                && !apiKey.isBlank()
                && !apiSecret.isBlank();
    }

    private Path privateRoot(String configuredPath, String type) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of(System.getProperty("java.io.tmpdir"), "sjp-private", type)
                    .toAbsolutePath()
                    .normalize();
        }
        return Path.of(configuredPath).toAbsolutePath().normalize();
    }

    private String avatarExtension(MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return switch (contentType) {
            case "image/png" -> ".png";
            default -> ".jpg";
        };
    }

    private byte[] sanitizeAvatar(MultipartFile file) throws IOException {
        BufferedImage source;
        try (var inputStream = file.getInputStream()) {
            source = ImageIO.read(inputStream);
        }
        if (source == null) {
            throw new IOException("Invalid avatar image");
        }
        boolean png = "image/png".equalsIgnoreCase(file.getContentType());
        BufferedImage sanitized = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = sanitized.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(sanitized, png ? "png" : "jpg", output)) {
                throw new IOException("Unsupported avatar image format");
            }
            return output.toByteArray();
        }
    }
}
