package com.ldx.qa.analyzer;

/**
 * Exception thrown when analysis fails
 */
public class AnalysisException extends Exception {
    
    public AnalysisException(String message) {
        super(message);
    }
    
    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
