package com.agrofarm.backend.AiChat; 
 
public class ChatMessage { 
    private String role; // system, user, assistant 
    private String content; 
 
    public ChatMessage(String role, String content) { 
        this.role = role; 
        this.content = content; 
    } 
 
    public String getRole() { 
        return role; 
    } 
 
    public String getContent() { 
        return content; 
    } 
}