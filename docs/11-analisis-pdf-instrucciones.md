# Análisis de PDF de instrucciones

Traduce las **anotaciones del margen** de un PDF de instrucciones (p. ej. formulario 684/480) en **cambios XML** sobre la plantilla Orbeon cargada.

---

## 1. Flujo en la interfaz

El intérprete **no depende de un formulario concreto**: construye el catálogo de reglas a partir del **XML que tenga cargado** (declaraciones, anexos, secciones, textos). Funciona con cualquier plantilla Orbeon que siga la estructura habitual.

1. **Cargar XML base** — plantilla a modificar (p. ej. v39, 1793 PRCI, etc.).
2. En la barra superior, bloque **PDF instrucciones** (índigo):
   - **Elegir PDF** — fichero con anotaciones de margen.
   - **Aplicar al XML** (opcional) — si está marcado, ejecuta los cambios automáticos al analizar.
   - **Analizar** — envía PDF + XML al servidor.
3. Se abre un **modal** con tres pestañas:
   - **Propuestas** — lista filtrable (Todos / Automáticos / Revisión manual) con etiquetas Auto/Revisar y confianza.
   - **Estructura** — árbol por secciones del formulario con los campos afectados por cada instrucción; al final, anotaciones sin mapear.
   - **XML formulario** — plantilla actual (o modificada si aplicó cambios), con **Copiar** y **Abrir en Código XML**.
4. Tras cerrar el modal queda una **barra resumen** con «Ver detalle».
5. Si marcó **Aplicar al XML**, el editor se actualiza con el XML resultante y un log de cambios aplicados.
6. **Comparar con otro PDF…** (dentro del modal) — elige un segundo PDF de instrucciones sobre el mismo XML y muestra diferencias de anotaciones y campos afectados.

El **comparador XML** (Comparar con otro XML / Comparar 2 archivos…) está en la barra superior, junto al bloque PDF. Las modificaciones JSON siguen en la pestaña **Modificar JSON**.

---

## 2. Etiquetas de las propuestas

Cada propuesta muestra dos indicadores:

| Etiqueta | Significado |
|----------|-------------|
| **Auto** | Se puede aplicar al XML sin revisión: campo y cambio definidos en el catálogo del formulario. |
| **Revisar** | No se aplica automáticamente; requiere comprobación (p. ej. altas de controles nuevos copiando del XML objetivo PRE). |
| **alta** | **Confianza alta** — correspondencia directa entre la anotación del PDF y el campo XML (ID o texto del catálogo). |
| **media** | **Confianza media** — correspondencia inferida (texto parcial, proximidad en el PDF o regla genérica). Conviene verificar el campo afectado. |

Filtros del modal: **Todos**, **Automáticos**, **Revisión manual**.

---

## 3. API REST

### `POST /api/formulario/analizar-instrucciones-pdf`

**Content-Type:** `multipart/form-data`

| Campo | Tipo | Obligatorio | Descripción |
|-------|------|-------------|-------------|
| `pdf` | archivo | Sí | PDF de instrucciones con anotaciones |
| `xml` | texto | Sí | Plantilla Orbeon actual |
| `aplicar` | boolean | No (default `false`) | Si `true`, aplica al XML solo propuestas con `aplicableAutomaticamente: true` |

**Respuesta:** `AnalisisInstruccionesResponse`

```json
{
  "nombrePdf": "684 F1b Mixto - 480 Solicitud_Instrucciones.pdf",
  "totalPaginas": 6,
  "totalAnotaciones": 23,
  "resumen": "23 anotaciones · 15 propuestas (12 auto, 3 manual)",
  "anotaciones": [ { "pagina": 4, "contenido": "Eliminar", "subtipo": "StrikeOut" } ],
  "propuestas": [
    {
      "id": "eliminar-roac",
      "intencion": "eliminar-declaracion",
      "descripcion": "Eliminar declaración: Registro Oficial de Auditores de Cuentas",
      "confianza": "alta",
      "aplicableAutomaticamente": true,
      "camposAfectados": ["declaracionesResponsables-inscritoROAC-control"],
      "cambios": [{ "type": "remove-field", "fieldId": "declaracionesResponsables-inscritoROAC-control" }]
    }
  ],
  "cambiosAgregados": [],
  "xml": "...",
  "nombreFormulario": "684_F1b_MIXTO_480_Solicitud",
  "estructura": { "secciones": [] },
  "estructuraInstrucciones": {
    "formulario": "684_F1b_MIXTO_480_Solicitud",
    "totalSeccionesAfectadas": 4,
    "totalCamposAfectados": 12,
    "secciones": [
      {
        "id": "declaracionesResponsables-section",
        "titulo": "Declaraciones responsables",
        "campos": [
          {
            "fieldId": "declaracionesResponsables-inscritoROAC-control",
            "label": "Inscrito en el ROAC",
            "intencion": "eliminar-declaracion",
            "confianza": "alta",
            "aplicableAutomaticamente": true
          }
        ]
      }
    ],
    "anotacionesSinMapear": []
  },
  "logAplicados": []
}
```

