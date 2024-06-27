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
  -DrepoUrl=${CODEARTIFACT_URL}

RUN ls 
RUN find ~/.m2

FROM ${ECR_REGISTRY}/merritt-tomcat:dev

RUN mkdir -p /tdr/tmpdir/logs 

