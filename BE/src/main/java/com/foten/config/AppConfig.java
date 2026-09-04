package com.foten.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 루트 컨텍스트 설정. Service / Mapper 등 웹 계층을 제외한 빈을 스캔한다.
 * Controller 는 WebConfig(서블릿 컨텍스트)에서만 스캔한다.
 */
@Configuration
@EnableScheduling
@ComponentScan(
        basePackages = "com.foten",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = org.springframework.stereotype.Controller.class)
)
public class AppConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setLocation(ActiveProfile.propertiesFile());
        configurer.setIgnoreUnresolvablePlaceholders(false);
        return configurer;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // 끄지 않으면 LocalDate 가 [2026,9,4] 배열로 나간다. ISO-8601 문자열로 내보낸다.
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
}
