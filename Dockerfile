FROM openjdk:11

ARG VERSION_HASH="SNAPSHOT"
ENV VERSION_HASH=$VERSION_HASH

COPY target/scala-2.12/consumer-supervisor.jar /
ENTRYPOINT ["java","-jar","consumer-supervisor.jar"]