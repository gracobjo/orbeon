# Documentación — Orbeon Form Editor

Editor visual de plantillas XML de **Orbeon Form Runner** para lectura, edición, comparación y exportación.

## Índice

| Documento | Descripción |
|-----------|-------------|
| [**00 — Manual de usuario**](00-manual-usuario.md) | Guía práctica: botones, pestañas, flujos y limitaciones |
| [01 — Requisitos funcionales](01-requisitos-funcionales.md) | Qué debe hacer el sistema (RF-xxx) |
| [02 — Requisitos no funcionales](02-requisitos-no-funcionales.md) | Rendimiento, seguridad, usabilidad, etc. (RNF-xxx) |
| [03 — Casos de uso](03-casos-de-uso.md) | Actores, flujos y escenarios (CU-xxx) |
| [04 — Documentación de desarrollador](04-documentacion-desarrollador.md) | Arquitectura, API REST, modelo de datos, despliegue |
| [05 — APIs y dependencias externas](05-apis-externas.md) | Qué servicios usa o referencia la aplicación (JCYL, CDN, etc.) |
| [06 — HOWTO arranque y CRUD](06-howto-arranque-y-crud.md) | Arranque, Tomcat embebido, JAR portable, CRUD de labels/hints |
| [07 — Dependencias de secciones](07-dependencias-secciones.md) | Visibilidad condicional (`relevant`), pestaña Dependencias y CRUD |
| [08 — Editor CRUD contextual y preview](08-editor-crud-contextual-y-preview.md) | Clic en resultados → XML + CRUD + previsualizar antes de guardar |
| [09 — Calculadoras XForms](09-calculadoras-xforms.md) | `xf:bind @calculate`: inventario, tipos y edición en plantilla 684/480 |
| [10 — Controles genéricos y búsqueda XML](10-controles-genericos-y-busqueda-xml.md) | Detección `control-N`, renombrado CRUD y buscador en Código XML |
| [11 — Análisis PDF instrucciones](11-analisis-pdf-instrucciones.md) | Anotaciones del PDF → propuestas XML, estructura, comparación de PDFs y API |
| [12 — Roadmap y mejoras](12-roadmap-mejoras.md) | Pendientes, eficiencia y orden de implementación recomendado |
| [Diagramas UML](diagramas/) | Fuentes PlantUML (casos de uso, clases, componentes, secuencia) |

## Stack tecnológico

- **Backend:** Java 17+, Spring Boot 3.2.5, DOM/XPath (javax.xml)
- **Frontend:** HTML5, JavaScript vanilla, Tailwind CSS (CDN `cdn.tailwindcss.com`)
- **Asistente NL:** parser de reglas en español (sin API de IA externa)
- **PDF:** OpenPDF 2.0.3
- **Build:** Maven

## Inicio rápido

**Windows (desarrollo):** doble clic en `arrancar.cmd` (Maven en `.tools`).

**Windows (solo JAR, otro equipo):** compile el JAR, copie `arrancar-jar.cmd` y opcionalmente un JRE 17 portable en `jre\`. Ver [06 — HOWTO](06-howto-arranque-y-crud.md#parte-1--arrancar-el-servidor).

```bash
mvn spring-boot:run
# Abrir http://localhost:8080
```

> Maven portable: `arrancar.cmd` · Distribución: `arrancar-jar.cmd` + JAR. Servidor: **Tomcat embebido** en Spring Boot (puerto 8080).

## Archivos de ejemplo

Plantillas Orbeon de prueba (`.txt`, `.pdf`) se usan en local y **no se versionan** (ver `.gitignore`: `*.txt`, `*.pdf`). Ejemplos habituales en el equipo:

- `684_F1b_MIXTO_480_Solicitud_PRE.txt` — plantilla base
- `688_F1b_CTCON_2792_Solicitud_PRE_ini.txt` — incluye `control-1` genérico
- `684_F1b_MIXTO_480_Solicitud_v39.txt` — versión alternativa para comparación

Cárguelos con **Cargar XML base** desde su copia local.

## Funcionalidades principales

- Carga, edición y exportación de plantillas Orbeon Form Runner
- Vista diseño, **vista PDF** (rejillas JCYL, instancia cumplimentable), comparación entre versiones
- **Cumplimentar instancia** con preset del PDF Instrucciones 684 (Ayuntamiento de El Barco de Ávila)
- Motor de cambios JSON (`changes[]`)
- **Asistente en lenguaje natural** (pestaña «Asistente»): logos, desplegables CRUD, labels/hints
- **Dependencias de secciones** (pestaña «Dependencias»): análisis y CRUD de `xf:bind @relevant`
- **Calculadoras XForms** (pestaña «Calculadoras»): inventario de `xf:bind @calculate`, fuentes de datos y CRUD
- **Controles genéricos** (pestaña «Controles N»): detección de `control-N`, renombrado CRUD y aviso en comparador
- **Búsqueda en Código XML** con resaltado, patrones Orbeon y navegación desde el CRUD
- **Análisis de PDF de instrucciones** (barra superior): anotaciones → cambios XML con catálogo dinámico; modal con pestañas Propuestas, Estructura y XML; comparación entre dos PDFs de instrucciones
- **Comparador** en barra superior (sustituye carga JSON del header; JSON en pestaña Modificar JSON)
- **Editor CRUD contextual**: clic en logos, campos, secciones o dependencias → XML localizado + previsualización antes de guardar
- Análisis de logos con posición en sección

Ver [05 — APIs externas](05-apis-externas.md) para el detalle de dependencias de red.

La aplicación activa es el módulo Maven raíz (`orbeon-form-editor`, Spring Boot + Tomcat embebido).
