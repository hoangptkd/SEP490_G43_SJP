package com.sjp.recruitment.service.storage;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.UUID;

public interface StorageService {
    StoredFile storeCandidateCv(UUID candidateId, MultipartFile file) throws IOException;
    Resource loadCandidateCv(String storageKey) throws IOException;

    record StoredFile(String storageKey, long fileSize, String contentType) {
    }
}
