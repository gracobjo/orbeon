# Documentación de desarrollador

**Proyecto:** Orbeon Form Editor (`orbeon-form-editor`)  
**Paquete base:** `com.orbeon.editor`

---

## 1. Visión general

Aplicación **Spring Boot** monolítica que expone una API REST y sirve un SPA ligero (`static/index.html`). No usa base de datos: el XML vive en el cliente y se envía en cada operación.

### Objetivo técnico

Parsear plantillas **Orbeon Form Runner** (XHTML + XForms + extensiones `fr:`) para:

1. Extraer metadatos de `fr-form-resources` (labels, hints, items de selects).
2. Recorrer `fr:view` y construir modelo de componentes.
3. Aplicar modificaciones al DOM XML.
4. Comparar dos versiones y generar PDF aproximado.
5. Interpretar instrucciones en lenguaje natural (español), analizar logos y calculadoras XForms.
6. Traducir anotaciones de PDF de instrucciones a cambios XML (catálogo dinámico + reglas 684/480 opcionales).

**APIs externas:** el backend no llama servicios HTTP externos. Ver [05-apis-externas.md](05-apis-externas.md).

---

## 2. Arquitectura en capas

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (index.html + Tailwind + fetch API)             │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP JSON / multipart
┌──────────────────────────▼──────────────────────────────┐
│  FormularioController          GlobalExceptionHandler     │
└──────────────────────────┬──────────────────────────────┘
                           │
     ┌─────────────────────┼─────────────────────┐
     ▼                     ▼                     ▼
 OrbeonFormService   OrbeonStructureService  OrbeonModificationService
 OrbeonCompareService   OrbeonPdfService
 OrbeonLogoService   OrbeonNaturalLanguageService
 OrbeonDependencyService   OrbeonCalculatorService
 OrbeonInstanceService   OrbeonPdfInstructionsService
 OrbeonInstructionsInterpreterService
                           │
     ┌─────────────────────┴─────────────────────┐
     ▼                     ▼                     ▼
 OrbeonResourceParser   OrbeonXmlUtil      model.* / dto.*
```

Diagrama detallado: [diagramas/arquitectura-componentes.puml](diagramas/arquitectura-componentes.puml)

---

## 3. Estructura del proyecto

```
orbeon/
├── pom.xml
├── arrancar.cmd                   ← Arranque Windows (Maven en .tools, desarrollo)
├── arrancar-jar.cmd               ← Arranque con JAR (distribución / otro PC)
├── docs/                          ← Esta documentación
├── .tools/                        ← Maven portable (versionado)
└── src/
    ├── main/
    │   ├── java/com/orbeon/editor/
    │   │   ├── OrbeonEditorApplication.java
    │   │   ├── controller/
    │   │   │   ├── FormularioController.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── dto/               ← Objetos de transferencia API
    │   │   ├── model/             ← Entidades de dominio
    │   │   ├── service/           ← Lógica de negocio (incl. OrbeonInstanceService)
    │   │   └── util/              ← Parseo XML y recursos
    │   └── resources/
    │       ├── datos/             ← Presets de instancia y catálogo PDF instrucciones
│       │   ├── instancia-ejemplo-instrucciones-684.json
│       │   └── instrucciones-684-mapeo.json
    │       └── static/
    │       └── index.html         ← UI principal
    └── test/
        └── java/.../
            ├── SelectItemsVerificationTest.java
            ├── NaturalLanguageServiceTest.java
            └── OrbeonXmlUtilTest.java
```

**`.gitignore` relevante:** `target/`, `.tools/` excepto JARs de Maven (`!/.tools/**/*.jar`), `*.pdf` y `*.txt` (plantillas Orbeon locales de prueba no se suben al repositorio).

---

## 4. Servicios principales

### 4.1 `OrbeonFormService`

**Responsabilidad:** Parseo plano de controles en `fr:view` y modificaciones simples por lista de `ComponenteFormulario`.

| Método | Descripción |
|--------|-------------|
| `parsearEstructuraDesdeString(xml)` | Lista de `ComponenteFormulario` |
| `aplicarModificacionesDesdeString(xml, lista)` | Actualiza label/hint en resources |

**Algoritmo de parseo:**

1. Construir mapa `bind → ref` desde instancia de datos.
2. `OrbeonResourceParser.extraerRecursos()` sobre `fr-form-resources`.
3. XPath: `//*[local-name()='view']//*[@id]`.
4. Filtrar contenedores (`section`, `grid`, `c`, …).
5. Resolver label/hint/alert por clave de recurso (`bind` sin `-bind`).
6. Para selects: `OrbeonResourceParser.configurarItemsSelect()`.

