FROM openjdk:14
ARG VERSION_HASH="SNAPSHOT"
ENV VERSION_HASH=$VERSION_HASH
COPY certs/mdc-CA.cer /etc/pki/ca-trust/source/anchors/mdc-CA.cer
RUN /bin/update-ca-trust
RUN mkdir -p /srv
COPY target/scala-2.13/consumer.jar /consumer.jar
ENTRYPOINT java $JAVA_OPTS -jar /consumer.jar start --config /srv/common.conf