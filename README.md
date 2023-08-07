# Merritt Audit Service

This microservice is part of the [Merritt Preservation System](https://github.com/CDLUC3/mrt-doc).

## Purpose

This microservice performs a fixity check on each instance of a file stored in the Merritt Preservation Service on a rotating basis. 
The goal is to check the each file every 3 months.  For offline copies of a file (Glacier), a metadata check is performed rather than a fixity check.

The work that this service performs is determined by querying the Merritt Inventory Database.

## Original System Specifications
- [Merritt Audit Service](https://github.com/CDLUC3/mrt-doc/blob/main/doc/Merritt-audit-service-latest.pdf)

## Component Diagram
```mermaid
%%{init: {'theme': 'neutral', 'securityLevel': 'loose', 'themeVariables': {'fontFamily': 'arial'}}}%%
graph TD
  RDS[(Inventory DB)]
  AUDIT(AUDIT - Fixity Check)
  click AUDIT href "https://github.com/CDLUC3/mrt-audit" "source code"

  subgraph flowchart
    subgraph cloud_storage
      CLDS3[/AWS S3/]
      CLDSDSC[/SDSC Minio/]
      CLDWAS[/Wasabi/]
      CLDGLC[/Glacier/]
    end

    RDS --> |acquire work| AUDIT
    AUDIT --> |fixity check| CLDS3
    AUDIT --> |metadata check| CLDGLC
    AUDIT --> |fixity check| CLDWAS
    AUDIT --> |fixity check| CLDSDSC
    AUDIT -.-> |record fixity| RDS
  end
  style CLDS3 fill:#77913C
  style CLDGLC fill:#77913C
  style CLDSDSC fill:#77913C
  style CLDWAS fill:#77913C
  style RDS fill:#F68D2F

  style AUDIT stroke:red,stroke-width:4px
```

## Dependencies

This code depends on the following Merritt Libraries.
- [Merritt Cloud API](https://github.com/CDLUC3/mrt-cloud)
- [Merritt Core Library](https://github.com/CDLUC3/mrt-core2)

## For external audiences
This code is not intended to be run apart from the Merritt Preservation System.

See [Merritt Docker](https://github.com/CDLUC3/merritt-docker) for a description of how to build a test instnce of Merritt.

## Build instructions
This code is deployed as a war file. The war file is built on a Jenkins server.

## Test instructions

## Internal Links

### Deployment and Operations at CDL

https://github.com/CDLUC3/mrt-doc-private/blob/main/uc3-mrt-audit.md
