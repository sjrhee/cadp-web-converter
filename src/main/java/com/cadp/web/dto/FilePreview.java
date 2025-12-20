package com.cadp.web.dto;

import lombok.Data;
import java.util.List;

@Data
public class FilePreview {
    private String filename;
    private List<String> headers;
    private List<List<String>> rows;
    private int totalRows; // Approximation or first N rows
}
