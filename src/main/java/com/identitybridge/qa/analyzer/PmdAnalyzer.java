package com.identitybridge.qa.analyzer;

import com.identitybridge.qa.config.QaConfiguration;
import com.identitybridge.qa.model.AnalysisResult;
import com.identitybridge.qa.model.Violation;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSets;
import net.sourceforge.pmd.RulesetsFactoryUtils;
import net.sourceforge.pmd.renderers.XMLRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * PMD analyzer implementation
 */
public class PmdAnalyzer implements Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(PmdAnalyzer.class);
    
    private final QaConfiguration config;
    
    public PmdAnalyzer(QaConfiguration config) {
        this.config = config;
    }
    
    @Override
    public String getName() {
        return "pmd";
    }
    
    @Override
    public String getType() {
        return "static";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public AnalysisResult analyze(Path projectPath) throws AnalysisException {
        logger.info("Running PMD analysis on: {}", projectPath);
        
        try {
            // Read existing PMD XML report
            Path pmdReportPath = projectPath.resolve("build/reports/pmd/main.xml");
            List<Violation> violations = new ArrayList<>();
            
            if (pmdReportPath.toFile().exists()) {
                violations = parsePmdXmlReport(pmdReportPath);
                logger.info("Parsed {} violations from PMD XML report", violations.size());
            } else {
                logger.warn("PMD XML report not found at: {}", pmdReportPath);
            }
            
            String status = violations.isEmpty() ? "pass" : "fail";
            
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("violationsFound", violations.size());
            
            return AnalysisResult.builder()
                .type(getName())
                .status(status)
                .summary(String.format("PMD found %d violations", violations.size()))
                .violations(violations)
                .metrics(metrics)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            throw new AnalysisException("PMD analysis failed", e);
        }
    }
    
    private List<Violation> parsePmdXmlReport(Path xmlReportPath) throws Exception {
        List<Violation> violations = new ArrayList<>();
        
        try {
            String xmlContent = java.nio.file.Files.readString(xmlReportPath);
            
            // Simple XML parsing for PMD violations
            String[] lines = xmlContent.split("\n");
            for (String line : lines) {
                if (line.trim().startsWith("<violation")) {
                    violations.add(parseViolationFromXml(line));
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to parse PMD XML report", e);
        }
        
        return violations;
    }
    
    private Violation parseViolationFromXml(String xmlLine) {
        try {
            // Extract attributes from XML line
            String file = extractAttribute(xmlLine, "filename");
            String lineStr = extractAttribute(xmlLine, "beginline");
            String rule = extractAttribute(xmlLine, "rule");
            String message = extractAttribute(xmlLine, "description");
            
            return Violation.builder()
                .file(file)
                .line(parseLineNumber(lineStr))
                .rule(rule)
                .message(message)
                .severity("warning")
                .type("PMD")
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to parse PMD violation from XML line: {}", xmlLine, e);
            return Violation.builder()
                .file("unknown")
                .line(0)
                .rule("unknown")
                .message("Failed to parse violation")
                .severity("warning")
                .type("PMD")
                .build();
        }
    }
    
    private String extractAttribute(String xmlLine, String attributeName) {
        try {
            String pattern = attributeName + "=\"";
            int start = xmlLine.indexOf(pattern);
            if (start == -1) return "";
            
            start += pattern.length();
            int end = xmlLine.indexOf("\"", start);
            if (end == -1) return "";
            
            return xmlLine.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }
    
    private int parseLineNumber(String lineStr) {
        try {
            return Integer.parseInt(lineStr);
        } catch (Exception e) {
            return 0;
        }
    }
}
