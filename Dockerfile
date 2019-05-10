FROM openjdk:8u212-jre

ARG submodule
ARG version

ENV SUBMODULE=$submodule
ENV VERSION=$version

COPY ./sawtooth-daml-$SUBMODULE/target/sawtooth-daml-$SUBMODULE-$VERSION-shaded.jar /opt

WORKDIR /opt

#TO-DO remember to include environmental variable for injection as arguments
CMD java -jar sawtooth-daml-$SUBMODULE-$VERSION-shaded.jar
