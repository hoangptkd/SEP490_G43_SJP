package com.sjp.recruitment.service.storage;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LocalStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesCandidateCvUnderOpaquePrivateKeyAndLoadsItBack() throws Exception {
        LocalStorageService storage = new LocalStorageService(
                tempDirectory.resolve("cvs").toString(),
                tempDirectory.resolve("avatars").toString(),
                "",
                "",
                "",
                mock(Cloudinary.class)
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "personal-name.pdf",
                "application/pdf",
                "%PDF-1.7 private test".getBytes()
        );

        StorageService.StoredFile stored = storage.storeCandidateCv(UUID.randomUUID(), file);

        assertFalse(stored.storageKey().contains("personal-name"));
        assertFalse(stored.storageKey().startsWith("http"));
        assertEquals("application/pdf", stored.contentType());
        assertTrue(storage.loadCandidateCv(stored.storageKey()).isReadable());
    }

    @Test
    void rejectsAvatarBytesThatCannotBeDecodedAsAnImage() {
        LocalStorageService storage = new LocalStorageService(
                tempDirectory.resolve("cvs").toString(),
                tempDirectory.resolve("avatars").toString(),
                "",
                "",
                "",
                mock(Cloudinary.class)
        );
        MockMultipartFile disguisedFile = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "not an image".getBytes()
        );

        assertThrows(IOException.class, () -> storage.storeUserAvatar(UUID.randomUUID(), disguisedFile));
    }
}
