package com.ldx.qa.config;

import org.junit.jupiter.api.Test;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class QaConfigurationGeminiModelTest {

    @Test
    public void testDefaultGeminiModel() {
        QaConfiguration config = QaConfiguration.defaultConfig();
        assertEquals("gemini-2.0-flash-thinking-exp", config.getGeminiModel());
    }

    @Test
    public void testGeminiModelFromProperties() {
        Properties props = new Properties();
        props.setProperty("qa.ai.gemini.model", "gemini-2.5-flash");
        
        QaConfiguration config = QaConfiguration.fromProperties(props);
        assertEquals("gemini-2.5-flash", config.getGeminiModel());
    }

    @Test
    public void testGeminiModelCustomValue() {
        Properties props = new Properties();
        props.setProperty("qa.ai.gemini.model", "gemini-1.5-pro");
        
        QaConfiguration config = QaConfiguration.fromProperties(props);
        assertEquals("gemini-1.5-pro", config.getGeminiModel());
    }

    @Test
    public void testGeminiModelSetterGetter() {
        QaConfiguration config = new QaConfiguration();
        config.setGeminiModel("custom-model");
        assertEquals("custom-model", config.getGeminiModel());
    }
}