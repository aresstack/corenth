# Corenth

Java-first local AI architecture for enterprise workstations.

Corenth is the AresStack reference architecture for bringing practical AI capabilities closer to existing enterprise workstations, existing user boundaries and existing Java trust paths.

It starts from a tested observation:

> Local, Java-first AI integration on enterprise workstations is more practical than it first appears.

The project is intentionally structured as a Gradle multi-project build. Each module represents one architectural responsibility and keeps the Greek naming scheme agreed for the Corenth architecture.

## Why Corenth

Enterprise AI does not always have to begin with a cloud-first platform, a new runtime stack or a multi-year transformation program.

A more grounded path is possible:

- use the workstation where the work already happens;
- keep the integration layer Java-first;
- respect existing user boundaries and permissions;
- index and process only what is intentionally allowed;
- combine lexical search, semantic search and local reranking where useful;
- keep cloud usage optional for tasks that genuinely need larger generative models.

Corenth turns the enterprise workstation into a trusted boundary for practical AI.

## Project structure

```text
corenth/
├── adyton/
├── astu/
│   ├── propylaea/
│   └── acropolis/
│       └── chalcotheca/
│           ├── tamias/
│           ├── anagraphai/
│           └── pinakes/
└── proasteion/
    ├── exedra/
    ├── katagogion/
    └── emporion/
        ├── holkas/
        └── deigma/
```

## Architectural layers

### adyton

Inner boundary and trust layer. This module is intended to define the protected core concepts: identity context, permission boundaries, credential access abstractions and security-sensitive interfaces.

### astu

The structured city of local enterprise knowledge. This layer contains the core resource, indexing and retrieval architecture.

### astu:propylaea

The gateway layer for virtual resources and protocol-based addressing. It is responsible for the abstractions that allow different sources to appear as addressable resources.

### astu:acropolis

The elevated core of the architecture. It coordinates resource processing, indexing rules and the stable interfaces used by higher-level capabilities.

### astu:acropolis:chalcotheca

The treasury of indexed knowledge. It groups the modules that store, describe and retrieve processed enterprise knowledge.

### astu:acropolis:chalcotheca:tamias

The steward of index governance and rules. It should handle allowlists, denylists, indexing policies and decisions about what may enter the searchable corpus.

### astu:acropolis:chalcotheca:anagraphai

The registry of records. It should define metadata, provenance, citations, resource identities and traceability for indexed material.

### astu:acropolis:chalcotheca:pinakes

The catalogue and retrieval module. It should contain lexical search, semantic search and reranking-facing abstractions.

### proasteion

The outer district. This is where adapters, connectors and external integration modules live.

### proasteion:exedra

Discussion and interpretation layer. It is intended for interaction-facing abstractions, question handling and future assistant-style integration points.

### proasteion:katagogion

The lodging place for incoming resources. It should host document ingestion, file processing and normalization interfaces.

### proasteion:emporion

The marketplace of connectors and exchange. It contains the modules that bring external resources and extracted structures into Corenth.

### proasteion:emporion:holkas

The carrier module for connectors. It should provide transport and source adapters such as local files, HTTP resources, enterprise systems or legacy protocols.

### proasteion:emporion:deigma

The sample and inspection module. It should provide parsers, source-code analysis abstractions, UAST-style concepts and structure extraction.

## Documentation

- [Technical Thesis](docs/technical-thesis.md)
- [Architecture Notes](docs/architecture-notes.md)

## Status

This repository is an early architecture scaffold. It intentionally contains documentation and module boundaries first. Implementation should grow from these boundaries instead of collapsing them too early.