package com.foten.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
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

    /**
     * SPRING_PROFILES_ACTIVE(local/docker/prod) 이름의 application-{profile}.properties
     * 하나만 읽는다. Spring Boot 가 아니라 Spring Framework 이므로 @Profile 기반
     * 자동 프로퍼티 로딩이 없어 파일 경로를 직접 조립한다.
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profile == null || profile.isEmpty()) {
            profile = System.getProperty("spring.profiles.active", "local");
        }
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setLocation(new ClassPathResource("application-" + profile + ".properties"));
        configurer.setIgnoreUnresolvablePlaceholders(false);
        return configurer;
    }
}
