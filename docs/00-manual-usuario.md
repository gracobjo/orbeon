# Manual de usuario — Orbeon Form Editor

Guía práctica para analistas funcionales y desarrolladores Orbeon que editan plantillas XML exportadas desde Form Builder / Form Runner.

**Versión de la aplicación:** 1.0.0-SNAPSHOT  
**URL local:** [http://localhost:8080](http://localhost:8080)

---

## 1. ¿Para qué sirve esta aplicación?

Orbeon Form Editor permite **trabajar con plantillas XML de Orbeon sin abrir Form Builder** para tareas habituales de mantenimiento:

| Objetivo | Qué hace la app |
|----------|-----------------|
| Revisar un formulario | Lista de campos, secciones, vista diseño aproximada |
| Aplicar cambios de negocio | Editar labels, hints, desplegables, calculadoras, visibilidad |
| Seguir instrucciones de un PDF | Traducir anotaciones del margen a cambios XML (formulario 684/480) |
| Comparar versiones | Detectar altas, bajas y cambios entre PRE y v39, etc. |
| Exportar resultado | Descargar XML listo para importar en Orbeon |

> **Importante:** no sustituye a Orbeon Form Runner en ejecución. No valida XForms en tiempo real ni ejecuta servicios JCYL (provincia/municipio).

---

## 2. Arranque

1. Ejecute `arrancar.cmd` (Windows) o `mvn spring-boot:run`.
2. Abra el navegador en **http://localhost:8080**.
3. Compruebe el mensaje de estado en la esquina derecha del header: *«Sin archivo cargado»* hasta que cargue un XML.

Para despliegue en otro equipo con solo JAR, ver [06 — HOWTO arranque](06-howto-arranque-y-crud.md).

---

## 3. Pantalla principal

La interfaz se divide en **dos paneles**:

```
┌─────────────────────────────────────────────────────────────────┐
│  BARRA SUPERIOR: cargar, PDF, comparar, exportar, estado        │
├──────────────────────────┬──────────────────────────────────────┤
│  PANEL IZQUIERDO (50%)   │  PANEL DERECHO (50%)                 │
│  Pestañas de edición     │  Vista diseño (simulación HTML)      │
│  + CRUD contextual       │                                      │
└──────────────────────────┴──────────────────────────────────────┘
```

---

## 4. Barra superior — botones y controles

Todos los botones de acción usan el **mismo azul** (marca Orbeon).

| Control | Qué hace | Cuándo usarlo |
|---------|----------|---------------|
| **Cargar XML base** | Abre selector de archivo `.xml` o `.txt` y parsea la plantilla | Siempre al iniciar una sesión |
| **PDF instrucciones** (bloque) | Zona para analizar un PDF con anotaciones de margen | Al aplicar el documento de instrucciones del formulario 684/480 |
| ↳ **Elegir PDF** | Selecciona el PDF de instrucciones | Tras cargar el XML base |
| ↳ **Aplicar al XML** | Si está marcado, ejecuta cambios automáticos al pulsar Analizar | Cuando confíe en las propuestas **Auto** |
| ↳ **Analizar** | Envía PDF + XML al servidor y abre el modal de propuestas | Tras elegir el PDF |
| **Comparar con otro XML** | Compara el XML cargado con otro archivo | Tras cargar XML base; se activa al cargar |
| **Comparar 2 archivos…** | Abre diálogo para elegir base y nuevo sin sesión previa | Comparación puntual PRE vs v39 |
| **Exportar XML de Salida** | Descarga el XML actual (con todos los cambios aplicados) | Al terminar la edición |
| **Estado** (derecha) | Muestra nombre del archivo y nº de componentes | Referencia rápida de la sesión |

### Barra resumen PDF (tras analizar)

Si analizó un PDF, aparece una franja ámbar bajo el header:

- **Ver detalle** — reabre el modal con todas las propuestas.
- **×** — cierra la barra (el análisis sigue en memoria hasta recargar).

---

## 5. Pestañas del panel izquierdo

### Lista

- Tabla plana de **todos los controles** (`id`, tipo, label, hint).
- **Buscar** por id, label o tipo.
- Clic en una fila → abre **CRUD contextual** y localiza el campo en el XML.

### Asistente

- Escriba instrucciones en **español** (patrones predefinidos, no IA libre).
- Ejemplos: *«¿Cuántos logos tiene?»*, *«Listar desplegables»*, *«Cambiar label de…»*.
- Checkbox **Aplicar cambios**: desmárquelo para solo consultar.
- Los resultados (logos, desplegables) son **clicables** → CRUD.

### Secciones

- Árbol jerárquico por `fr:section`.
- Búsqueda y clic para editar sección o campos hijos.

### Dependencias

- Inventario de `xf:bind @relevant` (visibilidad condicional).
- Filtros por tipo (visible fija, solo PDF, etc.).
- CRUD de expresiones `relevant`. Ver [07 — Dependencias](07-dependencias-secciones.md).

### Calculadoras

- Inventario de `xf:bind @calculate`.
- Glosario de fuentes (`$campo`, APIs externas).
- Edición de expresiones. Ver [09 — Calculadoras](09-calculadoras-xforms.md).

### Controles N

- Detecta campos genéricos `control-1`, `control-2`, etc.
- Permite **renombrarlos** de forma coherente en instancia, bind, control y resources.
- Ver [10 — Controles genéricos](10-controles-genericos-y-busqueda-xml.md).

### Modificar JSON

- Editor de `changes[]` tipados (motor de modificaciones).
- **Plantilla** — ejemplo de JSON.
- **Aplicar cambios** — ejecuta sobre el XML en memoria.
- Aquí puede pegar o editar modificaciones (antes había carga JSON en el header).

### Cambios

- **Changelog** de la sesión: cada operación aplicada con ✓ o ✗.

### Código XML

- Editor de texto del XML completo.
- **Buscador** con patrones Orbeon (control, bind, instancia, texto libre).
- Resaltado y navegación ◀ ▶ (Ctrl+F, Enter, Shift+Enter).
- **Actualizar Vista de Diseño desde el Código** — reparsa el XML editado manualmente.

---

## 6. Panel CRUD contextual

Aparece al pulsar un campo, logo, sección, dependencia o calculadora.

| Botón | Acción |
|-------|--------|
| **Ver en XML** | Salta a la pestaña Código XML y resalta el fragmento |
| **Previsualizar** | Muestra cambios en Vista diseño **sin guardar** en el XML |
| **Aplicar al XML** | Confirma cambios y actualiza modelo + changelog |
| **Descartar vista previa** | Vuelve al estado anterior al preview |
| **← Volver** / **Esc** | Regresa a la lista de donde vino |

Los IDs (Bind, Control, etc.) son **clicables** para buscar en el XML.

Guía ampliada: [08 — Editor CRUD contextual](08-editor-crud-contextual-y-preview.md).

---

## 7. Panel derecho — Vista diseño

Simulación HTML del formulario según el XML activo:

- Campos de texto, desplegables (estáticos abribles), imágenes, explicaciones.
- Desplegables **dinámicos** (JCYL) muestran badge «Opciones dinámicas» — no cargan datos reales.
- Banner ámbar si hay **vista previa sin guardar** (desde CRUD).

---

## 8. Análisis de PDF de instrucciones

Flujo recomendado para el formulario **684/480** (v39 → PRE):

1. Cargar XML base (p. ej. v39).
2. Elegir PDF de instrucciones.
3. **Sin marcar** «Aplicar al XML» → revisar propuestas en el modal.
4. Filtrar por **Automáticos** / **Revisión manual**.
5. Leer etiquetas:

| Etiqueta | Significado |
|----------|-------------|
| **Auto** | Se puede aplicar sin revisión (campo en catálogo) |
| **Revisar** | Comprobar manualmente (p. ej. altas de controles) |
| **alta** | Confianza alta — correspondencia directa PDF ↔ campo |
| **media** | Confianza media — verificar campo afectado |

6. Si está conforme, marcar **Aplicar al XML** y volver a **Analizar**, o aplicar cambios restantes a mano.
7. **Comparar** con XML objetivo (PRE) para validar.
8. **Exportar XML de Salida**.

Detalle técnico: [11 — Análisis PDF instrucciones](11-analisis-pdf-instrucciones.md).

---

## 9. Comparador de versiones

### Comparar con otro XML

- Requiere XML base ya cargado.
- Elige el segundo archivo → modal con resumen (añadidos, eliminados, modificados).
- Filtros por tipo de diferencia.
- Aviso especial si hay etiquetas `control-N`.

### Comparar 2 archivos…

- No requiere sesión previa.
- Elige XML origen y XML nuevo en el diálogo.

---

## 10. Flujos de trabajo habituales

### A. Corregir labels y hints

1. Cargar XML → pestaña **Lista** o **Asistente**.
2. Editar → **Previsualizar** → **Aplicar al XML**.
3. Exportar.

### B. Aplicar instrucciones PDF (684)

1. Cargar v39 → analizar PDF (sin aplicar).
2. Revisar propuestas **media** y **Revisar**.
3. Aplicar automáticos → comparar con PRE → ajustar manualmente en Código XML o CRUD.
4. Exportar.

### C. Renombrar controles genéricos

1. Cargar XML → **Controles N**.
2. Renombrar cada `control-N` → comprobar en **Comparar**.
3. Exportar.

### D. Auditar visibilidad y cálculos

1. Cargar XML → **Dependencias** + **Calculadoras**.
2. Filtrar, editar expresiones, exportar.

---

## 11. Lo que la aplicación **ya consigue**

- Parseo completo de plantillas Orbeon (~300+ controles en 684).
- Edición guiada y por JSON de labels, hints, textos, imágenes, desplegables.
- Asistente en español para consultas y cambios frecuentes.
- CRUD contextual con preview antes de guardar.
- Comparación estructural entre versiones.
- Análisis de PDF de instrucciones con catálogo validado 684/480.
- Detección y renombrado de `control-N`.
- Búsqueda avanzada en XML.
- API REST para integración y automatización.
- Tests automatizados de regresión (desplegables, intérprete PDF, etc.).

---

## 12. Lo que **no** hace (limitaciones)

| Limitación | Impacto práctico |
|------------|------------------|
| Sin motor XForms real | No valida reglas `constraint`/`relevant` al rellenar |
| Sin Orbeon Server | No publica ni prueba el formulario en entorno real |
| Desplegables JCYL | Solo muestra URL del servicio; no carga provincias/municipios |
| Asistente por reglas | Frases muy libres pueden no entenderse |
| Sin persistencia | Al recargar el navegador se pierde el trabajo no exportado |
| PDF mock (API) | Generación PDF existe en API; **no hay pestaña Vista PDF en la UI actual** |
| Catálogo PDF | Solo formulario 684/480 mapeado; otros requieren nuevo catálogo |
| Altas de controles desde PDF | Propuestas **Revisar**: copiar estructura del XML objetivo a mano |

Lista completa: [01 — Requisitos funcionales §7](01-requisitos-funcionales.md).

---

## 13. Consejos para ser más eficiente

1. **Siempre exporte** al terminar; no hay autoguardado.
2. Use **Previsualizar** antes de **Aplicar al XML** en cambios delicados.
3. Para instrucciones PDF: primero **analizar sin aplicar**, luego aplicar.
4. Use **Comparar con PRE** como prueba de aceptación.
5. Cambios masivos: pestaña **Modificar JSON** o API REST.
6. Edición manual XML: **Sincronizar** tras editar Código XML.
7. Consulte el **changelog** antes de exportar para ver qué se aplicó.

Roadmap de mejoras planificadas: [12 — Roadmap y mejoras](12-roadmap-mejoras.md).

---

## 14. Documentación relacionada

| Documento | Contenido |
|-----------|-----------|
| [06 — HOWTO arranque y CRUD](06-howto-arranque-y-crud.md) | Arranque y edición básica |
| [11 — PDF instrucciones](11-analisis-pdf-instrucciones.md) | API y catálogo PDF |
| [04 — Desarrollador](04-documentacion-desarrollador.md) | API REST completa |
| [12 — Roadmap y mejoras](12-roadmap-mejoras.md) | Pendiente e ideas de eficiencia |
