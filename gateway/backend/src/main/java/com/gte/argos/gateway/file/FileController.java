package com.gte.argos.gateway.file;

import com.gte.argos.gateway.access.CurrentUser;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /files/{fileId}: 배치도 그림 등 중앙 서버가 가진 파일 (로그인한 사용자) */
@RestController
@RequestMapping("/api/v1")
public class FileController {

    private final FileStore files;
    private final CurrentUser currentUser;

    public FileController(FileStore files, CurrentUser currentUser) {
        this.files = files;
        this.currentUser = currentUser;
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<byte[]> file(@PathVariable long fileId) {
        currentUser.scope();
        FileStore.Stored f = files.find(fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.name() == null ? "file" : f.name(), StandardCharsets.UTF_8).build().toString())
                .contentType(f.mime() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(f.mime()))
                .body(files.read(f));
    }
}
