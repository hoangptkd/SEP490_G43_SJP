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

    private final Path uploadRoot;

    public LocalStorageService(@Value("${app.storage.cv-upload-dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile storeCandidateCv(UUID candidateId, MultipartFile file) throws IOException {
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "cv.pdf" : file.getOriginalFilename());
        String extension = original.toLowerCase().endsWith(".pdf") ? ".pdf" : "";
        String fileName = UUID.randomUUID() + extension;
        Path candidateDir = uploadRoot.resolve(candidateId.toString()).normalize();
        Files.createDirectories(candidateDir);
        Path target = candidateDir.resolve(fileName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IOException("Invalid storage path");
        }
        file.transferTo(target);
        return new StoredFile(uploadRoot.relativize(target).toString().replace('\\', '/'), file.getSize(), file.getContentType());
    }

    @Override
    public Resource loadCandidateCv(String storageKey) throws IOException {
        Path target = uploadRoot.resolve(storageKey == null ? "" : storageKey).normalize();
        if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) {
            throw new IOException("CV file not found");
        }
        return new UrlResource(target.toUri());
    }
}
