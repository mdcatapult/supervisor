FROM openjdk:11
COPY target/scala-2.12/consumer-supervisor.jar /
ENTRYPOINT ["java","-jar","consumer-supervisor.jar"]