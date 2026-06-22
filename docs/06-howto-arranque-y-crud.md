# HOWTO — Arranque del servidor y CRUD

Guía práctica para poner en marcha **Orbeon Form Editor** y modificar plantillas XML: etiquetas (labels), hints, alerts, desplegables, logos y más.

---

## Parte 1 — Arrancar el servidor

### Requisitos previos

| Componente | Versión mínima | Comprobar |
|------------|----------------|-----------|
| Java JDK | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Navegador | Chrome, Edge o Firefox reciente | — |

En Windows, si Maven no está en el PATH tras instalarlo (p. ej. con Scoop), abre una **nueva** terminal o reinicia el IDE.

### Opción A — Script Windows (recomendado)

Doble clic en **`arrancar.cmd`** en la raíz del proyecto, o desde consola:

```cmd
arrancar.cmd
```

El script usa Maven empaquetado en `.tools\apache-maven-3.9.16\bin\mvn.cmd` (no requiere Maven en el PATH). Si el puerto **8080** ya está en uso, avisa antes de arrancar — cierra la instancia anterior para cargar la última versión del código.

### Opción B — Modo desarrollo (Maven en PATH)

Desde la raíz del proyecto (`orbeon/`):

```bash
mvn spring-boot:run
```

Cuando aparezca en consola algo como `Started OrbeonEditorApplication`, abre:

**http://localhost:8080**

La interfaz web se sirve automáticamente desde `src/main/resources/static/index.html`.

### Opción C — JAR ejecutable

```bash
mvn package -DskipTests
java -jar target/orbeon-form-editor-1.0.0-SNAPSHOT.jar
```

### Cambiar el puerto

Si el **8080** está ocupado, crea o edita `src/main/resources/application.properties`:

```properties
server.port=8081
```

O arranca con variable de entorno:

```bash
# Linux / macOS
SERVER_PORT=8081 mvn spring-boot:run

# Windows PowerShell
$env:SERVER_PORT=8081; mvn spring-boot:run
```

### Liberar el puerto 8080 (Windows)

```powershell
netstat -ano | findstr :8080
taskkill /PID <número_pid> /F
```

### Verificar que el servidor responde

```bash
curl http://localhost:8080/api/formulario/esquema-modificaciones
```

Debe devolver JSON con la lista de tipos de cambio (`update-label`, `update-hint`, etc.).

### Ejecutar tests (opcional)

```bash
mvn test
```

### Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---------|----------------|----------|
| `mvn` no reconocido | Maven no en PATH | Usar **`arrancar.cmd`** o ruta `.tools\apache-maven-3.9.16\bin\mvn.cmd` |
| Puerto en uso | Otra instancia en 8080 | Matar proceso o cambiar `server.port`; **reiniciar** tras cambios de código |
| **Cumplimentar ejemplo → Not Found** | Servidor antiguo en 8080 | Cerrar consola anterior y ejecutar `arrancar.cmd` de nuevo |
| UI sin estilos | Sin internet | Tailwind se carga de CDN; ver [05-apis-externas.md](05-apis-externas.md) |
| XML no carga | Archivo corrupto o no Orbeon | Comprobar que es exportación Form Runner |

---

## Parte 2 — Flujo de trabajo básico

```
1. Arrancar servidor  →  http://localhost:8080
2. Cargar XML base    →  botón «Cargar XML base»
3. Localizar elemento →  Lista / Secciones / Dependencias / Asistente (clic en resultado)
4. Editar             →  Panel CRUD contextual + Código XML localizado
5. Previsualizar      →  Vista Diseño (borrador sin guardar) → Aplicar al XML
6. Exportar           →  «Exportar XML de Salida»
7. Importar en Orbeon →  Form Builder / despliegue
```

### Vista PDF y cumplimentación de instancia

1. Cargar `684_F1b_MIXTO_480_Solicitud_v39.txt`.
2. Pestaña **Vista PDF** → **Cumplimentar ejemplo** rellena `fr-form-instance` con el preset `instrucciones-684` (datos del PDF de referencia *684 F1b Mixto - 480 Solicitud_Instrucciones*).
3. **Actualizar PDF** genera el impreso con datos de instancia, etiquetas de provincia/municipio y secciones visibles según `relevant`.

**API:**

