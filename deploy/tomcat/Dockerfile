# syntax=docker/dockerfile:1
# Build a Tomcat 9 WAR image without changing the application source tree.
# ServletInitializer is injected only in this image build.

FROM maven:3.9.9-eclipse-temurin-8 AS builder
WORKDIR /build

COPY pom.xml .
RUN sed -i '/<version>1.5.0<\/version>/a\    <packaging>war</packaging>' pom.xml

COPY src ./src
COPY deploy/tomcat/ServletInitializer.java src/main/java/top/whgojp/ServletInitializer.java

ENV MAVEN_OPTS="-Xmx1024m"
RUN mvn -B clean package -DskipTests

FROM tomcat:9.0-jdk8-temurin
LABEL maintainer="whgojp@foxmail.com"
LABEL description="JavaGoat on external Tomcat 9"

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=docker \
    CATALINA_OPTS=""

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && rm -rf /usr/local/tomcat/webapps/* \
    && mkdir -p /tmp/upload /tmp/static/api /tmp/static/css /tmp/static/images \
               /tmp/static/js /tmp/static/lib /tmp/static/other /tmp/static/upload \
    && chmod -R 777 /tmp/upload /tmp/static \
    && echo "vul test.jsp" > /tmp/upload/test.jsp \
    && echo "vul test.txt" > /tmp/upload/test.txt \
    && echo "test readme.md" > /tmp/static/api/readme.md \
    && echo "test styles.css" > /tmp/static/css/styles.css \
    && echo "test script.js" > /tmp/static/js/script.js \
    && echo "test resource.txt" > /tmp/static/other/resource.txt \
    && echo "test file.txt" > /tmp/static/upload/file.txt \
    && sed -i 's/Connector port="8080" protocol="HTTP\/1.1"/Connector port="8080" protocol="HTTP\/1.1" maxPostSize="52428800" maxSwallowSize="52428800" URIEncoding="UTF-8"/' \
       /usr/local/tomcat/conf/server.xml

COPY --from=builder /build/target/JavaGoat.war /usr/local/tomcat/webapps/ROOT.war
COPY deploy/tomcat/setenv.sh /usr/local/tomcat/bin/setenv.sh
RUN chmod +x /usr/local/tomcat/bin/setenv.sh

WORKDIR /usr/local/tomcat
EXPOSE 8080
