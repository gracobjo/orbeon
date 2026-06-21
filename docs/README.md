# Documentación — Orbeon Form Editor

Editor visual de plantillas XML de **Orbeon Form Runner** para lectura, edición, comparación y exportación.

## Índice

| Documento | Descripción |
|-----------|-------------|
| [01 — Requisitos funcionales](01-requisitos-funcionales.md) | Qué debe hacer el sistema (RF-xxx) |
| [02 — Requisitos no funcionales](02-requisitos-no-funcionales.md) | Rendimiento, seguridad, usabilidad, etc. (RNF-xxx) |
| [03 — Casos de uso](03-casos-de-uso.md) | Actores, flujos y escenarios (CU-xxx) |
| [04 — Documentación de desarrollador](04-documentacion-desarrollador.md) | Arquitectura, API REST, modelo de datos, despliegue |
| [05 — APIs y dependencias externas](05-apis-externas.md) | Qué servicios usa o referencia la aplicación (JCYL, CDN, etc.) |
| [06 — HOWTO arranque y CRUD](06-howto-arranque-y-crud.md) | Cómo arrancar el servidor y hacer CRUD (labels, hints, desplegables…) |
| [07 — Dependencias de secciones](07-dependencias-secciones.md) | Visibilidad condicional (`relevant`), pestaña Dependencias y CRUD |
| [08 — Editor CRUD contextual y preview](08-editor-crud-contextual-y-preview.md) | Clic en resultados → XML + CRUD + previsualizar antes de guardar |
| [Diagramas UML](diagramas/) | Fuentes PlantUML (casos de uso, clases, componentes, secuencia) |

## Stack tecnológico

- **Backend:** Java 17+, Spring Boot 3.2.5, DOM/XPath (javax.xml)
- **Frontend:** HTML5, JavaScript vanilla, Tailwind CSS (CDN `cdn.tailwindcss.com`)
- **Asistente NL:** parser de reglas en español (sin API de IA externa)
- **PDF:** OpenPDF 2.0.3
- **Build:** Maven

## Inicio rápido

```bash
mvn spring-boot:run
# Abrir http://localhost:8080
```

Guía detallada: **[06 — HOWTO arranque y CRUD](06-howto-arranque-y-crud.md)** (puerto ocupado, JAR, verificación, CRUD de labels/hints).

## Archivos de ejemplo

- `684_F1b_MIXTO_480_Solicitud_PRE.txt` — plantilla base
- `684_F1b_MIXTO_480_Solicitud_v39.txt` — versión alternativa para comparación

## Funcionalidades principales

- Carga, edición y exportación de plantillas Orbeon Form Runner
- Vista diseño, PDF mock, comparación entre versiones
- Motor de cambios JSON (`changes[]`)
- **Asistente en lenguaje natural** (pestaña «Asistente»): logos, desplegables CRUD, labels/hints
- **Dependencias de secciones** (pestaña «Dependencias»): análisis y CRUD de `xf:bind @relevant`
- **Editor CRUD contextual**: clic en logos, campos, secciones o dependencias → XML localizado + previsualización antes de guardar
- Análisis de logos con posición en sección

Ver [05 — APIs externas](05-apis-externas.md) para el detalle de dependencias de red.

La carpeta `orbeon-editor/` contiene un prototipo anterior (Jetty + servlets). La aplicación activa es el módulo Maven raíz (`orbeon-form-editor`).