### 4.2 `OrbeonStructureService`

**Responsabilidad:** Vista jerárquica por `fr:section` → grids → controles.

Devuelve `EstructuraFormulario` con lista de `SeccionFormulario`, cada una con campos `ComponenteFormulario`.

### 4.3 `OrbeonResourceParser`

**Responsabilidad:** Lectura de `fr-form-resources` y resolución de `xf:itemset`.

| Método | Descripción |
|--------|-------------|
| `extraerRecursos(doc, xpath)` | Map clave → `RecursoFormulario` |
| `resolverItemsSelect(control, clave, resources)` | Items inline, itemset estático o vacío |
| `configurarItemsSelect(...)` | Items + metadatos `itemsetRef`, `itemsetDinamico`, `resourceUrl` |

**Patrón Orbeon para desplegables estáticos:**

```xml
<xf:itemset ref="$form-resources/personaFisica-tipoVia/item">
```

**Patrón dinámico:**

```xml
<fr:databound-select1 resource="http://servicios.jcyl.es/geolrest/geolServicio/Provincias">
  <xf:itemset ref="Provincia"/>
</fr:databound-select1>
```

> **Importante:** estas URLs las declara el XML Orbeon; **este editor no las invoca**. En runtime real las consume Orbeon Form Runner.

### 4.4 `OrbeonModificationService`

**Responsabilidad:** Motor `changes[]` tipado sobre DOM.

| Tipo | Parámetros |
|------|------------|
| `update-label` | `fieldId`, `label` |
| `update-hint` | `fieldId`, `hint` |
| `update-text` | `elementId`, `value` |
| `update-image` | `imageTag`, `src`, `filename`, `mediatype` |
| `hide-section` / `show-section` | `sectionId` |
| `update-resource` | `fieldId`, `resourceType`, `value` |
| `update-bind` | `bindId`, `attributes{}` |
| `remove-field` | `fieldId` |
| `add-field` | `sectionId`, `fieldName` |
| `add-select-item` | `fieldId`, `label`, `value` |
| `update-select-item` | `fieldId`, `value`, `label?`, `newValue?` |
| `remove-select-item` | `fieldId`, `value` |
| `add-image` | `imageTag`, `src?`, `filename?`, `mediatype?`, `sectionId?`, `label?` |
| `update-section-relevant` | `bindId` o `sectionId`, `relevant` \| `removeRelevant: true` |
| `update-calculator` | `bindId`, `calculate` \| `removeCalculate: true` |
| `rename-control-numeric` | `nombreActual`, `nombreNuevo` — renombra instancia, bind, control y resources de un campo `control-N` |

### 4.4b `OrbeonXmlUtil` (controles genéricos)

| Método | Descripción |
|--------|-------------|
| `detectarEtiquetasControlNumerico(xml)` | Lista ordenada de nombres `control-N` (regex sobre XML en bruto) |
| `analizarEtiquetasControlNumerico(xml)` | Lista de `EtiquetaControlNumerico` con bind, control, tipo y ocurrencias |
| `renombrarEtiquetaControlNumerico(xml, viejo, nuevo)` | Renombrado coherente en DOM |

Ver [10 — Controles genéricos y búsqueda XML](10-controles-genericos-y-busqueda-xml.md).

### 4.5 `OrbeonDependencyService`

**Responsabilidad:** Analizar visibilidad condicional de secciones/grids (`xf:bind @relevant`), extraer referencias `$variable` y clasificar tipo de visibilidad.

