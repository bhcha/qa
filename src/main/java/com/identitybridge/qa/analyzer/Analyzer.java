package com.identitybridge.qa.analyzer;

import com.identitybridge.qa.model.AnalysisResult;
import java.nio.file.Path;

/**
 * Base interface for all analyzers
 */
public interface Analyzer {
    
    /**
     * Get the name of this analyzer
     */
    String getName();
    
    /**
     * Check if this analyzer is available/installed
     */
    boolean isAvailable();
    
    /**
     * Run analysis on the project
     * 
     * @param projectPath Root path of the project to analyze
     * @return Analysis result
     * @throws AnalysisException if analysis fails
     */
    AnalysisResult analyze(Path projectPath) throws AnalysisException;
    
    /**
     * Get the analyzer type (static, ai, etc.)
     */
    default String getType() {
        return "unknown";
    }
}
