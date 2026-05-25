# Adyton

Security vault boundary for credentials, keys and delegated access.

Adyton is the isolated vault of Corenth. It represents the boundary where credentials, keys and delegated access operations belong. Ordinary application logic should not reach into this module to read secrets. It should ask for controlled actions or signed/delegated access instead.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.
