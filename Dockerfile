#*********************************************************************
#   Copyright 2021 Regents of the University of California
#   All rights reserved
#*********************************************************************

ARG ECR_REGISTRY=ecr_registry_not_set

FROM ${ECR_REGISTRY}/merritt-tomcat:dev

COPY audit-war/target/mrt-auditwarpub-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/audit.war

RUN mkdir -p /build/static/ && \
    date -r ingest-src/target +'mrt-ingest: %Y-%m-%d:%H:%M:%S' > /build/static/build.content.txt && \
    jar uf /usr/local/tomcat/webapps/audit.war -C /build static/build.content.txt

RUN mkdir -p /tdr/tmpdir/logs 

