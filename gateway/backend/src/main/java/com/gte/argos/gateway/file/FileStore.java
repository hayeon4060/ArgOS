package com.gte.argos.gateway.file;

import com.gte.argos.gateway.common.api.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 중앙 서버가 받는 파일(점검 서류 스캔, 배치도, 보고서)을 디스크에 두고 file_asset 에 기록한다.
 * 저장 위치: argos.files.dir (기본 ./data/files) 아래 kind/yyyy-MM/uuid.확장자
 */
@Component
public class FileStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final Path root;

    public FileStore(NamedParameterJdbcTemplate jdbc, @Value("${argos.files.dir:./data/files}") String dir) {
        this.jdbc = jdbc;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public record Stored(long id, String kind, Path path, String mime, long size, String name) {
    }

    public Stored save(String kind, String originalName, String mime, byte[] bytes) {
        String ext = ext(originalName);
        String rel = kind.toLowerCase() + "/" + LocalDate.now().toString().substring(0, 7) + "/" + UUID.randomUUID() + ext;
        Path target = root.resolve(rel).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_SAVE_FAILED", "파일을 저장하지 못했습니다.");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO file_asset (kind, path, mime, size_bytes, orig_name)
                VALUES (:kind, :path, :mime, :size, :name) RETURNING file_id
                """, new MapSqlParameterSource("kind", kind).addValue("path", rel).addValue("mime", mime)
                .addValue("size", (long) bytes.length).addValue("name", originalName), Long.class);
        return new Stored(id, kind, target, mime, bytes.length, originalName);
    }

    public Stored find(long fileId) {
        Map<String, Object> r = jdbc.queryForList("SELECT * FROM file_asset WHERE file_id = :id",
                new MapSqlParameterSource("id", fileId)).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "파일이 없습니다."));
        Path p = root.resolve(String.valueOf(r.get("path"))).normalize();
        if (!p.startsWith(root)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "파일이 없습니다.");
        }
        Number size = (Number) r.get("size_bytes");
        return new Stored(fileId, String.valueOf(r.get("kind")), p, (String) r.get("mime"),
                size == null ? 0 : size.longValue(), (String) r.get("orig_name"));
    }

    public byte[] read(Stored f) {
        try {
            return Files.readAllBytes(f.path());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "파일을 읽을 수 없습니다.");
        }
    }

    static String ext(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        String e = i < 0 ? "" : name.substring(i).toLowerCase();
        return e.matches("\\.[a-z0-9]{1,5}") ? e : "";
    }
}