| Método | Descripción |
|--------|-------------|
| `analizar(xml)` | `AnalisisDependencias` con lista de elementos y glosario |
| `clasificar(expresion)` | `siempre_visible`, `condicional`, `oculta_fija`, etc. |
| `extraerReferencias(expresion)` | Lista de binds referenciados (`$documentoIdent-nifSol`, …) |

**API:** `POST /api/formulario/analizar-dependencias` — también incluido en respuesta de `/cargar`.

Ver [07 — Dependencias de secciones](07-dependencias-secciones.md).

### 4.6 `OrbeonCalculatorService`

**Responsabilidad:** Detectar `xf:bind` con atributo `calculate`, extraer fuentes de datos y clasificar el tipo de expresión.

| Método | Descripción |
|--------|-------------|
| `analizar(xml)` | `AnalisisCalculadoras` con lista de `CalculadoraFormulario` y glosario de fuentes |
| `clasificar(expresion)` | Tipo heurístico (vaciar según autónomo, contador provincia, API externa, etc.) |
| `extraerFuentes(expresion)` | Variables `$campo`, rutas `/form/...`, nodo actual (`.`) y URLs en `doc()` |

**API:** `POST /api/formulario/analizar-calculadoras` — también incluido en respuesta de `/cargar` y `/sincronizar-codigo`.

Ver [09 — Calculadoras XForms](09-calculadoras-xforms.md).

### 4.7 `OrbeonPdfInstructionsService`

Extrae **anotaciones del margen** de un PDF de instrucciones usando OpenPDF (`PdfReader`, anotaciones de página).

| Método | Descripción |
|--------|-------------|
| `extraerAnotaciones(byte[] pdf)` | `List<AnotacionInstruccionPdf>` con página, contenido, subtipo y posición |

### 4.8 `OrbeonInstructionsCatalogBuilder`

Genera el **catálogo de reglas** escaneando el XML cargado (declaraciones, anexos, secciones, textos). Fusiona opcionalmente `instrucciones-684-mapeo.json`.

### 4.9 `OrbeonInstructionsInterpreterService`

Traduce anotaciones + XML a **propuestas de cambio** usando el catálogo dinámico.

| Método | Descripción |
|--------|-------------|
| `analizar(pdfBytes, nombrePdf, xml, aplicar)` | `AnalisisInstruccionesResponse` con propuestas, `estructuraInstrucciones`, `xml`; si `aplicar`, delega en `OrbeonModificationService` |
| `compararPdfs(pdfBase, pdfNuevo, xml)` | `ComparacionInstruccionesResponse` — diff de anotaciones y campos entre dos PDFs |

**Modelo:** `PropuestaCambioXml` — `confianza` (`alta`/`media`), `aplicableAutomaticamente`, `cambios[]`, `camposAfectados[]`.

### 4.10 `OrbeonInstructionsStructureService`

Agrupa propuestas por **sección del formulario** (`EstructuraInstruccionesPdf`) y lista anotaciones sin mapear.

Ver [11 — Análisis PDF instrucciones](11-analisis-pdf-instrucciones.md).

### 4.11 `OrbeonCompareService`

Compara dos listas de componentes indexadas por `id`. Estados: `ANADIDO`, `ELIMINADO`, `MODIFICADO`. Campos comparados: label, hint, alert, tipo, appearance.

Además detecta etiquetas genéricas `control-N` en cada XML y reporta en `ComparacionResponse`: listas base/nuevo, añadidas y eliminadas entre versiones.

### 4.12 `OrbeonPdfService`

Genera PDF al **estilo impreso JCYL / Orbeon Form Runner** (referencia: `684 F1b Mixto - 480 Solicitud_Instrucciones.pdf`):

- Recorre `fr:section` y **rejillas** `fr:grid` / `fr:c` (12 columnas).
- Cabeceras de sección en mayúsculas con barra gris.
- Campos como **etiqueta + valor** leyendo `fr-form-instance`.
- Mapa **`etiquetas`** para mostrar provincia/municipio legibles en desplegables.
- No muestra hints `Formato:` como valores; omite IDs internos en declaraciones (solo texto `fr:explanation`).
- Desplegables: etiqueta de la opción seleccionada; `appearance="full"` como opciones marcadas.
- Excluye `noprintinpdf`, `Adme-section`, `verFirma-section` y evalúa `relevant` (`=`, `!=`, `and`, `or`, `xxf:non-blank`, `xxf:valid`).
- Paginación **N / total** en pie de página.

