package com.cadp.web.dto;

import lombok.Data;
import java.util.List;

public class FilePreview {
    private String filename;
    private List<String> headers;
    private List<List<String>> rows;
    private int totalRows; // Approximation or first N rows

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    @Override
    public String toString() {
        return "FilePreview{" +
                "filename='" + filename + '\'' +
                ", headers=" + headers +
                ", rows=" + rows +
                ", totalRows=" + totalRows +
                '}';
    }
}
