# Editor CRUD contextual y previsualización antes de guardar

Guía del flujo **clic en resultado → XML localizado → edición CRUD → previsualizar → aplicar o descartar**.

---

## Qué resuelve

Antes, consultar un logo o localizar un campo en el Asistente mostraba información, pero había que buscar manualmente el fragmento en el XML. Ahora, al pulsar casi cualquier resultado de la interfaz:

1. Se abre la pestaña **Código XML** con el fragmento **seleccionado y desplazado a la vista**.
2. Aparece el **panel CRUD** en la parte inferior del panel izquierdo con los campos editables del elemento.
3. Puedes pulsar **Previsualizar** para ver el formulario con los cambios **sin modificar el XML guardado**.
4. Solo al pulsar **Aplicar al XML** (o el botón equivalente del banner ámbar) se confirman los cambios de forma permanente.

---

## Flujo general

```
┌─────────────────────────────────────────────────────────────────┐
│  Usuario pulsa un resultado (logo, campo, sección, dependencia) │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Pestaña Código XML  +  panel #panelEditorCrud                  │
│  (anclas: id del control, bind, tag imagen, sectionId…)         │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
              ┌──────────────┴──────────────┐
              ▼                             ▼
     [ Previsualizar ]              [ Aplicar al XML ]
              │                             │
              ▼                             ▼
   Vista Diseño con borrador      XML + changelog actualizados
   (banner ámbar: sin guardar)    (cambio permanente)
              │
              ▼
     [ Descartar ] → restaura vista previa anterior
```

### Botones del panel CRUD

| Botón | Acción |
|-------|--------|
| **Ver en XML** | Vuelve a la pestaña Código XML y resalta el fragmento |
| **Previsualizar** | Aplica cambios en memoria vía `POST /modificar` + `POST /sincronizar-codigo`, actualiza Vista Diseño **sin** tocar `xmlActual` |
| **Aplicar al XML** | Persiste los cambios en `xmlActual`, textarea XML, changelog y todas las vistas |
| **Descartar vista previa** | Restaura `componentes`, `estructura` y `dependencias` desde el snapshot previo a la previsualización |
| **✕** (cerrar) | Oculta el panel CRUD |

El banner **«Vista previa con cambios sin guardar en el XML»** (panel derecho) ofrece atajos **Aplicar al XML** y **Descartar** con el mismo comportamiento.

---

## Orígenes clicables

| Origen | Qué se abre | Tipo de editor |
|--------|-------------|----------------|
| **Asistente** — tarjetas bajo la respuesta (logos) | Logo / imagen | `src`, `filename`, `mediatype` |
| **Asistente** — tarjetas de consulta de desplegables | Campo desplegable | `label`, `hint`, `alert` |
| **Asistente** — botón **XML** en lista de desplegables | Desplegable | Igual |
| **Lista** — tarjeta de componente | Campo, desplegable o imagen | Según tipo |
| **Secciones** — cabecera de sección | Sección / dependencia | Expresión `relevant` |
| **Secciones** — fila de campo | Campo o logo | Según tipo |
| **Secciones** — bloque «Imágenes en instancia» | Logo | `src`, `filename`, `mediatype` |
| **Dependencias** — tarjeta o **Editar aquí** | Sección / grid | Expresión `relevant` |

Las tarjetas del Asistente muestran el sufijo **→ editar** y un borde resaltado al pasar el ratón (clase `clickable-result`).

---

## Ejemplo: consulta de logos

1. Cargar un XML (p. ej. `684_F1b_MIXTO_480_Solicitud_v39.txt`).
2. Pestaña **Asistente** → pulsar **¿Cuántos logos tiene?** o escribir la pregunta.
3. En el panel violeta de resultado aparece una tarjeta, p. ej. `iapa-img → editar`.
4. **Clic en la tarjeta**:
   - Pestaña **Código XML** con selección en el nodo `iapa-img` / control `iapa-img-control`.
   - Panel CRUD con tag, control, sección (solo lectura) y campos editables `src`, `filename`, `mediatype`.