**Firma:** `generarPdf(xml, modificaciones)` y `generarPdf(xml, modificaciones, etiquetas)`.

**No** invoca Orbeon Server ni incrusta logos binarios.

### 4.13 `OrbeonInstanceService`

Cumplimenta nodos hoja de `fr-form-instance` y recalcula campos derivados para coherencia con el PDF.

| Método | Descripción |
|--------|-------------|
| `aplicarPreset(xml, preset)` | Carga `classpath:datos/instancia-ejemplo-{preset}.json` |
| `aplicarValores(xml, valores, etiquetas)` | Aplica mapa de valores y devuelve `ResultadoCumplimentacion` |

**Derivados automáticos:** `documentoIdent-tipodoc`, `tipoSolicitante`, `provincializador`, `centroGestor`, `centroDirectivo.*` según NIF y provincia.

**Preset incluido:** `instrucciones-684` — Ayuntamiento de El Barco de Ávila (`P0502100A`).

### 4.14 `OrbeonLogoService`

Detecta logos/imágenes (`fr:image`, adjuntos en instancia) y calcula **posición global**, **posición en sección**, `controlId`, `sectionId`, `filename`, `src`.

| Método | Descripción |
|--------|-------------|
| `analizarLogos(xml)` | `List<LogoEnFormulario>` |
| `describirLogos(xml)` | Texto legible para el asistente NL |

### 4.15 `OrbeonNaturalLanguageService`

Parser de **reglas en español** (sin LLM). Traduce instrucciones a `changes[]` o consultas.

| Intención | Ejemplo de instrucción |
|-----------|------------------------|
| `consulta-cantidad-logos` | «¿Cuántos logos tiene?» |
| `consulta-posicion-logos` | «¿Dónde está el logo?» |
| `sustituir-logo` | «Sustituir el logo iapa-img por /ruta/nueva.bin» |
| `anadir-logo` | «Añadir logo mi-logo en sección iapa-section» |
| `consulta-desplegables` | «Listar desplegables» |
| `consulta-opciones-desplegable` | «Listar opciones del desplegable tipo de vía» |
| `add-select-item` | «Añadir opción X con valor Y al desplegable Z» |
| `update-label` / `update-hint` | «Cambiar el label del campo …» |

---

## 5. Modelo de dominio

Diagrama: [diagramas/clases-dominio.puml](diagramas/clases-dominio.puml)

| Clase | Campos principales |
|-------|-------------------|
| `ComponenteFormulario` | id, tipo, label, hint, alert, appearance, items[], metadatos{} |
| `ItemSelect` | label, value |
| `RecursoFormulario` | label, hint, alert, items[] |
| `SeccionFormulario` | id, titulo, campos[], numGrids |
| `EstructuraFormulario` | secciones[], totalComponentes |
| `DiferenciaComponente` | tipoCambio, componenteBase, componenteNuevo, cambios[] |
| `CambioCampo` | campo, valorAnterior, valorNuevo |
| `LogoEnFormulario` | posicionGlobal, posicionEnSeccion, tag, controlId, sectionId, src, … |
| `CalculadoraFormulario` | bindId, ref, label, controlId, calculate, tipo, fuentes[] |
| `EtiquetaControlNumerico` | nombre, bindId, controlId, tipoControl, ocurrencias |
| `AnalisisCalculadoras` | total, calculadoras[], glosarioFuentes{} |
| `ResultadoCumplimentacion` | xml, etiquetas{}, camposAplicados |

**Metadatos relevantes en `ComponenteFormulario`:**

| Clave | Significado |
|-------|-------------|
| `resourceKey` | Clave en fr-form-resources |
| `bind` | ID del bind XForms |
| `ref` | Ruta imagen/adjunto |
| `itemsetRef` | Referencia del itemset (p. ej. `Provincia`) |
| `itemsetDinamico` | `"true"` si no hay items en XML |
| `resourceUrl` | URL servicio REST Orbeon databound |

