package com.ldx.qa.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of a single analyzer
 */
public class AnalysisResult {
    private String type;
    private String status; // pass, fail, skipped, error
    private String summary;
    private List<Violation> violations;
    private Map<String, Object> metrics;
    private LocalDateTime timestamp;
    
    // Constructor
    public AnalysisResult() {
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private AnalysisResult result = new AnalysisResult();
        
        public Builder type(String type) {
            result.type = type;
            return this;
        }
        
        public Builder status(String status) {
            result.status = status;
            return this;
        }
        
        public Builder summary(String summary) {
            result.summary = summary;
            return this;
        }
        
        public Builder violations(List<Violation> violations) {
            result.violations = violations;
            return this;
        }
        
        public Builder metrics(Map<String, Object> metrics) {
            result.metrics = metrics;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            result.timestamp = timestamp;
            return this;
        }
        
        public AnalysisResult build() {
            return result;
        }
    }
    
    // Getters and setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public List<Violation> getViolations() {
        return violations;
    }
    
    public void setViolations(List<Violation> violations) {
        this.violations = violations;
    }
    
    public Map<String, Object> getMetrics() {
        return metrics;
    }
    
    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
