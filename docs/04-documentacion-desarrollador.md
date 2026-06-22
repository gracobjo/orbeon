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
├── docs/                          ← Esta documentación
├── 684_F1b_MIXTO_480_Solicitud_PRE.txt
├── 684_F1b_MIXTO_480_Solicitud_v39.txt
├── orbeon-editor/                 ← Prototipo legado (Jetty)
└── src/
    ├── main/
    │   ├── java/com/orbeon/editor/
    │   │   ├── OrbeonEditorApplication.java
    │   │   ├── controller/
    │   │   │   ├── FormularioController.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── dto/               ← Objetos de transferencia API
    │   │   ├── model/             ← Entidades de dominio
    │   │   ├── service/           ← Lógica de negocio
    │   │   └── util/              ← Parseo XML y recursos
    │   └── resources/static/
    │       └── index.html         ← UI principal
    └── test/
        └── java/.../
            ├── SelectItemsVerificationTest.java
            └── NaturalLanguageServiceTest.java
```

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

### 4.7 `OrbeonCompareService`

Compara dos listas de componentes indexadas por `id`. Estados: `ANADIDO`, `ELIMINADO`, `MODIFICADO`. Campos comparados: label, hint, alert, tipo, appearance.

### 4.8 `OrbeonPdfService`

Genera PDF con OpenPDF. Respeta `class="noprintinpdf"`. Agrupa por secciones. **No** invoca Orbeon Server.

### 4.9 `OrbeonLogoService`

Detecta logos/imágenes (`fr:image`, adjuntos en instancia) y calcula **posición global**, **posición en sección**, `controlId`, `sectionId`, `filename`, `src`.

| Método | Descripción |
|--------|-------------|
| `analizarLogos(xml)` | `List<LogoEnFormulario>` |
| `describirLogos(xml)` | Texto legible para el asistente NL |

### 4.10 `OrbeonNaturalLanguageService`

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
| `AnalisisCalculadoras` | total, calculadoras[], glosarioFuentes{} |

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
  "calculadoras": { "total": 141, "calculadoras": [...], "glosarioFuentes": {} }
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
{ "xml": "...", "componentes": [] }
```

- **Respuesta:** `application/pdf` (binario)

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
| Modificar JSON | Editor `changes[]` |
| Cambios | Changelog acumulado |
| Código XML | Textarea + sincronizar; destino al localizar desde CRUD |

### Panel CRUD contextual (`#panelEditorCrud`)

Barra inferior del panel izquierdo. Funciones principales en `index.html`:

| Función | Descripción |
|---------|-------------|
| `abrirEditorCrud(tipo, meta)` | Muestra panel, renderiza formulario, llama `irACodigoXml` |
| `irACodigoXml(terminos)` | Selección en `#textareaXml` |
| `previsualizarEditorCrud()` | `POST /modificar` + `/sincronizar-codigo` sin actualizar `xmlActual` |
| `aplicarEditorCrud()` | Persiste cambios vía `/modificar` y `procesarRespuesta` |
| `descartarPreviewBorrador()` | Restaura snapshot de componentes/estructura/dependencias |

Tipos soportados: `logo`, `campo`, `desplegable`, `seccion`, `dependencia`, `calculadora`.

Guía de usuario: [08-editor-crud-contextual-y-preview.md](08-editor-crud-contextual-y-preview.md).

### Panel derecho

| Vista | Función |
|-------|---------|
| Vista Diseño | Render HTML por tipo de control; banner ámbar si hay preview borrador |
| Vista PDF | iframe con blob PDF |

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

## 9. Desarrollo local

### Requisitos

- JDK 17+
- Maven 3.8+

### Comandos

```bash
# Compilar y ejecutar
mvn spring-boot:run

# Tests
mvn test

# Test específico desplegables
mvn test -Dtest=SelectItemsVerificationTest

# JAR ejecutable
mvn package
java -jar target/orbeon-form-editor-1.0.0-SNAPSHOT.jar
```

### Puerto ocupado

```powershell
netstat -ano | findstr :8080
taskkill /PID <pid> /F
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

---

## 13. APIs externas

Resumen: **solo CDN Tailwind** en el navegador; URLs JCYL y Orbeon en XML sin invocar. Detalle completo en [05-apis-externas.md](05-apis-externas.md).

---

## 14. Referencias

- [Orbeon Form Runner documentation](https://doc.orbeon.com/)
- Carpeta legado: `orbeon-editor/src/main/java/com/orbeon/editor/OrbeonXmlService.java`
