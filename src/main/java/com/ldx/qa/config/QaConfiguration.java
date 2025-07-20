package com.ldx.qa.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration for QA analysis
 */
public class QaConfiguration {
    
    // General settings
    private boolean ignoreFailures = true; // 개발 시 품질 검사 실패 무시
    private boolean skipUnavailableAnalyzers = true;
    
    // Static analysis settings
    private boolean staticAnalysisEnabled = true;
    private boolean checkstyleEnabled = true;
    private boolean pmdEnabled = true;
    private boolean spotbugsEnabled = true;
    private boolean jacocoEnabled = true;
    private boolean archunitEnabled = true;
    
    // AI analysis settings
    private boolean aiAnalysisEnabled = true;
    private boolean geminiEnabled = true;
    private String geminiModel = "gemini-2.0-flash-thinking-exp"; // Default model
    
    // Report settings
    private boolean htmlReportEnabled = true;
    private boolean jsonReportEnabled = true;
    
    // Configuration paths
    private String checkstyleConfigPath = "config/static/checkstyle/checkstyle.xml";
    private String pmdRulesetPath = "config/static/pmd/ruleset.xml";
    private String spotbugsExcludePath = "config/static/spotbugs/exclude.xml";
    private String geminiGuidePath = "config/ai/gemini-guide.md";
    private String archunitBasePackage = "";
    private String archunitConfigPath = "config/archunit/archunit.properties";
    
    // Track if base package was explicitly configured
    private boolean basePackageExplicitlyConfigured = false;

    // Kingfisher settings
    private boolean kingfisherEnabled = true;
    private String kingfisherConfigPath = "config/kingfisher/rules.yaml";
    private String kingfisherConfidenceLevel = "medium"; // low, medium, high
    private boolean kingfisherValidationEnabled = true;


    public QaConfiguration() {
        // Default constructor
    }
    
