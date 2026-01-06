package com.cadp.web.dto;

import lombok.Data;

@Data
public class CadpConfig {
    private String host;
    private String port = "443";
    private String token;
    private String userName;
    private String policyName;
}
