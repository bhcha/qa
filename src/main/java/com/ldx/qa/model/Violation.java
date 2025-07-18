package com.ldx.qa.model;

/**
 * Represents a single violation found during analysis
 */
public class Violation {
    private String severity; // error, warning, info
    private String file;
    private int line;
    private String message;
    private String rule;
    private String type;
    
    public Violation() {
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Violation violation = new Violation();
        
        public Builder severity(String severity) {
            violation.severity = severity;
            return this;
        }
        
        public Builder file(String file) {
            violation.file = file;
            return this;
        }
        
        public Builder line(int line) {
            violation.line = line;
            return this;
        }
        
        public Builder message(String message) {
            violation.message = message;
            return this;
        }
        
        public Builder rule(String rule) {
            violation.rule = rule;
            return this;
        }
        
        public Builder type(String type) {
            violation.type = type;
            return this;
        }
        
        public Violation build() {
            return violation;
        }
    }
    
    // Getters and setters
    public String getSeverity() {
        return severity;
    }
    
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    public String getFile() {
        return file;
    }
    
    public void setFile(String file) {
        this.file = file;
    }
    
    public int getLine() {
        return line;
    }
    
    public void setLine(int line) {
        this.line = line;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getRule() {
        return rule;
    }
    
    public void setRule(String rule) {
        this.rule = rule;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
}
