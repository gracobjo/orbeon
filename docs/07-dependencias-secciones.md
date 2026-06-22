# Dependencias de visibilidad entre secciones

Cómo Orbeon activa u oculta secciones y cómo editarlo con **Orbeon Form Editor**.

---

## Concepto

En Orbeon Form Runner la visibilidad de una sección (`fr:section`) o grid (`fr:grid`) se controla con el atributo **`relevant`** del **`xf:bind`** asociado (`bind="miSeccion-bind"`).

| Valor `relevant` | Comportamiento en Form Runner |
|------------------|-------------------------------|
| *(sin atributo)* | Sección siempre visible |
| `true()` | Siempre visible |
| `false()` | Siempre oculta |
| Expresión XPath | Visible solo si la expresión es verdadera |

Ejemplo real del formulario 684:

```xml
<xf:bind id="vinculadas-bind" ref="vinculadas" name="vinculadas"
         relevant="(xxf:non-blank($documentoIdent-nifSol) and xxf:valid($documentoIdent-nifSol)) and $otrosDatosEmpresa-vinculadas/string() ='true'">
```

La sección **`vinculadas-section`** solo aparece cuando el NIF es válido **y** el checkbox **`otrosDatosEmpresa-vinculadas`** está marcado.

---

## En la aplicación web

1. Arrancar el servidor: `mvn spring-boot:run` → http://localhost:8080
2. Cargar el XML de la plantilla.
3. Abrir la pestaña **Dependencias** (panel izquierdo).

Verás:

- Resumen: secciones totales, condicionales y ocultas fijas.
- Tarjeta por sección/grid con tipo de visibilidad, expresión `relevant` y disparadores (`$campo`).
- **Glosario** de variables referenciadas.
- **Clic en la tarjeta** o **Editar aquí** → abre el [editor CRUD contextual](08-editor-crud-contextual-y-preview.md) con el XML localizado y expresión editable.
- Edición inline: **Editar** → textarea → **Guardar en XML** (aplica de inmediato).

### Atajos al editar

| Botón | Efecto en el XML |
|-------|------------------|
| Siempre visible (quitar) | Elimina el atributo `relevant` del bind |
| Oculta fija | `relevant="false()"` |
| Visible fija | `relevant="true()"` |

Los cambios se aplican al XML en memoria y aparecen en **Cambios** y **Código XML**. Exporte con **Exportar XML de Salida**.

---

## API REST

### Analizar dependencias

```http
POST /api/formulario/analizar-dependencias
Content-Type: application/json

{ "xml": "<xf:form>..." }
```

Respuesta (`AnalisisDependencias`):

```json
{
  "totalSecciones": 35,
  "totalCondicionales": 18,
  "totalOcultas": 8,
  "elementos": [
    {
      "id": "vinculadas-section",
      "bindId": "vinculadas-bind",
      "titulo": "vinculadas",
      "tipoElemento": "section",
      "expresionRelevant": "...",
      "tipoVisibilidad": "condicional",
      "dependeDe": ["documentoIdent-nifSol", "otrosDatosEmpresa-vinculadas"],
      "descripcionesDependencias": ["NIF del solicitante...", "Checkbox «Empresas vinculadas»"]
    }
  ],
  "glosarioDisparadores": { "documentoIdent-nifSol": "NIF del solicitante..." }
}
```

También se incluye en la respuesta de **`POST /cargar`** y **`POST /sincronizar-codigo`** (`dependencias`).

### Modificar visibilidad (CRUD)

Tipo de cambio **`update-section-relevant`** en `POST /api/formulario/modificar`:

```json
{
  "xml": "...",
  "changes": [
    {
      "type": "update-section-relevant",
      "sectionId": "vinculadas-section",
      "relevant": "true()"
    }
  ]
}
```

Quitar condición (siempre visible):

```json
{
  "type": "update-section-relevant",
  "bindId": "vinculadas-bind",
  "removeRelevant": true
}
```

Alternativa genérica (mismo efecto):

```json
{
  "type": "update-bind",
  "bindId": "vinculadas-bind",
  "attributes": { "relevant": "nueva expresión" }
}
```

---

## Tipos de visibilidad detectados

| Código | Significado |
|--------|-------------|
| `siempre_visible` | Sin `relevant` en el bind |
| `condicional` | Expresión con variables `$...` |
| `oculta_fija` | `relevant="false()"` |
| `visible_fija` | `relevant="true()"` |
| `solo_pdf` | Contiene `fr:mode()='pdf'` |

---

## Disparadores habituales (formulario 684)

| Variable | Uso |
|----------|-----|
| `$documentoIdent-nifSol` | Puerta común: NIF válido |
| `$documentoIdent-tipodoc` | dni / nie / cif |
| `$documentoIdent-autonomo` | Checkbox autónomo |
| `$otrosDatosEmpresa-vinculadas` | Empresas vinculadas → sección vinculadas |
| `$control-spj` | SPJ (CIF E/H) → agrupaciones |
| `$conRepresentante` | Campos de representante |
| `$notifica-opcion` | electrónica vs papel |
| `$declaracionesResponsables-*Opc` | Subsecciones de declaraciones |
| `$certifico-integracionLaboral-opcion` | Integración laboral / conceptoE4 |

---

## Casos de uso para el usuario final

| Necesidad | Acción en el editor |
|-----------|---------------------|
| Ocultar sección de empresas vinculadas | Clic en `vinculadas-section` → editar `relevant` → Previsualizar → Aplicar |
| Mostrar sección siempre | Quitar `relevant` o poner `true()` |
| Nueva regla: solo si marca checkbox X | `relevant="$miCheckbox/string()='true'"` |
| Cambiar de papel a electrónico por defecto | Editar binds de `notificacion-bind` / `notificaPostal-bind` |

---

## Limitaciones

- El editor **analiza y modifica** el XML; no ejecuta XPath en tiempo real como Orbeon Form Runner.
- La visibilidad de **campos individuales** (no sección completa) también usa `relevant` en binds hijos; la pestaña Dependencias lista secciones y grids con bind explícito.
- Secciones con `relevant="false()"` pero campos activos por `calculate` (p. ej. persona física / autónomo) requieren editar los binds de campo vía **Modificar JSON** (`update-bind`). Ver [09 — Calculadoras XForms](09-calculadoras-xforms.md).

---

## Referencias

- [09 — Calculadoras XForms](09-calculadoras-xforms.md)
- [08 — Editor CRUD contextual y preview](08-editor-crud-contextual-y-preview.md)
- [HOWTO arranque y CRUD](06-howto-arranque-y-crud.md)
- [Documentación desarrollador — API](04-documentacion-desarrollador.md)
- Esquema de tipos: `GET /api/formulario/esquema-modificaciones`