    /**
     * Load configuration from properties file
     */
    public static QaConfiguration fromFile(File configFile) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration from " + configFile, e);
        }
        
        return fromProperties(props);
    }
    
    /**
     * Load configuration from properties
     */
    public static QaConfiguration fromProperties(Properties props) {
        QaConfiguration config = new QaConfiguration();
        
        // General settings
        config.ignoreFailures = Boolean.parseBoolean(
            props.getProperty("qa.ignoreFailures", "false"));
        config.skipUnavailableAnalyzers = Boolean.parseBoolean(
            props.getProperty("qa.skipUnavailableAnalyzers", "true"));
        
        // Static analysis
        config.staticAnalysisEnabled = Boolean.parseBoolean(
            props.getProperty("qa.static.enabled", "true"));
        config.checkstyleEnabled = Boolean.parseBoolean(
            props.getProperty("qa.static.checkstyle.enabled", "true"));
        config.pmdEnabled = Boolean.parseBoolean(
            props.getProperty("qa.static.pmd.enabled", "true"));
        config.spotbugsEnabled = Boolean.parseBoolean(
            props.getProperty("qa.static.spotbugs.enabled", "true"));
        config.jacocoEnabled = Boolean.parseBoolean(
            props.getProperty("qa.static.jacoco.enabled", "true"));
        config.archunitEnabled = Boolean.parseBoolean(
            props.getProperty("qa.static.archunit.enabled", "true"));

        // Kingfisher
        config.kingfisherEnabled = Boolean.parseBoolean(
                props.getProperty("qa.static.kingfisher.enabled", "true"));
        config.kingfisherConfigPath = props.getProperty(
                "qa.static.kingfisher.configPath", config.kingfisherConfigPath);
        config.kingfisherConfidenceLevel = props.getProperty(
                "qa.static.kingfisher.confidenceLevel", config.kingfisherConfidenceLevel);
        config.kingfisherValidationEnabled = Boolean.parseBoolean(
                props.getProperty("qa.static.kingfisher.validationEnabled", "true"));


        // AI analysis
        config.aiAnalysisEnabled = Boolean.parseBoolean(
            props.getProperty("qa.ai.enabled", "true"));
        config.geminiEnabled = Boolean.parseBoolean(
            props.getProperty("qa.ai.gemini.enabled", "true"));
        config.geminiModel = props.getProperty(
            "qa.ai.gemini.model", config.geminiModel);
        
        // Reports
        config.htmlReportEnabled = Boolean.parseBoolean(
            props.getProperty("qa.reports.html.enabled", "true"));
        config.jsonReportEnabled = Boolean.parseBoolean(
            props.getProperty("qa.reports.json.enabled", "true"));
        
        // Paths
        config.checkstyleConfigPath = props.getProperty(
            "qa.static.checkstyle.configPath", config.checkstyleConfigPath);
        config.pmdRulesetPath = props.getProperty(
            "qa.static.pmd.rulesetPath", config.pmdRulesetPath);
        config.spotbugsExcludePath = props.getProperty(
            "qa.static.spotbugs.excludePath", config.spotbugsExcludePath);
        config.geminiGuidePath = props.getProperty(
            "qa.ai.gemini.guidePath", config.geminiGuidePath);
        String configuredBasePackage = props.getProperty("qa.static.archunit.basePackage");
        if (configuredBasePackage != null) {
            config.archunitBasePackage = configuredBasePackage;
            config.basePackageExplicitlyConfigured = true;
        }
        config.archunitConfigPath = props.getProperty(
            "qa.static.archunit.configPath", config.archunitConfigPath);
        
        return config;
    }
    
    /**
     * Create default configuration
     */
    public static QaConfiguration defaultConfig() {
        return new QaConfiguration();
    }
    
    // Getters and setters
    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }
    
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }
    
    public boolean isSkipUnavailableAnalyzers() {
        return skipUnavailableAnalyzers;
    }
    
    public void setSkipUnavailableAnalyzers(boolean skipUnavailableAnalyzers) {
        this.skipUnavailableAnalyzers = skipUnavailableAnalyzers;
    }
    
    public boolean isStaticAnalysisEnabled() {
        return staticAnalysisEnabled;
    }
    
    public void setStaticAnalysisEnabled(boolean staticAnalysisEnabled) {
        this.staticAnalysisEnabled = staticAnalysisEnabled;
    }
    
    public boolean isCheckstyleEnabled() {
        return staticAnalysisEnabled && checkstyleEnabled;
    }
    
    public void setCheckstyleEnabled(boolean checkstyleEnabled) {
        this.checkstyleEnabled = checkstyleEnabled;
    }
    
    public boolean isPmdEnabled() {
        return staticAnalysisEnabled && pmdEnabled;
    }
    
    public void setPmdEnabled(boolean pmdEnabled) {
        this.pmdEnabled = pmdEnabled;
    }
    
    public boolean isSpotbugsEnabled() {
        return staticAnalysisEnabled && spotbugsEnabled;
    }
    
    public void setSpotbugsEnabled(boolean spotbugsEnabled) {
        this.spotbugsEnabled = spotbugsEnabled;
    }
    
    public boolean isJacocoEnabled() {
        return staticAnalysisEnabled && jacocoEnabled;
    }
    
    public void setJacocoEnabled(boolean jacocoEnabled) {
        this.jacocoEnabled = jacocoEnabled;
    }
    
    public boolean isArchunitEnabled() {
        return staticAnalysisEnabled && archunitEnabled;
    }
    
    public void setArchunitEnabled(boolean archunitEnabled) {
        this.archunitEnabled = archunitEnabled;
    }
    
    public boolean isAiAnalysisEnabled() {
        return aiAnalysisEnabled;
    }
    
    public void setAiAnalysisEnabled(boolean aiAnalysisEnabled) {
        this.aiAnalysisEnabled = aiAnalysisEnabled;
    }
    
    public boolean isGeminiEnabled() {
        return aiAnalysisEnabled && geminiEnabled;
    }
    
    public void setGeminiEnabled(boolean geminiEnabled) {
        this.geminiEnabled = geminiEnabled;
    }
    
    public String getGeminiModel() {
        return geminiModel;
    }
    
    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }
    
    public boolean isHtmlReportEnabled() {
        return htmlReportEnabled;
    }
    
    public void setHtmlReportEnabled(boolean htmlReportEnabled) {
        this.htmlReportEnabled = htmlReportEnabled;
    }
    
    public boolean isJsonReportEnabled() {
        return jsonReportEnabled;
    }    
    public void setJsonReportEnabled(boolean jsonReportEnabled) {
        this.jsonReportEnabled = jsonReportEnabled;
    }
    
    public String getCheckstyleConfigPath() {
        return checkstyleConfigPath;
    }
    
    public void setCheckstyleConfigPath(String checkstyleConfigPath) {
        this.checkstyleConfigPath = checkstyleConfigPath;
    }
    
    public String getPmdRulesetPath() {
        return pmdRulesetPath;
    }
    
    public void setPmdRulesetPath(String pmdRulesetPath) {
        this.pmdRulesetPath = pmdRulesetPath;
    }
    
    public String getSpotbugsExcludePath() {
        return spotbugsExcludePath;
    }
    
    public void setSpotbugsExcludePath(String spotbugsExcludePath) {
        this.spotbugsExcludePath = spotbugsExcludePath;
    }
    
    public String getGeminiGuidePath() {
        return geminiGuidePath;
    }    
    public void setGeminiGuidePath(String geminiGuidePath) {
        this.geminiGuidePath = geminiGuidePath;
    }
    
    public String getArchunitBasePackage() {
        return archunitBasePackage;
    }
    
    public void setArchunitBasePackage(String archunitBasePackage) {
        this.archunitBasePackage = archunitBasePackage;
    }
    
    public String getArchunitConfigPath() {
        return archunitConfigPath;
    }
    
    public void setArchunitConfigPath(String archunitConfigPath) {
        this.archunitConfigPath = archunitConfigPath;
    }
    
    public boolean isBasePackageExplicitlyConfigured() {
        return basePackageExplicitlyConfigured;
    }
    
    public void setBasePackageExplicitlyConfigured(boolean basePackageExplicitlyConfigured) {
        this.basePackageExplicitlyConfigured = basePackageExplicitlyConfigured;
    }

    public boolean isKingfisherEnabled() {
        return staticAnalysisEnabled && kingfisherEnabled;
    }

    public void setKingfisherEnabled(boolean kingfisherEnabled) {
        this.kingfisherEnabled = kingfisherEnabled;
    }

    public String getKingfisherConfigPath() {
        return kingfisherConfigPath;
    }

    public void setKingfisherConfigPath(String kingfisherConfigPath) {
        this.kingfisherConfigPath = kingfisherConfigPath;
    }

    public String getKingfisherConfidenceLevel() {
        return kingfisherConfidenceLevel;
    }

    public void setKingfisherConfidenceLevel(String kingfisherConfidenceLevel) {
        this.kingfisherConfidenceLevel = kingfisherConfidenceLevel;
    }

    public boolean isKingfisherValidationEnabled() {
        return kingfisherValidationEnabled;
    }

    public void setKingfisherValidationEnabled(boolean kingfisherValidationEnabled) {
        this.kingfisherValidationEnabled = kingfisherValidationEnabled;
    }

}
