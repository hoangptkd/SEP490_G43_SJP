package com.sjp.recruitment.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private final Path cvUploadRoot;
    private final Path avatarUploadRoot;

    public LocalStorageService(
            @Value("${app.storage.cv-upload-dir}") String cvUploadDir,
            @Value("${app.storage.avatar-upload-dir:uploads/avatars}") String avatarUploadDir
    ) {
        this.cvUploadRoot = Path.of(cvUploadDir).toAbsolutePath().normalize();
        this.avatarUploadRoot = Path.of(avatarUploadDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile storeCandidateCv(UUID candidateId, MultipartFile file) throws IOException {
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "cv.pdf" : file.getOriginalFilename());
        String extension = original.toLowerCase().endsWith(".pdf") ? ".pdf" : "";
        return storeInUserDirectory(cvUploadRoot, candidateId, file, extension);
    }

    @Override
    public Resource loadCandidateCv(String storageKey) throws IOException {
        Path target = cvUploadRoot.resolve(storageKey == null ? "" : storageKey).normalize();
        if (!target.startsWith(cvUploadRoot) || !Files.isRegularFile(target)) {
            throw new IOException("CV file not found");
        }
        return new UrlResource(target.toUri());
    }

    @Override
    public StoredFile storeUserAvatar(UUID userId, MultipartFile file) throws IOException {
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "avatar" : file.getOriginalFilename());
        String lower = original.toLowerCase();
        String extension = lower.endsWith(".png") ? ".png"
                : lower.endsWith(".webp") ? ".webp"
                : lower.endsWith(".jpeg") ? ".jpeg"
                : lower.endsWith(".jpg") ? ".jpg"
                : ".jpg";
        return storeInUserDirectory(avatarUploadRoot, userId, file, extension);
    }

    private StoredFile storeInUserDirectory(Path root, UUID ownerId, MultipartFile file, String extension) throws IOException {
        String fileName = UUID.randomUUID() + extension;
        Path ownerDir = root.resolve(ownerId.toString()).normalize();
        Files.createDirectories(ownerDir);
        Path target = ownerDir.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Invalid storage path");
        }
        file.transferTo(target);
        return new StoredFile(root.relativize(target).toString().replace('\\', '/'), file.getSize(), file.getContentType());
    }
}