```bash
POST /api/formulario/cumplimentar-instancia
{ "xml": "...", "preset": "instrucciones-684" }

POST /api/formulario/vista-pdf
{ "xml": "...", "cumplimentarEjemplo": true, "presetInstancia": "instrucciones-684" }
```

Preset JSON: `src/main/resources/datos/instancia-ejemplo-instrucciones-684.json`.

> **Nuevo:** al pulsar un logo, campo, sección o dependencia en la UI se abre el **editor CRUD contextual** con el XML resaltado y opción de **previsualizar antes de guardar**. Guía completa: **[08 — Editor CRUD contextual y preview](08-editor-crud-contextual-y-preview.md)**.

Archivos de prueba en la raíz del proyecto:

- `684_F1b_MIXTO_480_Solicitud_PRE.txt`
- `684_F1b_MIXTO_480_Solicitud_v39.txt`

---

## Parte 3 — Dónde vive cada cosa en el XML Orbeon

Orbeon separa **recursos de interfaz** (textos visibles) de **vista** (controles) y **datos** (instancia).

| Qué quieres cambiar | Dónde está en el XML | Clave que usa el editor |
|---------------------|----------------------|-------------------------|
| **Label** del campo | `fr-form-resources` → `<campo><label>` | `fieldId` = nombre del campo (sin `-control`) |
| **Hint** (pista) | `fr-form-resources` → `<campo><hint>` | Igual |
| **Alert** (validación) | `fr-form-resources` → `<campo><alert>` | Igual |
| **Opciones desplegable** | `fr-form-resources` → `<campo><item>` | `fieldId` + `value` de la opción |
| **Logo / imagen** | `fr-form-instance` → nodo `<iapa-img>` etc. | `imageTag` |
| **Control visual** | `fr:view` → `xf:input`, `xf:select1`, `fr:image` | `id` del control (ej. `…-control`) |
| **Texto explicativo** | `fr-form-resources` → `<campo><text>` o nodo en vista | `elementId` / `update-text` |

### Ejemplo real en la plantilla PRE

**Instancia de recursos** (`id="fr-form-resources"`):

```xml
<personaFisica-tipoVia>
    <label>Tipo de vía</label>
    <hint/>
    <item>
        <label>CALLE</label>
        <value>CL</value>
    </item>
    ...
</personaFisica-tipoVia>
```

**Vista** (el control referencia el recurso, no guarda el texto):

```xml
<xf:select1 id="personaFisica-tipoVia-control" bind="personaFisica-tipoVia-bind">
    <xf:label ref="$form-resources/personaFisica-tipoVia/label"/>
    <xf:hint ref="$form-resources/personaFisica-tipoVia/hint"/>
    <xf:itemset ref="$form-resources/personaFisica-tipoVia/item"/>
</xf:select1>
```

Por eso, al cambiar un label o hint debes modificar **`fr-form-resources`**, no el `xf:label` de la vista. El editor lo hace automáticamente.

### Cómo obtener el `fieldId`

| Si conoces… | `fieldId` es… |
|-------------|---------------|
| Id del control `personaFisica-nombre-control` | `personaFisica-nombre` (quitar `-control`) |
| Bind `personaFisica-nombre-bind` | `personaFisica-nombre` (quitar `-bind`) |
| Label visible «Tipo de vía» | Buscar en pestaña Lista o usar Asistente |

---

## Parte 4 — CRUD de etiquetas (labels, hints, alerts)

### Resumen de operaciones

| Operación | Label / Hint / Alert | Mecanismo |
|-----------|----------------------|-----------|
| **Read** | Ver valor actual | Cargar XML → pestaña Lista o Secciones |
| **Update** | Cambiar texto | Lista + Exportar, JSON, o Asistente |
| **Create** | Añadir hint/alert vacío | `update-resource` o edición que crea el nodo hijo si no existe |
| **Delete** | Vaciar texto | Poner cadena vacía `""` (no elimina el nodo XML) |

> Orbeon casi siempre mantiene los nodos `<label>`, `<hint>`, `<alert>` aunque estén vacíos. El editor **actualiza el texto** o **crea el hijo** si falta; no borra la estructura del campo.

---

### Método 4 — Panel visual en pestaña Asistente

Tras cargar el XML, la pestaña **Asistente** muestra todos los desplegables detectados:

