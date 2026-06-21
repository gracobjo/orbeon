# Diagramas UML

Fuentes en formato **PlantUML** (`.puml`). Puedes visualizarlos con:

- Extensión **PlantUML** en VS Code / Cursor
- [PlantUML Online Server](https://www.plantuml.com/plantuml/uml/)
- CLI: `java -jar plantuml.jar docs/diagramas/*.puml`

## Índice de diagramas

| Archivo | Tipo UML | Descripción |
|---------|----------|-------------|
| [casos-uso.puml](casos-uso.puml) | Casos de uso | Actores y CU-01 a CU-10 |
| [clases-dominio.puml](clases-dominio.puml) | Clases | Modelo `model.*` y DTOs |
| [arquitectura-componentes.puml](arquitectura-componentes.puml) | Componentes | Capas Spring Boot |
| [secuencia-cargar-xml.puml](secuencia-cargar-xml.puml) | Secuencia | CU-01 Cargar plantilla |
| [secuencia-modificar-exportar.puml](secuencia-modificar-exportar.puml) | Secuencia | CU-04 / CU-05 Modificar y exportar |
| [secuencia-comparar.puml](secuencia-comparar.puml) | Secuencia | CU-08 Comparar versiones |
| [secuencia-lenguaje-natural.puml](secuencia-lenguaje-natural.puml) | Secuencia | CU-11 Asistente NL |
| [despliegue.puml](despliegue.puml) | Despliegue | JVM, navegador, archivos |

## Vista previa Mermaid (casos de uso simplificado)

```mermaid
flowchart TB
    subgraph Actores
        AF[Analista]
        DEV[Desarrollador]
        INT[Integrador]
    end
    subgraph Casos de uso
        UC1[Cargar XML]
        UC2[Explorar]
        UC3[Editar]
        UC4[JSON changes]
        UC5[Exportar]
        UC6[Vista diseño]
        UC7[PDF]
        UC8[Comparar]
    end
    AF --> UC1 & UC2 & UC6 & UC8
    DEV --> UC1 & UC3 & UC4 & UC5 & UC7
    INT --> UC4 & UC5 & UC8
    UC1 --> UC2
    UC1 --> UC6
    UC3 --> UC5
    UC4 --> UC5
```

## Vista previa Mermaid (secuencia cargar)

```mermaid
sequenceDiagram
    participant U as Usuario
    participant UI as index.html
    participant C as FormularioController
    participant F as OrbeonFormService
    participant R as OrbeonResourceParser

    U->>UI: Selecciona XML
    UI->>C: POST /cargar
    C->>F: parsearEstructuraDesdeString
    F->>R: extraerRecursos
    R-->>F: recursos + items
    F-->>C: componentes[]
    C-->>UI: FormularioResponse
    UI-->>U: Vista actualizada
```
