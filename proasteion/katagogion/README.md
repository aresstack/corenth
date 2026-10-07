# Katagogion

Plugin and tool lodging house.

Katagogion is where outer extensions lodge: plugins contribute tools, and a headless host installs
them explicitly and runs them against mediated Corenth capabilities only. It keeps its Greek name
intentionally; the name marks a boundary in the architecture.

## What exists (#12, first slice)

| Concept | Type | Notes |
| --- | --- | --- |
| Plugin entry point | `CorenthPlugin` | Returns `descriptor()` and `tools()`. Receives no host, registry or global context. |
| Plugin identity | `PluginDescriptor` | Immutable id, version, display name, requested `ToolCapability` set. |
| Tool | `Tool`, `ToolDescriptor`, `ToolParameter` | Immutable declarations; string arguments only; required capabilities per tool. |
| Invocation / result | `ToolInvocation`, `ToolResult`, `ToolFailureReason` | Typed success (text + string records) or typed failure; no payload on failure. |
| Capabilities | `ToolContext`, `ToolCapabilities`, `LexicalSearch`, `MediatedReading` | The only things a tool can reach. |
| Admission | `ToolAdmissionPolicy`, `AllowlistToolAdmissionPolicy`, `ToolAdmission` | Fail-closed allowlist; over-requesting plugins are rejected, not downgraded. |
| Host | `ToolHost`, `PluginInstallation`, `PluginRejectionReason`, `ToolListing` | Explicit `install(plugin)`, `tools()`, `invoke(invocation)`. Instance-scoped registry, no statics. |
| Discovery | `discovery.PluginDiscovery`, `discovery.ServiceLoaderPluginDiscovery` | Finds `META-INF/services` providers in a host-chosen class loader; never installs. |
| Search binding | `mediation.SearchCoordinatorLexicalSearch` | Adapts the existing Acropolis `SearchCoordinator` use case. |
| Reference tools | `reference.CorenthReferencePlugin`, `LexicalSearchTool` (`corenth.search`), `ReadResourceTool` (`corenth.read`) | Built-in examples, installed explicitly, not via ServiceLoader. |

## Security model

- **Tools receive capabilities, not systems.** `ToolContext` exposes only `lexicalSearch()` and
  `mediatedReading()`. There is no accessor for files, network, Holkas, `AcquisitionPort`,
  `MediatedResourceService`, Tamias policies, Adyton secrets, the host or other plugins.
- **Two separate decisions.** Tool admission (`ToolAdmissionPolicy`) decides whether a plugin may
  offer tools using a capability. Every resource access a capability performs is still decided by
  Tamias inside the mediated use case. Admission never grants access to a resource, and the two
  policies are not merged.
- **All-or-nothing installation.** A plugin is installed only if it is admitted, each tool requires
  only capabilities that were requested, granted and offered by the host, and every tool name is
  free. Otherwise nothing of the plugin is registered and the rejection is typed.
- **Validated invocations.** Missing required and undeclared arguments are rejected before a tool
  runs. Using an ungranted capability at runtime yields `CAPABILITY_NOT_GRANTED`.
- **Error containment, not isolation.** The host turns a tool's runtime exception into
  `EXECUTION_FAILED` (reporting only the exception type). This is not a sandbox: plugin code runs in
  the Corenth JVM with the host's Java permissions, can use any class on its class path, and
  `Error`s are not caught. A capability interface or an allowlist is not an OS or class-loader
  sandbox; real isolation (separate class loader or process) is a follow-up decision.

## Not in this slice

- **MCP transport, browser automation, wd4j.** They follow an approved #44 cut. The contracts are
  shaped so that a later MCP adapter can expose them (`ToolDescriptor` ≈ tool declaration,
  `ToolInvocation` ≈ `tools/call`, `ToolResult` = strings only).
- **Production binding of `MediatedReading`.** It needs a `ResourceAccessRequest` with a
  host-bound actor, i.e. Tamias types that Katagogion must not import. The binding to
  `MediatedResourceAccess` belongs to the composition root (`proasteion:application`); until it
  exists, hosts install `CorenthReferencePlugin.lexicalSearchOnly()`, and
  `CorenthReferencePlugin.all()` is rejected with `CAPABILITY_UNAVAILABLE`. `ReadResourceTool` is
  covered against a fake capability only.
- **Dynamic installer.** No download, unpacking, versioned plugin directories or hot reload.
- **UI or menu coupling**, and no global `PluginContext`/service locator.

## Dependencies

`katagogion` → `astu` (identities in capability contracts) and `astu:acropolis` (the
`SearchCoordinator` use case, implementation only). The architecture tests forbid Katagogion to
depend on Holkas, Tamias and the composition root.
