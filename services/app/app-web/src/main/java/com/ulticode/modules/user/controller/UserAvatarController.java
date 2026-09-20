package com.ulticode.modules.user.controller;

import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.storage.StorageKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated read proxy for private avatar objects. */
@RestController
@RequestMapping("/users/avatars")
@RequiredArgsConstructor
public class UserAvatarController {

    private final FileStoragePort fileStorage;

    @GetMapping("/{accountId}/{objectName:.+}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String accountId,
                                             @PathVariable String objectName) {
        String key;
        try {
            key = StorageKeys.avatarKey(accountId, objectName);
            if (!StorageKeys.isAvatarKey(key)
                    || !accountId.equals(StorageKeys.avatarAccountId(key))
                    || !objectName.equals(StorageKeys.avatarObjectName(key))) {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }

        return fileStorage.get(key)
                .map(object -> {
                    MediaType mediaType = mediaType(object.contentType());
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(mediaType);
                    headers.setContentLength(object.content().length);
                    headers.setETag(key);
                    headers.add(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
                    return new ResponseEntity<>(object.content(), headers, HttpStatus.OK);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static MediaType mediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
