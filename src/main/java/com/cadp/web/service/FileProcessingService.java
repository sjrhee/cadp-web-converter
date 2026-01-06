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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class FileProcessingService {

    @Autowired
    private CadpClient cadpClient;

    // Job Status Map
    private final Map<String, com.cadp.web.dto.JobStatus> jobMap = new ConcurrentHashMap<>();

    // Result File Map (Simple storage for download)
    private final Map<String, File> resultFiles = new ConcurrentHashMap<>();

    public com.cadp.web.dto.JobStatus getJobStatus(String jobId) {
        return jobMap.get(jobId);
    }

    public File getResultFile(String jobId) {
        return resultFiles.get(jobId);
    }

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
                if (line.trim().isEmpty())
                    continue;
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

    public File processFileWithConfig(MultipartFile file, String mode, List<Integer> columnIndices, String policy,
            String delimiter, boolean skipHeader, com.cadp.web.dto.CadpConfig config) throws Exception {
        // Update Config if provided
        if (config != null) {
            cadpClient.reconfigure(config.getHost(), config.getPort(), config.getToken(), config.getUserName());
        }
        return processFile(file, mode, columnIndices, policy, delimiter, skipHeader);
    }

    public File processFile(MultipartFile file, String mode, List<Integer> columnIndices, String policy,
            String delimiter, boolean skipHeader) throws Exception {
        // Create temp input and output files
        Path tempDir = Files.createTempDirectory("cadp_upload");
        File inputFile = tempDir.resolve(file.getOriginalFilename()).toFile();
        file.transferTo(inputFile);

        String outputPrefix = (mode != null ? mode.toLowerCase() : "processed") + "_";
        File outputFile = tempDir.resolve(outputPrefix + file.getOriginalFilename()).toFile();

        // Use a fixed thread pool of 8 threads
        int threadCount = 8;
        // Process in chunks to avoid OOM
        int chunkSize = 1000;

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            String line;
            long lineNum = 0;

            // Handle escaped delimiter like "\t"
            String actualDelimiter = delimiter;
            if ("\\t".equals(delimiter)) {
                actualDelimiter = "\t";
            }
            final String finalDelimiter = actualDelimiter;

            List<java.util.concurrent.Future<String>> currentChunkFutures = new ArrayList<>(chunkSize);

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
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

                currentChunkFutures.add(executor.submit(() -> {
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

                // If chunk is full, process it
                if (currentChunkFutures.size() >= chunkSize) {
                    writeChunkResults(writer, currentChunkFutures);
                    currentChunkFutures.clear();
                    // Optional: Call System.gc() periodically if memory pressure is high?
                    // Better to rely on limiting active objects which we are doing here.
                }

                lineNum++;
            }

            // Process remaining futures
            if (!currentChunkFutures.isEmpty()) {
                writeChunkResults(writer, currentChunkFutures);
                currentChunkFutures.clear();
            }

        } finally {
            executor.shutdown();
        }

        return outputFile;
    }

    private void writeChunkResults(BufferedWriter writer, List<java.util.concurrent.Future<String>> futures)
            throws IOException {
        for (java.util.concurrent.Future<String> future : futures) {
            try {
                String result = future.get(); // Blocks until result is ready
                writer.write(result);
                writer.newLine();
            } catch (Exception e) {
                writer.write("ERROR_PROCESSING_LINE");
                writer.newLine();
                e.printStackTrace();
            }
        }
        writer.flush(); // Ensure data is written to disk
    }

    // Async Processing Method
    public String processFileAsync(MultipartFile file, String mode, List<Integer> columnIndices, String policy,
            String delimiter, boolean skipHeader, com.cadp.web.dto.CadpConfig config) throws Exception {

        if (config != null) {
            cadpClient.reconfigure(config.getHost(), config.getPort(), config.getToken(), config.getUserName());
        }

        String jobId = UUID.randomUUID().toString();
        com.cadp.web.dto.JobStatus job = new com.cadp.web.dto.JobStatus();
        job.setJobId(jobId);
        job.setStatus("UPLOADING");
        job.setFilename(file.getOriginalFilename());
        job.setStartTime(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        job.setProcessedLines(0);
        job.setErrorCount(0);
        jobMap.put(jobId, job);

        Path tempDir = Files.createTempDirectory("cadp_upload");
        File inputFile = tempDir.resolve(file.getOriginalFilename()).toFile();
        file.transferTo(inputFile);

        String outputPrefix = (mode != null ? mode.toLowerCase() : "processed") + "_";
        File outputFile = tempDir.resolve(outputPrefix + file.getOriginalFilename()).toFile();

        new Thread(() -> {
            job.setStatus("PROCESSING");
            long startTimeMillis = System.currentTimeMillis();

            try {
                processFileInternal(inputFile, outputFile, mode, columnIndices, policy, delimiter, skipHeader, job);

                long endTimeMillis = System.currentTimeMillis();
                job.setStatus("COMPLETED");
                job.setEndTime(java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                double seconds = (endTimeMillis - startTimeMillis) / 1000.0;
                job.setDuration(String.format("%.3f s", seconds));

                // Store result for retrieval
                resultFiles.put(jobId, outputFile);
                job.setResultToken(jobId);

            } catch (Exception e) {
                job.setStatus("FAILED");
                job.setErrorMessage(e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    Files.deleteIfExists(inputFile.toPath());
                } catch (IOException ignored) {
                }
            }
        }).start();

        return jobId;
    }

    private void processFileInternal(File inputFile, File outputFile, String mode, List<Integer> columnIndices,
            String policy,
            String delimiter, boolean skipHeader, com.cadp.web.dto.JobStatus job) throws Exception {

        int threadCount = 8;
        int chunkSize = 1000;

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            String line;
            long lineNum = 0;

            String actualDelimiter = delimiter;
            if ("\\t".equals(delimiter)) {
                actualDelimiter = "\t";
            }
            final String finalDelimiter = actualDelimiter;

            List<java.util.concurrent.Future<String>> currentChunkFutures = new ArrayList<>(chunkSize);

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                if (skipHeader && lineNum == 0) {
                    writer.write(line);
                    writer.newLine();
                    lineNum++;
                    continue;
                }

                final String currentLine = line;

                currentChunkFutures.add(executor.submit(() -> {
                    String[] parts = currentLine.split(finalDelimiter, -1);
                    boolean hasError = false;
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
                                hasError = true;
                            }
                        }
                    }
                    if (hasError)
                        throw new RuntimeException("Processing Error");
                    return String.join(finalDelimiter, parts);
                }));

                if (currentChunkFutures.size() >= chunkSize) {
                    writeChunkResults(writer, currentChunkFutures, job);
                    currentChunkFutures.clear();
                }

                lineNum++;
            }

            if (!currentChunkFutures.isEmpty()) {
                writeChunkResults(writer, currentChunkFutures, job);
                currentChunkFutures.clear();
            }

            job.setTotalLines(lineNum);

        } finally {
            executor.shutdown();
        }
    }

    private void writeChunkResults(BufferedWriter writer, List<java.util.concurrent.Future<String>> futures,
            com.cadp.web.dto.JobStatus job)
            throws IOException {
        long errorsInChunk = 0;
        long processedInChunk = 0;

        for (java.util.concurrent.Future<String> future : futures) {
            processedInChunk++;
            try {
                String result = future.get();
                writer.write(result);
                writer.newLine();
            } catch (Exception e) {
                errorsInChunk++;
                writer.write("ERROR_PROCESSING_LINE");
                writer.newLine();
            }
        }
        writer.flush();

        if (job != null) {
            synchronized (job) {
                job.setProcessedLines(job.getProcessedLines() + processedInChunk);
                job.setErrorCount(job.getErrorCount() + errorsInChunk);
            }
        }
    }
}
