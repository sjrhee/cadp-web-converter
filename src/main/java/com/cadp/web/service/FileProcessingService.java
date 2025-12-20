package com.cadp.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class FileProcessingService {

    @Autowired
    private CadpClient cadpClient;

    public com.cadp.web.dto.FilePreview generatePreview(MultipartFile file, String delimiter) throws Exception {
        com.cadp.web.dto.FilePreview preview = new com.cadp.web.dto.FilePreview();
        preview.setFilename(file.getOriginalFilename());
        
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();

        // Handle escaped delimiter like "\t"
        String actualDelimiter = delimiter;
        if ("\\t".equals(delimiter)) {
            actualDelimiter = "\t";
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int count = 0;
            // Read up to 10 rows for preview
            while ((line = reader.readLine()) != null && count < 10) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(actualDelimiter, -1);
                rows.add(java.util.Arrays.asList(parts));
                count++;
            }
        }
        
        if (!rows.isEmpty()) {
            // Assume first row might be header
            preview.setHeaders(rows.get(0));
            preview.setRows(rows);
            preview.setTotalRows(rows.size()); // Just a preview count
        }
        
        return preview;
    }

    public File processFileWithConfig(MultipartFile file, String mode, List<Integer> columnIndices, String policy, String delimiter, boolean skipHeader, com.cadp.web.dto.CadpConfig config) throws Exception {
        // Update Config if provided
        if (config != null) {
            cadpClient.reconfigure(config.getHost(), config.getPort(), config.getToken(), config.getUserName());
        }
        return processFile(file, mode, columnIndices, policy, delimiter, skipHeader);
    }

    public File processFile(MultipartFile file, String mode, List<Integer> columnIndices, String policy, String delimiter, boolean skipHeader) throws Exception {
        // Create temp input and output files
        Path tempDir = Files.createTempDirectory("cadp_upload");
        File inputFile = tempDir.resolve(file.getOriginalFilename()).toFile();
        file.transferTo(inputFile);

        String outputPrefix = (mode != null ? mode.toLowerCase() : "processed") + "_";
        File outputFile = tempDir.resolve(outputPrefix + file.getOriginalFilename()).toFile();

        // Use a fixed thread pool of 8 threads
        int threadCount = 8;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            String line;
            int lineNum = 0;
            
            // Handle escaped delimiter like "\t"
            String actualDelimiter = delimiter;
            if ("\\t".equals(delimiter)) {
                actualDelimiter = "\t";
            }
            final String finalDelimiter = actualDelimiter;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                     // Empty lines are preserved as is (or skipped based on logic, but preserving ensures structure)
                     // However, original logic skipped empty lines: "if (line.trim().isEmpty()) continue;"
                     // We will keep skipping empty lines to match original behavior.
                     continue; 
                }

                if (skipHeader && lineNum == 0) {
                    writer.write(line);
                    writer.newLine();
                    lineNum++;
                    continue;
                }

                // Capture variables for lambda
                final String currentLine = line;
                
                futures.add(executor.submit(() -> {
                    String[] parts = currentLine.split(finalDelimiter, -1);
                    
                    for (int columnIndex : columnIndices) {
                        if (parts.length > columnIndex) {
                            try {
                                String target = parts[columnIndex];
                                String processed = target;
                                if (target != null && !target.isEmpty()) {
                                    if ("protect".equalsIgnoreCase(mode)) {
                                        processed = cadpClient.protect(target, policy);
                                    } else if ("reveal".equalsIgnoreCase(mode)) {
                                        processed = cadpClient.reveal(target, policy);
                                    }
                                }
                                parts[columnIndex] = processed;
                            } catch (Exception e) {
                                parts[columnIndex] = "ERROR";
                            }
                        }
                    }
                    return String.join(finalDelimiter, parts);
                }));
                
                lineNum++;
            }
            
            // Collect results in order
            for (java.util.concurrent.Future<String> future : futures) {
                try {
                    String result = future.get(); // This blocks until the specific line is done
                    writer.write(result);
                    writer.newLine();
                } catch (Exception e) {
                    writer.write("ERROR_PROCESSING_LINE");
                    writer.newLine();
                    e.printStackTrace();
                }
            }
            
        } finally {
            executor.shutdown();
        }

        return outputFile;
    }
}
