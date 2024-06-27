#*********************************************************************
#   Copyright 2021 Regents of the University of California
#   All rights reserved
#*********************************************************************

ARG ECR_REGISTRY=ecr_registry_not_set
ARG CODEARTIFACT_AUTH_TOKEN=tbd
ARG CODEARTIFACT_URL=tbd

FROM ${ECR_REGISTRY}/merritt-maven:dev as build

COPY settings.xml ~/.m2/
RUN mvn dependency:get -Dmaven.legacyLocalRepo=true -DgroupId=org.cdlib.mrt \
  -DartifactId=mrt-audit-it -Dversion=latest -Dpackaging=war \
  -DrepoUrl=${CODEARTIFACT_URL} > /tmp/tb.log
RUN pwd >> /tmp/tb.log
RUN ls >> /tmp/tb.log

FROM ${ECR_REGISTRY}/merritt-tomcat:dev

COPY --from=build /tmp/tb.log /tmp/tb.log
RUN cat /tmp/tb.log

RUN mkdir -p /tdr/tmpdir/logs 