1. **Filtrar** por label o id en el buscador.
2. Pulsar **▶** en un desplegable para expandirlo.
3. **Estáticos:** tabla con opciones (label + valor), botones ✓ guardar y ✕ eliminar, formulario **+ Añadir**.
4. **Dinámicos:** aviso violeta con la URL del servicio (sin CRUD).
5. Banner verde/rojo bajo el título confirma éxito o error de cada operación.

No hace falta escribir «tipo de vía» ni ningún nombre concreto: eliges el desplegable de la lista.

---

### Método 1 — Interfaz gráfica (pestaña Lista)

1. Carga el XML.
2. Pestaña **Lista**.
3. Busca el campo (por id, label o tipo).
4. Edita **Label**, **Hint** o **Alert** en los inputs.
5. La **Vista Diseño** se actualiza al instante (solo en memoria del navegador).
6. Pulsa **Exportar XML de Salida**.

Al exportar, el frontend envía la lista de `componentes` modificados y el backend escribe en `fr-form-resources` vía `OrbeonFormService.aplicarModificacionesDesdeString()`.

**Importante:** hasta que no exportes (o apliques JSON / Asistente), el XML en la pestaña **Código XML** no cambia. La edición en Lista es en memoria + exportación.

---

### Método 2 — JSON tipado (pestaña Modificar JSON)

Más preciso para scripts y lotes de cambios.

#### Actualizar label

```json
{
  "changes": [
    {
      "type": "update-label",
      "fieldId": "personaFisica-tipoVia",
      "label": "Tipo de vía (domicilio)"
    }
  ]
}
```

#### Actualizar hint

```json
{
  "changes": [
    {
      "type": "update-hint",
      "fieldId": "personaFisica-nif",
      "hint": "Introduzca DNI, NIE o pasaporte"
    }
  ]
}
```

#### Actualizar alert

```json
{
  "changes": [
    {
      "type": "update-alert",
      "fieldId": "personaFisica-cPostal",
      "alert": "El código postal debe tener 5 dígitos"
    }
  ]
}
```

> **Nota:** el tipo `update-alert` no está en el switch actual; usa **`update-resource`** con `resourceType: "alert"`:

```json
{
  "type": "update-resource",
  "fieldId": "personaFisica-cPostal",
  "resourceType": "alert",
  "value": "El código postal debe tener 5 dígitos"
}
```

#### Aplicar desde la UI

1. Pestaña **Modificar JSON** → pegar JSON → **Aplicar cambios**.
2. El XML se actualiza en **Código XML** y en todas las vistas.
3. Revisa la pestaña **Cambios** (changelog).

#### Aplicar vía API (curl)

```bash
curl -X POST http://localhost:8080/api/formulario/modificar \
  -H "Content-Type: application/json" \
  -d "{\"xml\":\"<contenido xml>\",\"changes\":[{\"type\":\"update-label\",\"fieldId\":\"personaFisica-tipoVia\",\"label\":\"Tipo de vía\"}]}"
```

---

### Método 3 — Asistente en lenguaje natural

Pestaña **Asistente** → escribe en español → **Ejecutar**.

| Instrucción | Efecto |
|-------------|--------|
| `Cambiar el label del campo tipo de vía a Vía del domicilio` | `update-label` |
| `Cambiar el hint del campo nif a Introduzca DNI o NIE` | `update-hint` |
| `Listar desplegables` | Solo consulta |
| `Listar opciones del desplegable tipo de vía` | Solo consulta |

Desmarca **Aplicar cambios al XML** para consultar sin modificar.

---

### Qué ocurre en el XML tras un cambio de label

**Antes:**

```xml
<personaFisica-tipoVia>
    <label>Tipo de vía</label>
    ...
</personaFisica-tipoVia>
```

**Después** (`update-label` con `"Vía del domicilio"`):

```xml
<personaFisica-tipoVia>
    <label>Vía del domicilio</label>
    ...
</personaFisica-tipoVia>
```

El nodo `xf:label ref="$form-resources/personaFisica-tipoVia/label"` en la vista **no se toca**; Orbeon resuelve el texto en runtime desde resources.

---

## Parte 5 — CRUD de desplegables (opciones)

Solo desplegables **estáticos** (`xf:itemset ref="$form-resources/.../item"`). Los dinámicos JCYL (`servicios.jcyl.es`) no admiten CRUD en el XML.

### Read

- UI: pestaña Lista / Vista Diseño (select con opciones).
- Asistente: `Listar opciones del desplegable tipo de vía`.

