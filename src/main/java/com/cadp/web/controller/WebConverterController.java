package com.cadp.web.controller;

import com.cadp.web.service.FileProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class WebConverterController {

    @Autowired
    private com.cadp.web.service.CadpClient cadpClient;

    @Autowired
    private FileProcessingService fileProcessingService;

    // Simple in-memory storage for download tokens
    private static final Map<String, File> fileStore = new ConcurrentHashMap<>();

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    // Step 2: File Preview
    @PostMapping("/api/preview")
    public ResponseEntity<com.cadp.web.dto.FilePreview> previewFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "delimiter", defaultValue = ",") String delimiter
    ) {
        try {
            return ResponseEntity.ok(fileProcessingService.generatePreview(file, delimiter));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Step 3: Config
    @GetMapping("/api/config")
    public ResponseEntity<com.cadp.web.dto.CadpConfig> getConfig() {
        com.cadp.web.dto.CadpConfig config = new com.cadp.web.dto.CadpConfig();
        config.setHost(cadpClient.getKeyManagerHost());
        config.setPort(cadpClient.getKeyManagerPort());
        config.setToken(cadpClient.getRegistrationToken());
        config.setUserName(cadpClient.getDefaultUserName());
        config.setPolicyName(cadpClient.getDefaultPolicyName());
        return ResponseEntity.ok(config);
    }

    // Step 4: Process
    @PostMapping("/api/process")
    public ResponseEntity<Map<String, String>> processFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode,
            @RequestParam("column") int column,
            @RequestParam("policy") String policy,
            @RequestParam(value = "delimiter", defaultValue = ",") String delimiter,
            @RequestParam(value = "skipHeader", defaultValue = "false") boolean skipHeader,
            // Config params
            @RequestParam(value = "confHost", required = false) String confHost,
            @RequestParam(value = "confPort", required = false) String confPort,
            @RequestParam(value = "confToken", required = false) String confToken,
            @RequestParam(value = "confUser", required = false) String confUser
    ) {
        try {
            com.cadp.web.dto.CadpConfig config = null;
            if (confHost != null && !confHost.isEmpty()) {
                config = new com.cadp.web.dto.CadpConfig();
                config.setHost(confHost);
                config.setPort(confPort);
                config.setToken(confToken);
                config.setUserName(confUser);
            }

            // Start Timer
            long startTime = System.currentTimeMillis();
            java.time.LocalDateTime startDateTime = java.time.LocalDateTime.now();

            File resultFile = fileProcessingService.processFileWithConfig(file, mode, column, policy, delimiter, skipHeader, config);
            
            // End Timer
            long endTime = System.currentTimeMillis();
            java.time.LocalDateTime endDateTime = java.time.LocalDateTime.now();
            java.time.Duration duration = java.time.Duration.between(startDateTime, endDateTime);
            
            // Generate Download Token
            String token = UUID.randomUUID().toString();
            fileStore.put(token, resultFile);
            
            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            response.put("filename", resultFile.getName());
            
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            response.put("startTime", startDateTime.format(formatter));
            response.put("endTime", endDateTime.format(formatter));
            
            // Format duration in seconds
            double seconds = duration.toMillis() / 1000.0;
            response.put("duration", String.format("%.3f s", seconds));

            // Count lines
            try (java.util.stream.Stream<String> stream = java.nio.file.Files.lines(resultFile.toPath())) {
                 long lineCount = stream.count();
                 response.put("totalLines", String.valueOf(lineCount));
            }
            response.put("duration", String.format("%.3f s", seconds));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            String msg = e.getMessage();
            if (msg == null) {
                msg = "Unknown error occurred (" + e.getClass().getSimpleName() + ")";
            }
            error.put("error", msg);
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // Step 5: Download
    @GetMapping("/api/download/{token}")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable("token") String token) {
        try {
            File file = fileStore.get(token);
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + file.getName())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.length())
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Helper for result preview
    @GetMapping("/api/preview-result/{token}")
    public ResponseEntity<com.cadp.web.dto.FilePreview> previewResult(@PathVariable("token") String token) {
        try {
            File file = fileStore.get(token);
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            com.cadp.web.dto.FilePreview preview = new com.cadp.web.dto.FilePreview();
            preview.setFilename(file.getName());
            java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 8) {
                    if (line.trim().isEmpty()) continue;
                    rows.add(java.util.Collections.singletonList(line));
                    count++;
                }
            }
            preview.setRows(rows);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
