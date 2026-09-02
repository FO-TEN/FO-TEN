package com.foten.config;

import org.springframework.core.io.ClassPathResource;

/**
 * SPRING_PROFILES_ACTIVE(local/docker/prod) 값으로 application-{profile}.properties
 * 파일 하나를 고른다. 루트 컨텍스트(AppConfig)와 서블릿 컨텍스트(WebConfig) 양쪽에서 쓴다 —
 * 두 컨텍스트는 BeanFactoryPostProcessor 를 공유하지 않으므로 각자 등록해야 한다.
 */
final class ActiveProfile {

    private ActiveProfile() {
    }

    static ClassPathResource propertiesFile() {
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profile == null || profile.isEmpty()) {
            profile = System.getProperty("spring.profiles.active", "local");
        }
        return new ClassPathResource("application-" + profile + ".properties");
    }
}
