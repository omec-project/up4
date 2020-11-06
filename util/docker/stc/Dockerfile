FROM azul/zulu-openjdk:8u272-8.50.0.21 as build

RUN apt-get update && apt-get install -y curl
RUN curl -o /usr/local/bin/stc.jar https://repo1.maven.org/maven2/org/onosproject/onos-stc/2.5/onos-stc-2.5.jar

ENV DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,address=5005,server=y,suspend=y"

ENTRYPOINT ["/usr/bin/java", "-jar", "/usr/local/bin/stc.jar"]