package com.identitybridge.qa.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Overall quality report containing all analysis results
 */
public class QualityReport {
    private LocalDateTime timestamp;
    private String projectPath;
    private String overallStatus;
    private List<AnalysisResult> results;
    
    public QualityReport() {
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private QualityReport report = new QualityReport();
        
        public Builder timestamp(LocalDateTime timestamp) {
            report.timestamp = timestamp;
            return this;
        }
        
        public Builder projectPath(String projectPath) {
            report.projectPath = projectPath;
            return this;
        }
        
        public Builder overallStatus(String overallStatus) {
            report.overallStatus = overallStatus;
            return this;
        }
        
        public Builder results(List<AnalysisResult> results) {
            report.results = results;
            return this;
        }
        
        public QualityReport build() {
            return report;
        }
    }
    
    // Getters and setters
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getProjectPath() {
        return projectPath;
    }
    
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }
    
    public String getOverallStatus() {
        return overallStatus;
    }
    
    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }
    
    public List<AnalysisResult> getResults() {
        return results;
    }
    
    public void setResults(List<AnalysisResult> results) {
        this.results = results;
    }
}
