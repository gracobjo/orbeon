# Análisis de PDF de instrucciones

Traduce las **anotaciones del margen** de un PDF de instrucciones (p. ej. formulario 684/480) en **cambios XML** sobre la plantilla Orbeon cargada.

---

## 1. Flujo en la interfaz

1. **Cargar XML base** — plantilla a modificar (p. ej. v39).
2. En la barra superior, bloque **PDF instrucciones** (índigo):
   - **Elegir PDF** — fichero con anotaciones de margen.
   - **Aplicar al XML** (opcional) — si está marcado, ejecuta los cambios automáticos al analizar.
   - **Analizar** — envía PDF + XML al servidor.
3. Se abre un **modal** con las propuestas. Tras cerrarlo queda una **barra resumen** con «Ver detalle».
4. Si marcó **Aplicar al XML**, el editor se actualiza con el XML resultante y un log de cambios aplicados.

El **comparador** (Comparar con otro XML / Comparar 2 archivos…) está en la barra superior, junto al bloque PDF. Las modificaciones JSON siguen en la pestaña **Modificar JSON**.

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
  "logAplicados": []
}
```

Si `aplicar=true`, `xml` contiene la plantilla modificada y `logAplicados` lista las operaciones ejecutadas.

---

## 4. Arquitectura backend

```
PDF (multipart)
    │
    ▼
OrbeonPdfInstructionsService     ← extrae anotaciones (OpenPDF)
    │
    ▼
OrbeonInstructionsInterpreterService   ← reglas + catálogo JSON
    │
    ├── propuestas (PropuestaCambioXml)
    └── si aplicar: OrbeonModificationService (remove-field, update-resource, update-bind, …)
```

### Catálogo de mapeo

`src/main/resources/datos/instrucciones-684-mapeo.json`

Define reglas tipadas para el formulario **684_F1b_MIXTO_480_Solicitud** (v39 → PRE):

- `reglasApartado` — eliminar secciones completas y binds asociados
- `reglasDeclaracion` — eliminar declaraciones responsables por fragmento de texto
- `reglasAnexo` — sustituir textos, eliminar documentos, marcar altas pendientes
- `reglasTexto` — insertar párrafos en resources o eliminar campos

Solo se aplican reglas del catálogo con **campos concretos**; no se infiere una eliminación masiva por cada anotación genérica «eliminar declaración».

### Tipos de cambio generados

| Tipo | Uso típico |
|------|------------|
| `remove-field` | Quitar control del `fr:view` y recursos asociados |
| `update-resource` | Actualizar `<text>` en `fr-form-resources` |
| `update-bind` | Modificar `xf:bind` (p. ej. quitar `@relevant`) |

---

## 5. Pruebas

```bash
mvn test -Dtest=OrbeonInstructionsInterpreterTest
```

Verifica que el intérprete no elimina declaraciones no contempladas en el catálogo (p. ej. `declaracionesResponsables-noDeudaImpagada-control` se conserva).

---

## 6. Limitaciones

- Catálogo validado para el **684/480**; otros formularios requieren un JSON de mapeo propio.
- Las propuestas **Revisar** (altas de controles) no se aplican solas: hay que copiar la estructura del XML objetivo manualmente.
- La extracción de anotaciones depende del formato del PDF (comentarios/marcas de revisión en el margen).
- No sustituye la revisión humana del comparador PRE vs v39.

---

## 7. Documentación relacionada

- [00 — Manual de usuario](00-manual-usuario.md) — flujos, botones y consejos de eficiencia
- [12 — Roadmap y mejoras](12-roadmap-mejoras.md) — pendientes (catálogo, aplicar propuestas, Vista PDF, etc.)