### Create — añadir opción

**JSON:**

```json
{
  "changes": [
    {
      "type": "add-select-item",
      "fieldId": "personaFisica-tipoVia",
      "label": "PEATONAL",
      "value": "PE"
    }
  ]
}
```

**Asistente:**

```
Añadir opción PEATONAL con valor PE al desplegable tipo de vía
```

**XML resultante** (nuevo `<item>` al final del campo):

```xml
<item>
    <label>PEATONAL</label>
    <value>PE</value>
</item>
```

### Update — cambiar etiqueta de una opción

```json
{
  "type": "update-select-item",
  "fieldId": "personaFisica-tipoVia",
  "value": "CL",
  "label": "CALLE / VÍA"
}
```

**Asistente:** `Cambiar opción CL del desplegable tipo de vía por CALLE PRINCIPAL`

### Delete — eliminar opción

```json
{
  "type": "remove-select-item",
  "fieldId": "personaFisica-tipoVia",
  "value": "PE"
}
```

**Asistente:** `Eliminar opción PE del desplegable tipo de vía`

---

## Parte 6 — CRUD de logos e imágenes

### Read

- Asistente: `¿Cuántos logos tiene?` / `¿Dónde está el logo?`
- API: `POST /api/formulario/analizar-logos` con `{ "xml": "..." }`
- Pestaña Secciones → bloque «Imágenes en instancia»

Tras una consulta de logos en el Asistente, **pulsa la tarjeta del resultado** (`iapa-img → editar`) para abrir el editor CRUD y localizar el nodo en el XML. Ver [08 — Editor CRUD contextual](08-editor-crud-contextual-y-preview.md).

### Update — sustituir logo

**JSON:**

```json
{
  "type": "update-image",
  "imageTag": "iapa-img",
  "src": "/fr/service/persistence/.../nuevo.bin",
  "filename": "nuevo_logo.png",
  "mediatype": "image/png"
}
```

**Asistente:** `Sustituir el logo iapa-img por /fr/service/.../nuevo.bin`

Modifica el nodo en **`fr-form-instance`**, no en resources.

### Create — añadir logo

```json
{
  "type": "add-image",
  "imageTag": "logo-secundario",
  "src": "/ruta/imagen.bin",
  "filename": "logo.png",
  "mediatype": "image/png",
  "sectionId": "iapa-section",
  "label": "Logo secundario"
}
```

---

## Parte 7 — Otros tipos de CRUD

| Tipo JSON | Operación | Parámetros clave |
|-----------|-----------|------------------|
| `update-text` | Cambiar texto HTML explicativo | `elementId`, `value` |
| `hide-section` / `show-section` | Ocultar/mostrar sección | `sectionId` |
| `update-bind` | Atributos XForms del bind (`calculate`, `relevant`, `readonly`, …) | `bindId`, `attributes{}` |
| `update-section-relevant` | Visibilidad condicional de sección/grid | `sectionId` o `bindId`, `relevant` o `removeRelevant` |
| `remove-field` | Quitar control del view | `fieldId` (id del control) |
| `add-field` | Añadir nodo en instancia | `sectionId`, `fieldName` |

Consulta el esquema completo:

```
GET http://localhost:8080/api/formulario/esquema-modificaciones
```

### Calculadoras (`calculate`)

Los campos calculados automáticamente (`xf:bind @calculate`) tienen pestaña propia **Calculadoras** en la UI.

1. Cargar el XML.
2. Pestaña **Calculadoras** — listado con fuentes de datos, filtros y glosario.
3. Clic en tarjeta → editor CRUD contextual + XML localizado.
4. **Editar aquí** / **Quitar calculate** / **Exportar XML de Salida**.

Guía completa: **[09 — Calculadoras XForms](09-calculadoras-xforms.md)**

Edición vía JSON (`update-calculator`):

```json
{
  "changes": [
    {
      "type": "update-calculator",
      "bindId": "provincializador-bind",
      "calculate": "$empresa-provincia"
    }
  ]
}
```

Eliminar el atributo `calculate`:

```json
{
  "changes": [
    {
      "type": "update-calculator",
      "bindId": "provincializador-bind",
      "removeCalculate": true
    }
  ]
}
```

---

## Parte 8 — CRUD de dependencias entre secciones

