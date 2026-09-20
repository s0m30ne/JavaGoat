#!/bin/sh
# External Tomcat JVM options. Application.main() is not used in WAR mode.

CATALINA_OPTS="${CATALINA_OPTS} -Dorg.apache.commons.collections.enableUnsafeSerialization=true"
CATALINA_OPTS="${CATALINA_OPTS} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-docker}"
CATALINA_OPTS="${CATALINA_OPTS} -Duser.timezone=Asia/Shanghai"
CATALINA_OPTS="${CATALINA_OPTS} -Xms256m -Xmx1024m"

export CATALINA_OPTS
