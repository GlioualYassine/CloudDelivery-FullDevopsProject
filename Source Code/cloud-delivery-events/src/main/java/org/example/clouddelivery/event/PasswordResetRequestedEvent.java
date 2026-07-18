package org.example.clouddelivery.event;

public class PasswordResetRequestedEvent {

    private String email;
    private String code;
    private String requestedAt;

    public PasswordResetRequestedEvent() {
    }

    public PasswordResetRequestedEvent(String email, String code, String requestedAt) {
        this.email = email;
        this.code = code;
        this.requestedAt = requestedAt;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getRequestedAt() { return requestedAt; }
    public void setRequestedAt(String requestedAt) { this.requestedAt = requestedAt; }

    @Override
    public String toString() {
        return "PasswordResetRequestedEvent{email='" + email + "', requestedAt='" + requestedAt + "'}";
    }
}
