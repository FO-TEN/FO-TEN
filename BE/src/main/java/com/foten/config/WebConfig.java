package com.foten.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "com.foten", useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(org.springframework.stereotype.Controller.class))
public class WebConfig implements WebMvcConfigurer {

    // configureMessageConverters 는 기본 컨버터 목록을 통째로 교체해버린다(문자열·byte[]·Resource
    // 전용 컨버터가 전부 사라짐). extendMessageConverters 로 기본 목록에 더하는 방식을 쓰고,
    // 기본 Jackson 컨버터(JavaTimeModule 미등록)만 우리 걸로 바꿔치기한다.
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper()));
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    // 루트 컨텍스트(AppConfig)의 PropertySourcesPlaceholderConfigurer 는 자식(서블릿)
    // 컨텍스트로 상속되지 않는다. 이게 없으면 Controller 에서 @Value("${...}") 를 썼을 때
    // 예외 없이 "${...}" 문자열이 그대로 주입되는 함정이 생긴다.
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setLocation(ActiveProfile.propertiesFile());
        configurer.setIgnoreUnresolvablePlaceholders(false);
        return configurer;
    }
}
