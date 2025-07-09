package org.example.video_recorder_backend.controller;

import org.example.video_recorder_backend.entity.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/videos")
@CrossOrigin
public class VideoController {

    private final JdbcTemplate jdbcTemplate;
    private final Path fileStorageLocation;

    @Autowired
    public VideoController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageLocation = Paths.get("videos").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getVideos() {
        String getSnippet = "SELECT * FROM videos";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(getSnippet);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{filename}")
    public ResponseEntity<?> getVideo(@PathVariable String filename) {
        String query = "SELECT filepath FROM videos WHERE filename = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(query, filename);

        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String filePathFromDB = (String) results.get(0).get("filepath");
        Path filePath = Paths.get(filePathFromDB).toAbsolutePath().normalize();

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .header("Content-Type", "video/webm")
                    .body(fileBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error reading video: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> addVideo(@RequestParam("file") MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String newFilename = UUID.randomUUID().toString() + fileExtension;
            Path targetLocation = this.fileStorageLocation.resolve(newFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            Video video = new Video();
            video.setFilename(newFilename);
            video.setFilepath("videos/" + newFilename);

            String addSnippet = "INSERT INTO videos(filename, filepath) VALUES(?, ?)";
            jdbcTemplate.update(addSnippet, video.getFilename(), video.getFilepath());

            return ResponseEntity.ok(video);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error saving video: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVideo(@PathVariable Long id) {
        String query = "SELECT filepath FROM videos WHERE id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(query, id);

        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String filePathFromDB = (String) results.get(0).get("filepath");
        Path filePath = Paths.get(filePathFromDB).toAbsolutePath().normalize();

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error deleting file: " + e.getMessage());
        }

        String deleteSnippet = "DELETE FROM videos WHERE id = ?";
        jdbcTemplate.update(deleteSnippet, id);

        return ResponseEntity.ok("Video deleted successfully");
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateVideo(@PathVariable Long id, @RequestBody Video video) {
        String updateSnippet = "UPDATE videos SET filename = ?, filepath = ? WHERE id = ?";
        jdbcTemplate.update(updateSnippet, video.getFilename(), video.getFilepath(), id);
        return ResponseEntity.ok().build();
    }
}