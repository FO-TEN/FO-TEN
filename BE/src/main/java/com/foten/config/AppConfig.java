package com.foten.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.scheduling.annotation.EnableScheduling;

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
}
