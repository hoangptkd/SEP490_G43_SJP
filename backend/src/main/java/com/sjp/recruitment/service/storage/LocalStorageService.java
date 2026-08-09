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
import java.util.Map;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private final Path cvUploadRoot;
    private final Cloudinary cloudinary;

    public LocalStorageService(
            @Value("${app.storage.cv-upload-dir}") String cvUploadDir,
            Cloudinary cloudinary
    ) {
        this.cvUploadRoot = Path.of(cvUploadDir).toAbsolutePath().normalize();
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
        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "sjp/avatars",
                    "resource_type", "image"
            ));
            String fileUrl = (String) uploadResult.get("secure_url");
            return new StoredFile(fileUrl, file.getSize(), file.getContentType());
        } catch (Exception e) {
            throw new IOException("Failed to upload Avatar to Cloudinary", e);
        }
    }
}