Si `aplicar=true`, `xml` contiene la plantilla modificada y `logAplicados` lista las operaciones ejecutadas.

### `POST /api/formulario/comparar-instrucciones-pdf`

Compara dos PDFs de instrucciones sobre la **misma plantilla XML** (p. ej. v39 vs revisión posterior del documento de instrucciones).

**Content-Type:** `multipart/form-data`

| Campo | Tipo | Obligatorio | Descripción |
|-------|------|-------------|-------------|
| `pdfBase` | archivo | Sí | PDF de referencia (el de la barra superior) |
| `pdfNuevo` | archivo | Sí | Segundo PDF a contrastar |
| `xml` | texto | Sí | Plantilla Orbeon actual |

**Respuesta:** `ComparacionInstruccionesResponse`

```json
{
  "resumen": "Base: 23 anotaciones · Nuevo: 25 anotaciones · 2 solo en base, 4 solo en nuevo",
  "anotacionesSoloBase": [{ "pagina": 3, "contenido": "Eliminar" }],
  "anotacionesSoloNuevo": [],
  "camposSoloBase": ["campo-a-control"],
  "camposSoloNuevo": ["campo-b-control"],
  "camposComunes": ["campo-c-control"],
  "analisisBase": { },
  "analisisNuevo": { }
}
```

---

## 4. Arquitectura backend

```
PDF (multipart)
    │
    ▼
OrbeonPdfInstructionsService     ← extrae anotaciones (OpenPDF)
    │
    ▼
OrbeonInstructionsCatalogBuilder       ← catálogo dinámico desde XML (+ JSON estático opcional)
    │
    ▼
OrbeonInstructionsInterpreterService   ← reglas + matching anotaciones
    │
    ├── propuestas (PropuestaCambioXml)
    ├── OrbeonInstructionsStructureService → estructuraInstrucciones (secciones / campos)
    ├── OrbeonStructureService → estructura completa del formulario
    └── si aplicar: OrbeonModificationService (remove-field, update-resource, update-bind, …)
```

### Catálogo de mapeo

**Generación automática (por defecto):** al analizar, `OrbeonInstructionsCatalogBuilder` escanea el XML cargado y crea reglas para:

- Textos de **declaraciones** (`declaraciones*`, `declaracion*`)
- Textos de **anexos** (`anexos-*`)
- **Secciones** (`fr:section` + controles hijos)
- Otros recursos con `-texto` en el id

Cada regla solo se aplica si una **anotación del PDF** coincide con el fragmento de texto y pide eliminación, sustitución o inserción.

**Catálogo estático opcional:** `instrucciones-684-mapeo.json` se fusiona como complemento (sustituciones exactas, marcadores del 684/480).

Servicio: `OrbeonInstructionsCatalogBuilder` + `OrbeonInstructionsInterpreterService`.

### Tipos de cambio generados

| Tipo | Uso típico |
|------|------------|
| `remove-field` | Quitar control del `fr:view` y recursos asociados |
| `update-resource` | Actualizar `<text>` en `fr-form-resources` |
| `update-bind` | Modificar `xf:bind` (p. ej. quitar `@relevant`) |

---

## 5. Pruebas

```bash
mvn test -Dtest=OrbeonInstructionsInterpreterTest,OrbeonInstructionsCatalogBuilderTest
```

- `OrbeonInstructionsInterpreterTest` — no elimina declaraciones no contempladas en el catálogo.
- `OrbeonInstructionsCatalogBuilderTest` — genera reglas desde XML de ejemplo.

---

## 6. Limitaciones

- La calidad depende de que las anotaciones del PDF **coincidan en texto** con recursos del XML (confianza **media** si la coincidencia es parcial).
- Las propuestas **Revisar** (altas de controles) no se aplican solas: hay que copiar la estructura del XML objetivo manualmente.
- La extracción de anotaciones depende del formato del PDF (comentarios/marcas de revisión en el margen).
- No sustituye la revisión humana del comparador con la versión objetivo.

---

## 7. Documentación relacionada

- [00 — Manual de usuario](00-manual-usuario.md) — flujos, botones y consejos de eficiencia
- [12 — Roadmap y mejoras](12-roadmap-mejoras.md) — pendientes (catálogo, aplicar propuestas, Vista PDF, etc.)
