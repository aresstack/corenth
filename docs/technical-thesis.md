# Technical Thesis

Corenth is based on a practical research thesis:

> Local, Java-first AI integration on enterprise workstations is more practical than it first appears.

The common default assumption is that enterprise AI starts with cloud platforms, Python stacks, containers, dedicated GPU servers and large transformation programs. Corenth explores a different starting point: the existing workstation, the existing user boundary, the existing Java trust path and the existing enterprise environment.

## Why Java-first?

Java has a long enterprise history. It is understood by operations teams, security teams and application developers. It is familiar on workstations and servers, and it has mature tooling for packaging, inspection and maintenance.

That does not mean the whole system is magically free of native layers. Operating systems, drivers, GPU runtimes and enterprise infrastructure remain real boundaries. The Corenth thesis is narrower and more precise:

> Keep the integration layer Java-first and avoid unnecessary native application dependencies.

This can make local AI integration easier to reason about on hardened workstations than ad-hoc native or scripting-heavy stacks.

## Why local first?

Many useful AI-adjacent tasks are not full text generation:

- extracting text and metadata,
- indexing resources,
- building lexical search indexes,
- creating embeddings,
- reranking search results,
- preparing citations,
- visualizing structures,
- connecting resources under existing user permissions.

These tasks can often be performed near the workstation or within controlled local infrastructure. Heavier generation may still require a stronger model or remote compute, but Corenth keeps the first boundary local and measurable.

## Why workstations?

Enterprise work often already happens on secured Windows workstations. Those machines already sit inside the user's permission context. They already know the network, proxies, files, identity rules and local operational constraints.

Corenth treats that workstation not as a weak endpoint, but as a useful boundary:

> Bring AI capability closer to the work, without first rebuilding the organization around a new AI platform.

## Research status

Corenth is a reference architecture and scaffold. It does not claim to solve every enterprise AI problem. It captures a research direction that has been tested through practical building blocks and is intended to be refined through real modules, small interfaces and measurable constraints.
