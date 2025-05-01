package com.zoho.catalyst_plugin.dto;

public class AuthResponse {
    public String message;  
    public String token;    
 
    public AuthResponse() {}

    public AuthResponse(String message, String token) {
        this.message = message;
        this.token = token;
    }

    // --- Getters ---
    public String getMessage() {
        return message;
    }

    public String getToken() {
        return token;
    }

    // --- Setters ---
    public void setMessage(String message) {
        this.message = message;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
