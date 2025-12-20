package com.cadp.web.service;

import com.centralmanagement.CentralManagementProvider;
import com.centralmanagement.CipherTextData;
import com.centralmanagement.ClientObserver;
import com.centralmanagement.RegisterClientParameters;
import com.centralmanagement.policy.CryptoManager;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import javax.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;

@Service
public class CadpClient {

    @Value("${cadp.kms.host}")
    private String keyManagerHost;

    @Value("${cadp.kms.port:443}")
    private String keyManagerPort;

    @Value("${cadp.registration.token}")
    private String registrationToken;

    @Value("${cadp.user.name}")
    private String defaultUserName;

    @Value("${cadp.policy.name:}")
    private String defaultPolicyName;

    private boolean isInitialized = false;

    @PostConstruct
    public void init() {
        try {
            if (keyManagerHost != null && !keyManagerHost.isEmpty() && registrationToken != null && !registrationToken.isEmpty()) {
                registerClient(keyManagerHost, keyManagerPort, registrationToken);
                isInitialized = true;
                System.out.println("CADP Client initialized successfully. Host: " + keyManagerHost + ", Port: " + keyManagerPort);
            } else {
                System.err.println("CADP Client initialization skipped: Missing configuration (Host or Token).");
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize CADP Client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void reconfigure(String host, String port, String token, String userName) throws Exception {
        // Simple re-registration attempt
        registerClient(host, port, token);
        this.keyManagerHost = host;
        this.keyManagerPort = port;
        this.registrationToken = token;
        this.defaultUserName = userName;
        this.isInitialized = true;
    }
    
    private void registerClient(String keyManagerHost, String keyManagerPort, String registrationToken) throws Exception {
        RegisterClientParameters.Builder builder = new RegisterClientParameters.Builder(keyManagerHost,
                registrationToken.toCharArray());

        if (keyManagerPort != null && !keyManagerPort.isEmpty()) {
            try {
                int port = Integer.parseInt(keyManagerPort);
                builder.setWebPort(port);
            } catch (NumberFormatException e) {
                // Ignore or log
            }
        }

        RegisterClientParameters registerClientParams = builder.build();
        CentralManagementProvider centralManagementProvider = new CentralManagementProvider(registerClientParams);
        centralManagementProvider.addProvider();
    }

    public String protect(String plainText, String policyName) throws Exception {
        if (!isInitialized) throw new IllegalStateException("CADP Client not initialized.");
        if (plainText == null) return null;

        CipherTextData cipherTextData = CryptoManager.protect(plainText.getBytes(StandardCharsets.UTF_8), policyName);
        return new String(cipherTextData.getCipherText(), StandardCharsets.UTF_8);
    }

    public String reveal(String cipherText, String policyName) throws Exception {
        if (!isInitialized) throw new IllegalStateException("CADP Client not initialized.");
        if (cipherText == null) return null;

        CipherTextData cipherTextData = new CipherTextData();
        cipherTextData.setCipherText(cipherText.getBytes(StandardCharsets.UTF_8));

        byte[] revealedData = CryptoManager.reveal(cipherTextData, policyName, defaultUserName);
        return new String(revealedData, StandardCharsets.UTF_8);
    }
    
    // Config Getters for UI
    public String getKeyManagerHost() { return keyManagerHost; }
    public String getKeyManagerPort() { return keyManagerPort; }
    public String getRegistrationToken() { return registrationToken; }
    public String getDefaultUserName() { return defaultUserName; }
    public String getDefaultPolicyName() { return defaultPolicyName; }
}