---

## 6. API REST

**Base URL:** `http://localhost:8080/api/formulario`

### POST `/cargar`

- **Content-Type:** `multipart/form-data`
- **Campo:** `archivo` (XML)
- **Respuesta:** `FormularioResponse`

```json
{
  "xml": "<?xml ...",
  "componentes": [ { "id": "...", "tipo": "select1", "label": "...", "items": [...] } ],
  "estructura": { "secciones": [...], "totalComponentes": 300 },
  "dependencias": { "total": 42, "elementos": [...] },
  "calculadoras": { "total": 141, "calculadoras": [...], "glosarioFuentes": {} },
  "etiquetasControlNumerico": ["control-1"],
  "controlesGenericos": [
    { "nombre": "control-1", "bindId": "control-1-bind", "controlId": "control-1-control", "tipoControl": "explanation", "ocurrencias": 11 }
  ]
}
```

### POST `/sincronizar-codigo`

```json
{ "xml": "..." }
```

### POST `/exportar`

- **multipart:** `xmlActual` (string), `modificaciones` (archivo JSON opcional)

### POST `/vista-pdf`

```json
{
  "xml": "...",
  "componentes": [],
  "cumplimentarEjemplo": false,
  "presetInstancia": "instrucciones-684",
  "etiquetas": { "empresa-provincia": "Ávila" }
}
```

- **Respuesta:** `application/pdf` (binario)
- Si `cumplimentarEjemplo: true`, aplica el preset antes de generar.

### POST `/cumplimentar-instancia`

```json
{
  "xml": "...",
  "preset": "instrucciones-684",
  "valores": { "documentoIdent-nifSol": "P0502100A" },
  "etiquetas": {}
}
```

- **Respuesta:** `FormularioResponse` completo (xml, componentes, estructura, dependencias, calculadoras).

### POST `/comparar`

- **multipart:** `archivoBase`, `archivoNuevo`

### POST `/modificar`

```json
{
  "xml": "...",
  "changes": [
    { "type": "update-label", "fieldId": "personaFisica-nombre", "label": "Nombre" }
  ]
}
```

### POST `/lenguaje-natural`

```json
{
  "xml": "...",
  "instruccion": "¿Cuántos logos tiene?",
  "aplicarCambios": true
}
```

**Respuesta:** `NaturalLanguageResponse` — `intencion`, `respuesta`, `ejecutado`, `xml?`, `logos[]`, `cambiosPropuestos[]`, `componentes[]`, `estructura`.

### POST `/analizar-logos`

```json
{ "xml": "..." }
```

**Respuesta:**

```json
{
  "total": 1,
  "descripcion": "El formulario tiene 1 logo/imagen: …",
  "logos": [ { "tag": "iapa-img", "posicionGlobal": 1, "sectionTitulo": "IAPA", … } ]
}
```

### POST `/analizar-calculadoras`

```json
{ "xml": "..." }
```

**Respuesta:**

```json
{
  "total": 141,
  "glosarioFuentes": { "$empresa-provincia": 3, "$documentoIdent-autonomo": 29 },
  "calculadoras": [
    {
      "bindId": "documentoIdent-tipodoc-bind",
      "ref": "documentoIdent-tipodoc",
      "label": "Tipo documento",
      "controlId": "documentoIdent-tipodoc-control",
      "calculate": "for $id in $documentoIdent-nifSol return (...)",
      "tipo": "Inferir tipo documento",
      "fuentes": ["$documentoIdent-nifSol"]
    }
  ]
}
```

### POST `/analizar-instrucciones-pdf`

**multipart:** `pdf` (archivo), `xml` (texto), `aplicar` (boolean, opcional)

**Respuesta:** `AnalisisInstruccionesResponse` — `propuestas[]`, `estructuraInstrucciones`, `xml`; si `aplicar=true`, incluye `xml` modificado y `logAplicados[]`.

### POST `/comparar-instrucciones-pdf`

**multipart:** `pdfBase`, `pdfNuevo`, `xml`

**Respuesta:** `ComparacionInstruccionesResponse` — diff de anotaciones y campos afectados entre dos PDFs.

