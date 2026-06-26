# Controles genéricos `control-N` y búsqueda en Código XML

Guía de la detección, renombrado y navegación de campos Orbeon con nombres genéricos (`control-1`, `control-2`, …) y del buscador integrado en la pestaña **Código XML**.

---

## 1. Problema que resuelve

En plantillas exportadas desde Form Builder a veces aparecen campos con nombres poco descriptivos:

| Pieza | Ejemplo |
|-------|---------|
| Instancia | `<control-1/>`, `<control-1><text>…</text></control-1>` |
| Bind | `id="control-1-bind"` `ref="control-1"` |
| Control vista | `id="control-1-control"` |
| Resources | `$form-resources/control-1/text` |

El editor los detecta automáticamente, los lista en la pestaña **Controles N** y permite **renombrarlos** de forma coherente en todo el XML.

---

## 2. Pestaña «Controles N»

### Cuándo aparece

Tras **cargar** o **sincronizar** un XML, si se detecta al menos un `control-N`:

- Badge ámbar en la pestaña **Controles N** con el recuento.
- Aviso en la pestaña **Código XML** con enlace **Ver lista y renombrar**.

### Lista

Cada fila muestra:

- Nombre de instancia (`control-1`)
- `bind` y `control` asociados
- Tipo de control en vista (`fr:explanation`, `xf:input`, …) si el DOM parsea
- Número aproximado de referencias en el XML

Filtro superior: busca por nombre, bind o control (no es el buscador del editor XML).

### Renombrar (CRUD)

1. Clic en una fila → panel **CRUD** inferior.
2. Campos de solo lectura: nombre actual, Bind ID, Control ID (clicables → saltan al XML).
3. **Nuevo nombre**: identificador descriptivo (ej. `avisoColectivo`).
4. **Aplicar al XML** → cambio `rename-control-numeric`.

**Qué actualiza el renombrado:**

- Etiquetas de instancia y `fr-form-resources`
- `id` / `ref` / `name` del bind
- `id` / `bind` del control
- Referencias `$form-resources/{nombre}/…`

**Restricciones:** el nuevo nombre no puede seguir el patrón `control-N` ni contener caracteres inválidos para un campo Orbeon.

---

## 3. Búsqueda en Código XML

Barra superior de la pestaña **Código XML**:

| Elemento | Función |
|----------|---------|
| Campo de búsqueda | Texto a localizar (ej. `control-1-control`) |
| Patrón | Modo de búsqueda Orbeon |
| ◀ / ▶ | Anterior / siguiente coincidencia |
| Contador | `n/total` |

**Atajos:** `Ctrl+F` enfoca el buscador · `Enter` siguiente · `Shift+Enter` anterior · `Esc` limpia.

### Patrones de búsqueda

| Patrón | Uso |
|--------|-----|
| **Auto (Orbeon)** | Si el texto termina en `-control`/`-bind`, busca por id; si no, expande al campo completo |
| **Texto libre** | Subcadena literal en todo el XML |
| **Campo completo** | Instancia + bind + control + resources |
| **ID control** | `id="…-control"` |
| **ID bind** | `id` y `bind` del bind |
| **Instancia** | Tags `<nombre>` y `ref`/`name` |

### Resaltado visual

Las coincidencias se iluminan en una capa bajo el textarea:

- **Amarillo:** todas las coincidencias
- **Naranja:** coincidencia activa

El editor sigue siendo editable; el scroll del resaltado se sincroniza con el textarea.

### Navegación desde CRUD

En cualquier editor CRUD, los campos **Bind ID**, **Control**, **Field ID**, etc. son clicables: cada clic salta a la siguiente ocurrencia en el XML.

---

## 4. Comparador de XML

Al comparar dos plantillas, si alguna contiene etiquetas `control-N`, el modal muestra un panel **Etiquetas genéricas control-N detectadas** con:

- Listado en XML base y nuevo
- Etiquetas añadidas o eliminadas entre versiones
- Aviso si ambas versiones conservan los mismos nombres genéricos

---

## 5. API y motor de cambios

### Respuesta de `/cargar` y `/sincronizar-codigo`

Campos adicionales en `FormularioResponse`:

```json
{
  "etiquetasControlNumerico": ["control-1"],
  "controlesGenericos": [
    {
      "nombre": "control-1",
      "bindId": "control-1-bind",
      "controlId": "control-1-control",
      "tipoControl": "explanation",
      "ocurrencias": 11
    }
  ]
}
```

### Comparación (`ComparacionResponse`)

- `etiquetasControlNumericoBase` / `etiquetasControlNumericoNuevo`
- `etiquetasControlNumericoAnadidas` / `etiquetasControlNumericoEliminadas`

### Cambio JSON

```json
{
  "type": "rename-control-numeric",
  "nombreActual": "control-1",
  "nombreNuevo": "avisoColectivo"
}
```

Implementación: `OrbeonXmlUtil.renombrarEtiquetaControlNumerico()` invocado desde `OrbeonModificationService`.

---

## 6. Detección (patrones)

El analizador recorre el XML en bruto con estas expresiones (insensible a mayúsculas):

1. Etiquetas: `<control-N>`, `<control-N/>`, `</control-N>`
2. IDs: `id="control-N-control"`, `id="control-N-bind"`
3. Referencias: `ref="control-N"`, `name="control-N"`
4. Resources: `$form-resources/control-N/`

Clase de dominio: `EtiquetaControlNumerico`. Tests: `OrbeonXmlUtilTest`.

---

## 7. Archivos de plantilla de prueba

Los XML de ejemplo (p. ej. `688_F1b_CTCON_2792_Solicitud_PRE_ini.txt`) se mantienen **en local** y están excluidos del repositorio Git (`.gitignore`: `*.txt`, `*.pdf`). Para probar, cargue el archivo desde **Cargar XML base** sin commitearlo.

---

## 8. Referencias en código

| Componente | Ubicación |
|------------|-----------|
| Detección y renombrado DOM | `OrbeonXmlUtil.java` |
| Tipo `rename-control-numeric` | `OrbeonModificationService.java` |
| Enriquecimiento en carga | `FormularioController.construirRespuesta()` |
| Comparador | `OrbeonCompareService.java` |
| UI lista + CRUD + buscador | `static/index.html` |

Documentación de desarrollador: [04-documentacion-desarrollador.md](04-documentacion-desarrollador.md).