5. Cambiar `filename` → **Previsualizar** → comprobar en **Vista Diseño**.
6. Si es correcto → **Aplicar al XML**. Si no → **Descartar vista previa**.

Consultas relacionadas: `¿Dónde está el logo?`, `Listar logos`.

---

## Ejemplo: sección con visibilidad condicional

1. Pestaña **Dependencias** → buscar `vinculadas-section`.
2. **Clic en la tarjeta** (fuera de botones de edición inline) o **Editar aquí**.
3. Panel CRUD con `sectionId`, `bindId` (solo lectura) y textarea **Expresión relevant**.
4. Modificar XPath → **Previsualizar** (la estructura de secciones se recalcula en memoria) → **Aplicar al XML**.

Documentación de expresiones: [07 — Dependencias de secciones](07-dependencias-secciones.md).

---

## Tipos de cambio que genera el editor

El panel CRUD traduce los valores del formulario a entradas del array `changes[]` de la API:

| Tipo editor | `type` en JSON | Campos |
|-------------|----------------|--------|
| Logo | `update-image` | `imageTag`, `src`, `filename`, `mediatype` |
| Campo / desplegable | `update-label`, `update-hint`, `update-resource` | `fieldId`, textos |
| Sección / dependencia | `update-section-relevant` | `bindId`, `sectionId`, `relevant` o `removeRelevant: true` |

Endpoints implicados:

- `POST /api/formulario/modificar` — aplica `changes[]` sobre un XML de entrada.
- `POST /api/formulario/sincronizar-codigo` — reparsa el XML resultante en `componentes`, `estructura`, `dependencias`.

En **Previsualizar**, el frontend llama a ambos endpoints pero **no** sustituye `xmlActual` hasta **Aplicar al XML**.

---

## Detalles de implementación (frontend)

Archivo: `src/main/resources/static/index.html`

| Elemento / función | Rol |
|--------------------|-----|
| `#panelEditorCrud` | Panel inferior con formulario y botones |
| `#bannerPreviewBorrador` | Aviso de borrador en Vista Diseño |
| `abrirEditorCrud(tipo, meta)` | Abre panel y localiza XML |
| `irACodigoXml(terminos)` | Cambia a pestaña Código XML y selecciona texto |
| `previsualizarEditorCrud()` | Borrador sin persistir `xmlActual` |
| `aplicarEditorCrud()` | Persiste cambios |
| `descartarPreviewBorrador()` | Restaura snapshot |
| `navegarDesdeLogo`, `navegarDesdeComponente`, `navegarDesdeDependencia` | Entrada desde cada origen |

Variables de estado:

- `editorCrudActivo` — tipo y metadatos del elemento en edición.
- `previewBorradorActivo` — indica si hay vista previa sin guardar.
- `snapshotAntesPreview` — copia de `componentes`, `estructura`, `dependencias` antes del primer preview.

---

## Limitaciones

- **Previsualizar** requiere que el backend acepte los cambios (`/modificar`); si la expresión `relevant` es inválida en Orbeon, el preview puede fallar con el mensaje de error del servidor.
- Los desplegables **dinámicos** (JCYL) se abren en el editor de label/hint, pero el CRUD de **opciones** sigue en el panel expandible del Asistente (no en el panel contextual).
- La edición en pestaña **Lista** (sin panel CRUD) sigue siendo en memoria hasta **Exportar XML**; son flujos complementarios.
- Hasta **Aplicar al XML**, el textarea **Código XML** no refleja el borrador de previsualización.

---

## Referencias

- [06 — HOWTO arranque y CRUD](06-howto-arranque-y-crud.md)
- [07 — Dependencias de secciones](07-dependencias-secciones.md)
- [04 — Documentación de desarrollador](04-documentacion-desarrollador.md) — API y frontend
- [03 — Casos de uso](03-casos-de-uso.md) — CU-13
