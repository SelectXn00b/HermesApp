# compose_dsl artifact generator

Generates compose_dsl artifacts from Compose source jars in Gradle cache.

- Kotlin renderer registry:
  - `app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslGeneratedRegistry.kt`
- Kotlin renderers:
  - `app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslGeneratedRenderers.kt`
- TypeScript generated bindings:
  - `examples/types/compose-dsl.material3.generated.d.ts`

Run:

```bash
python tools/compose_dsl/generate_compose_dsl_artifacts.py
```

Notes:
- Source jars are resolved from `~/.gradle/caches/modules-2/files-2.1`.
- If jars are missing, sync/build once to download dependencies.
- Generator behavior:
  - Keeps dedicated renderers for core complex nodes (TextField/Icon/Card/Surface, etc.).
  - Auto-discovers additional public `@Composable` functions from Material3/Foundation and
    generates generic renderers/registry/TS props when required params are mappable.
  - `ToolPkgComposeDslGeneratedRenderers.kt.tpl` is now a thin scaffold with helper functions;
    component renderers are injected at `// __GENERATED_COMPONENT_RENDERERS__`.