Ver [11 — Análisis PDF instrucciones](11-analisis-pdf-instrucciones.md).

### GET `/esquema-modificaciones`

Documentación JSON del formato `changes[]`.

Guía práctica de uso: [06-howto-arranque-y-crud.md](06-howto-arranque-y-crud.md).

---

## 7. Frontend (`index.html`)

### Estado en cliente

Variables globales JS (sin framework):

- `xmlActual` — XML en memoria
- `componentes` — lista plana
- `estructura` — jerarquía secciones
- `dependencias` — análisis de `relevant` (`OrbeonDependencyService`)
- `calculadoras` — análisis de `calculate` (`OrbeonCalculatorService`)
- `controlesGenericos` — campos `control-N` (`OrbeonXmlUtil`)
- `changelog` — cambios pendientes de la sesión
- `editorCrudActivo` — elemento en edición en el panel CRUD contextual
- `previewBorradorActivo` / `snapshotAntesPreview` — estado de previsualización sin guardar

### Pestañas panel izquierdo

| Pestaña | Función |
|---------|---------|
| Lista | Edición guiada label/hint; clic en tarjeta → editor CRUD |
| **Asistente** | Instrucciones en lenguaje natural; tarjetas de resultado clicables |
| Secciones | Árbol por sección; clic en cabecera/campo/imagen → editor CRUD |
| **Dependencias** | Visibilidad condicional; clic en tarjeta → editor CRUD |
| **Calculadoras** | `xf:bind @calculate`; fuentes de datos, filtros, edición inline y CRUD |
| **Controles N** | Detección `control-N`, lista filtrable y renombrado CRUD |
| Modificar JSON | Editor `changes[]` |
| Cambios | Changelog acumulado |
| Código XML | Textarea con **buscador** (patrones, resaltado, ◀/▶), capa de iluminación y sincronizar |

### Panel CRUD contextual (`#panelEditorCrud`)

Barra inferior del panel izquierdo. Funciones principales en `index.html`:

| Función | Descripción |
|---------|-------------|
| `abrirEditorCrud(tipo, meta)` | Muestra panel, renderiza formulario, llama `irACodigoXml` |
| `irACodigoXml(terminos)` | Selección y scroll en `#textareaXml`; resaltado en `#xmlHighlightLayer` |
| `ejecutarBusquedaXml()` / `actualizarResaltadoBusquedaXml()` | Buscador con patrones Orbeon y marcas amarillo/naranja |
| `previsualizarEditorCrud()` | `POST /modificar` + `/sincronizar-codigo` sin actualizar `xmlActual` |
| `aplicarEditorCrud()` | Persiste cambios vía `/modificar` y `procesarRespuesta` |
| `descartarPreviewBorrador()` | Restaura snapshot de componentes/estructura/dependencias |

Tipos soportados: `logo`, `campo`, `desplegable`, `seccion`, `dependencia`, `calculadora`, `control-generico`.

Guías: [08-editor-crud-contextual-y-preview.md](08-editor-crud-contextual-y-preview.md), [10-controles-genericos-y-busqueda-xml.md](10-controles-genericos-y-busqueda-xml.md).

### Panel derecho

| Vista | Función |
|-------|---------|
| Vista Diseño | Render HTML por tipo de control; desplegables estáticos **explorables** (`preview-select`); banner ámbar si hay preview borrador |
| Vista PDF | iframe con blob PDF; botones **Cumplimentar ejemplo**, Actualizar, Descargar |

---

## 8. Conceptos Orbeon relevantes

| Concepto | Ubicación en XML |
|----------|------------------|
| Recursos (i18n) | `xf:instance id="fr-form-resources"` |
| Datos por defecto | `xf:instance id="fr-form-instance"` |
| Vista visual | `fr:body` → `fr:view` |
| Binds / validación | `xf:bind` en modelo |
| Modo PDF | Atributos `class="noprintinpdf"` |

---

## 9. Servidor embebido, arranque y despliegue

### 9.1 Servidor web embebido

