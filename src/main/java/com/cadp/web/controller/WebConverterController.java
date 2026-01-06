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
import java.util.List;
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
            @RequestParam(value = "delimiter", defaultValue = ",") String delimiter) {
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

    // Step 4: Process (Async)
    @PostMapping("/api/process")
    public ResponseEntity<Map<String, String>> processFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode,
            @RequestParam("columns") List<Integer> columns,
            @RequestParam("policy") String policy,
            @RequestParam(value = "delimiter", defaultValue = ",") String delimiter,
            @RequestParam(value = "skipHeader", defaultValue = "false") boolean skipHeader,
            // Config params
            @RequestParam(value = "confHost", required = false) String confHost,
            @RequestParam(value = "confPort", required = false) String confPort,
            @RequestParam(value = "confToken", required = false) String confToken,
            @RequestParam(value = "confUser", required = false) String confUser) {
        try {
            com.cadp.web.dto.CadpConfig config = null;
            if (confHost != null && !confHost.isEmpty()) {
                config = new com.cadp.web.dto.CadpConfig();
                config.setHost(confHost);
                config.setPort(confPort);
                config.setToken(confToken);
                config.setUserName(confUser);
            }

            // Pre-flight check
            String checkPolicy = (config != null && config.getPolicyName() != null && !config.getPolicyName().isEmpty())
                    ? config.getPolicyName()
                    : policy;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(file.getInputStream()))) {
                String line = reader.readLine();
                if (skipHeader && line != null) {
                    line = reader.readLine();
                }

                if (line != null && !line.trim().isEmpty() && !columns.isEmpty()) {
                    String[] parts = line.split(delimiter);

                    int targetCol = columns.get(0);
                    if (targetCol < parts.length) {
                        String sampleData = parts[targetCol];
                        // Perform check based on mode
                        if ("protect".equalsIgnoreCase(mode)) {
                            cadpClient.protect(sampleData, checkPolicy);
                        } else if ("reveal".equalsIgnoreCase(mode)) {
                            cadpClient.reveal(sampleData, checkPolicy);
                        }
                    }
                }
            } catch (Exception e) {
                Map<String, String> error = new HashMap<>();
                error.put("error",
                        "Pre-flight check failed: (" + e.getMessage() + ")");
                return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(error);
            }

            // Start Async Job
            String jobId = fileProcessingService.processFileAsync(file, mode, columns, policy, delimiter, skipHeader,
                    config);

            Map<String, String> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "UPLOADING");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/api/status/{jobId}")
    public ResponseEntity<com.cadp.web.dto.JobStatus> getJobStatus(@PathVariable("jobId") String jobId) {
        com.cadp.web.dto.JobStatus status = fileProcessingService.getJobStatus(jobId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    // Step 5: Download
    @GetMapping("/api/download/{token}")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable("token") String token) {
        try {
            // Check local store first (legacy), then service store
            File file = fileStore.get(token);
            if (file == null) {
                file = fileProcessingService.getResultFile(token);
            }

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
            if (file == null) {
                file = fileProcessingService.getResultFile(token);
            }
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
                    if (line.trim().isEmpty())
                        continue;
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
