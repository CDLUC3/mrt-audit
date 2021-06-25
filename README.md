# Merritt Audit Service

This microservice is part of the [Merritt Preservation System](https://github.com/CDLUC3/mrt-doc).

## Purpose

This microservice performs a fixity check on each instance of a file stored in the Merritt Preservation Service on a rotating basis. 
The goal is to check the each file every 3 months.  For offline copies of a file (Glacier), a metadata check is performed rather than a fixity check.

The work that this service performs is determined by querying the Merritt Inventory Database.

## Original System Specifications
- [Merritt Audit Service](https://github.com/CDLUC3/mrt-doc/blob/main/doc/Merritt-audit-service-latest.pdf)

## Component Diagram
[![Flowchart](https://github.com/CDLUC3/mrt-doc/raw/main/diagrams/audit.mmd.svg)](https://cdluc3.github.io/mrt-doc/diagrams/audit)

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