| Aspecto | Detalle |
|---------|---------|
| Framework | Spring Boot **3.2.5** |
| Dependencia | `spring-boot-starter-web` |
| Servidor | **Apache Tomcat embebido** (no requiere instalación externa) |
| Puerto por defecto | `8080` (`server.port` en `application.properties`) |
| UI | `src/main/resources/static/index.html` |
| API | `/api/formulario/*` (`FormularioController`) |
| Subida de archivos | Hasta 50 MB (`spring.servlet.multipart.*`) |

Al ejecutar `OrbeonEditorApplication`, Tomcat arranca en el mismo proceso JVM que la aplicación.

### 9.2 Requisitos en el equipo destino

| Componente | ¿Obligatorio? |
|------------|---------------|
| Java **17+** (JRE o JDK) | Sí |
| Maven | No, si se distribuye el JAR compilado |
| Tomcat / IIS / nginx | No |
| Internet | Solo para CDN Tailwind en el navegador |

### 9.3 Desarrollo local

**Requisitos:** JDK 17+, Maven 3.8+ (o Maven en `.tools` vía `arrancar.cmd`).

```bash
# Windows: arrancar.cmd (Maven en .tools)

# Compilar y ejecutar
mvn spring-boot:run

# Tests
mvn test

# JAR ejecutable
mvn package -DskipTests
java -jar target/orbeon-form-editor-1.0.0-SNAPSHOT.jar

# Windows sin Maven global
arrancar-jar.cmd
```

### 9.4 Distribución en otro equipo (JAR + JRE portable)

1. Compilar: `.tools/apache-maven-3.9.16/bin/mvn.cmd package -DskipTests`
2. Copiar `target/orbeon-form-editor-1.0.0-SNAPSHOT.jar` y `arrancar-jar.cmd`
3. Opcional: JRE 17 portable en subcarpeta `jre/` (Temurin `.zip`)
4. Ejecutar `arrancar-jar.cmd` → http://localhost:8080

Guía paso a paso: [06-howto-arranque-y-crud.md](06-howto-arranque-y-crud.md) (Parte 1, opciones C y D).

### 9.5 Puerto ocupado

```powershell
netstat -ano | findstr :8080
taskkill /PID <pid> /F
```

### 9.6 Tests útiles

```bash
mvn test -Dtest=SelectItemsVerificationTest
mvn test -Dtest=OrbeonInstanceServiceTest
mvn test -Dtest=OrbeonPdfServiceTest
mvn test -Dtest=OrbeonXmlUtilTest
```

---

## 10. Añadir un nuevo tipo de modificación

1. Añadir case en `OrbeonModificationService.aplicarCambio()`.
2. Implementar método privado que manipule DOM vía `OrbeonXmlUtil`.
3. Documentar en `obtenerEsquema()`.
4. Opcional: UI en pestaña «Modificar JSON» con plantilla.

---

## 11. Añadir soporte de nuevo tipo de control

1. Incluir local-name en `TIPOS_CONTROL_VISUAL` de `OrbeonFormService`.
2. Si tiene items especiales → extender `OrbeonResourceParser`.
3. Añadir rama de render en `index.html` (función vista diseño).
4. Opcional: rama en `OrbeonPdfService`.

---

## 12. Tests

| Test | Cobertura |
|------|-----------|
| `SelectItemsVerificationTest` | Desplegables PRE y v39: opciones estáticas y dinámicos |
| `NaturalLanguageServiceTest` | Logos, consultas NL, CRUD opciones desplegable |
| `OrbeonInstanceServiceTest` | Preset instrucciones-684 aplica NIF y CIF |
| `OrbeonPdfServiceTest` | Generación PDF vacío y cumplimentado multipágina |
| `OrbeonXmlUtilTest` | Detección `control-N`, renombrado `rename-control-numeric` |

---

## 13. APIs externas

Resumen: **solo CDN Tailwind** en el navegador; URLs JCYL y Orbeon en XML sin invocar. Detalle completo en [05-apis-externas.md](05-apis-externas.md).

---

## 14. Referencias

- [Orbeon Form Runner documentation](https://doc.orbeon.com/)
- [Eclipse Temurin 17](https://adoptium.net/) — JRE portable para despliegue