Las secciones que se activan o desactivan al marcar checkboxes u otras condiciones se controlan con **`xf:bind @relevant`**.

### Desde la interfaz (recomendado)

1. Cargar el XML.
2. Pestaña **Dependencias**.
3. Buscar la sección (p. ej. `vinculadas-section`).
4. **Clic en la tarjeta** o **Editar aquí** → se abre el panel CRUD contextual y el XML localizado.
5. Modificar la expresión XPath (o usar atajos en la tarjeta inline: Siempre visible / Oculta fija / Visible fija).
6. **Previsualizar** → comprobar en Vista Diseño → **Aplicar al XML**.
7. Exportar cuando termine.

Alternativa inline en la misma tarjeta: **Editar** → textarea → **Guardar en XML** (aplica de inmediato, sin paso de preview).

Documentación completa: **[07 — Dependencias de secciones](07-dependencias-secciones.md)** y **[08 — Editor CRUD contextual](08-editor-crud-contextual-y-preview.md)**.

### Desde JSON (API / Modificar JSON)

Ocultar sección de empresas vinculadas salvo checkbox marcado (ejemplo ya existente en plantilla):

```json
{
  "changes": [
    {
      "type": "update-section-relevant",
      "sectionId": "vinculadas-section",
      "relevant": "(xxf:non-blank($documentoIdent-nifSol) and xxf:valid($documentoIdent-nifSol)) and $otrosDatosEmpresa-vinculadas/string() ='true'"
    }
  ]
}
```

Forzar sección siempre visible:

```json
{
  "changes": [
    {
      "type": "update-section-relevant",
      "bindId": "modoNotificacion-bind",
      "removeRelevant": true
    }
  ]
}
```

Análisis sin modificar:

```bash
curl -X POST http://localhost:8080/api/formulario/analizar-dependencias \
  -H "Content-Type: application/json" \
  -d "{\"xml\": \"$(cat 684_F1b_MIXTO_480_Solicitud_v39.txt | sed 's/\"/\\\\\"/g')\"}"
```

*(En PowerShell use un cliente REST o la pestaña Dependencias de la UI.)*

---

## Parte 9 — Tabla resumen: formas de editar

| Forma | Mejor para | Persiste en XML al instante |
|-------|------------|----------------------------|
| **Editor CRUD contextual** (clic en resultado) | Logos, campos, secciones, dependencias; preview antes de guardar | Al **Aplicar al XML** |
| **Lista** + Exportar | Edición visual masiva de labels/hints/alerts | Al exportar |
| **Dependencias** (inline) | Reglas de visibilidad sin preview | Al guardar |
| **Modificar JSON** | Lotes, CI/CD, cambios complejos | Al aplicar |
| **Asistente** | Consultas y órdenes en español | Al ejecutar (si «Aplicar» activo) |
| **Código XML** manual | Expertos Orbeon | Tras **Sincronizar** |

---

## Parte 10 — Ejemplo completo paso a paso

**Objetivo:** cambiar el label de «Tipo de vía» y añadir una opción al desplegable.

1. `mvn spring-boot:run`
2. Abrir http://localhost:8080
3. Cargar `684_F1b_MIXTO_480_Solicitud_PRE.txt`
4. Pestaña **Modificar JSON** → pegar:

```json
{
  "changes": [
    {
      "type": "update-label",
      "fieldId": "personaFisica-tipoVia",
      "label": "Tipo de vía del domicilio"
    },
    {
      "type": "add-select-item",
      "fieldId": "personaFisica-tipoVia",
      "label": "SENDA",
      "value": "SND"
    }
  ]
}
```

5. **Aplicar cambios**
6. Comprobar en **Vista Diseño** y **Código XML** (buscar `personaFisica-tipoVia`)
7. **Exportar XML de Salida**
8. Importar el XML en Orbeon Form Builder

---

## Referencias

- [08 — Editor CRUD contextual y preview](08-editor-crud-contextual-y-preview.md) — clic en resultados, previsualizar antes de guardar
- [07 — Dependencias de secciones](07-dependencias-secciones.md)
- [04 — Documentación de desarrollador](04-documentacion-desarrollador.md) — API y servicios
- [05 — APIs externas](05-apis-externas.md) — dependencias de red
- [03 — Casos de uso](03-casos-de-uso.md) — CU-03, CU-04, CU-11, CU-13
