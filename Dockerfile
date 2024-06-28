#*********************************************************************
#   Copyright 2021 Regents of the University of California
#   All rights reserved
#*********************************************************************

ARG ECR_REGISTRY=ecr_registry_not_set
ARG CODEARTIFACT_AUTH_TOKEN
ARG CODEARTIFACT_URL
ARG AWS_REGION
ARG AWS_ACCOUNT_ID

FROM ${ECR_REGISTRY}/merritt-maven:dev as build

ENV ECR_REGISTRY=$ECR_REGISTRY
ENV CODEARTIFACT_AUTH_TOKEN=$CODEARTIFACT_AUTH_TOKEN
ENV CODEARTIFACT_URL=$CODEARTIFACT_URL
ENV AWS_ACCOUNT_ID=$AWS_ACCOUNT_ID
ENV AWS_REGION=$AWS_REGION

COPY settings.xml ~/.m2/
RUN mvn dependency:get -DgroupId=org.cdlib.mrt \
  -DartifactId=mrt-auditwarpub -Dversion=codebuild \
  -Dpackaging=war \
  -DrepoUrl=${CODEARTIFACT_URL} 
RUN pwd >> /tmp/tb.log
RUN ls >> /tmp/tb.log

FROM ${ECR_REGISTRY}/merritt-tomcat:dev

COPY --from=build /tmp/tb.log /tmp/tb.log
RUN cat /tmp/tb.log

RUN mkdir -p /tdr/tmpdir/logs 

