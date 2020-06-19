FROM gradle:jdk11 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build
FROM amazoncorretto:11
LABEL maintainer="Holger Bruch holger.bruch@mitfahrdezentrale.de"

COPY --from=build /home/gradle/src/build/distributions/* /app/
RUN yum install -y unzip \
  && unzip -d /app /app/gtfsvtor.zip \
  && yum remove -y unzip \
  && yum clean all \
  && rm -rf /var/cache/yum \
  && rm /app/*.zip

WORKDIR /data

ENV GTFSVTOR_OPTS=-Xmx4G

ENTRYPOINT ["/app/gtfsvtor/bin/gtfsvtor"]
CMD ["-h"]