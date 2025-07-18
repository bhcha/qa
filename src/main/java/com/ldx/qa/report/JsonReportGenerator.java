package com.ldx.qa.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ldx.qa.model.QualityReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * JSON report generator
 */
public class JsonReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(JsonReportGenerator.class);
    
    private final ObjectMapper objectMapper;
    
    public JsonReportGenerator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    public void generate(QualityReport report, Path outputPath) throws IOException {
        try {
            objectMapper.writeValue(outputPath.toFile(), report);
            logger.info("JSON report generated at: {}", outputPath);
        } catch (IOException e) {
            throw new IOException("Failed to generate JSON report", e);
        }
    }
}
