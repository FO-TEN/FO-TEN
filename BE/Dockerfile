# 1단계: 빌드 (JDK + Gradle)
# 의존성 설치를 소스 복사보다 먼저 두어, 소스만 바뀌었을 때 캐시가 살아 있게 한다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies
COPY src ./src
RUN ./gradlew --no-daemon clean war -x test

# 2단계: 실행 (Tomcat 9, JDK 불필요)
#
# Tomcat 9 를 고정한다. Tomcat 10 이상은 javax.* → jakarta.* 로 넘어가서
# Spring Framework 5 앱이 아예 뜨지 않는다. 이미지 태그를 올리지 말 것.
FROM tomcat:9.0-jdk17-temurin
ENV SPRING_PROFILES_ACTIVE=docker
# DB 와 같은 시간대로 고정한다. 기본값(UTC)이면 귀국 D-day 계산과
# 환율 배치 시각이 하루씩 어긋난다.
ENV TZ=Asia/Seoul
RUN rm -rf /usr/local/tomcat/webapps/*
# war 파일명은 build.gradle 의 archivesBaseName(또는 archiveBaseName) 값과 일치해야 한다.
COPY --from=build /workspace/build/libs/foten.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
