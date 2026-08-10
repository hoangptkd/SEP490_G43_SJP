package com.sjp.recruitment.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
        this.cvUploadRoot = Path.of(cvUploadDir).toAbsolutePath().normalize();
        this.avatarUploadRoot = Path.of(avatarUploadDir).toAbsolutePath().normalize();
        this.cloudName = cloudName == null ? "" : cloudName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.cloudinary = cloudinary;
    }

    @Override
    public StoredFile storeCandidateCv(UUID candidateId, MultipartFile file) throws IOException {
        try {
            String contentType = file.getContentType() != null ? file.getContentType() : "application/pdf";
            String resourceType = contentType.contains("pdf") ? "raw" : "auto";
            
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "sjp/cvs",
                    "resource_type", resourceType 
            ));
            String fileUrl = (String) uploadResult.get("secure_url");
            return new StoredFile(fileUrl, file.getSize(), file.getContentType());
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Failed to upload CV to Cloudinary: " + e.getMessage(), e);
        }
    }

    @Override
    public Resource loadCandidateCv(String storageKey) throws IOException {
        // Fallback for old local files during development
        Path target = cvUploadRoot.resolve(storageKey == null ? "" : storageKey).normalize();
        if (!target.startsWith(cvUploadRoot) || !Files.isRegularFile(target)) {
            throw new IOException("CV file not found");
        }
        return new UrlResource(target.toUri());
    }

    @Override
    public StoredFile storeUserAvatar(UUID userId, MultipartFile file) throws IOException {
        if (isCloudinaryConfigured()) {
            try {
                Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                        "folder", "sjp/avatars",
                        "resource_type", "image"
                ));
                String fileUrl = (String) uploadResult.get("secure_url");
                return new StoredFile(fileUrl, file.getSize(), file.getContentType());
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
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile("/api/public/avatars/" + fileName, file.getSize(), file.getContentType());
    }

    private boolean isCloudinaryConfigured() {
        return !cloudName.isBlank()
                && !"demo".equalsIgnoreCase(cloudName)
                && !apiKey.isBlank()
                && !apiSecret.isBlank();
    }

    private String avatarExtension(MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
