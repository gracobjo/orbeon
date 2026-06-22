# Calculadoras XForms (`xf:bind @calculate`)

**Proyecto:** Orbeon Form Editor  
**Plantilla de referencia:** `684_F1b_MIXTO_480_Solicitud_v39.txt` (formulario 684 / 480, versión 39)  
**Última revisión:** junio 2026

---

## 1. Qué es una «calculadora» en Orbeon

En Orbeon Form Runner / XForms no existe un elemento llamado «calculadora». El término se refiere a los **`xf:bind` con atributo `calculate`**: nodos cuyo valor se **recalcula automáticamente** mediante una expresión XPath cuando cambian otros campos del formulario.

```xml
<xf:bind id="documentoIdent-tipodoc-bind" ref="documentoIdent-tipodoc"
         calculate="for $id in $documentoIdent-nifSol return (
           if (matches($id,'^\d{8}[TRWAGMYFPDXBNJZSQVHLCKE]$')) then 'dni'
           else if (matches($id,'^[XYZ]\d{7}[TRWAGMYFPDXBNJZSQVHLCKE]$')) then 'nie'
           else if (matches($id,'^([ABEH]\d{8})|...')) then 'cif'
           else ''
         )"/>
```

**Dónde viven:** dentro del árbol `fr-form-binds` → `xf:bind id="fr-form-binds"`, junto a `relevant`, `constraint`, `type`, etc.

**Relación con `relevant`:**

| Atributo | Efecto |
|----------|--------|
| `relevant` | Controla si el bind (y su control) es **visible/activo** |
| `calculate` | Controla el **valor** del nodo de instancia referenciado por `ref` |

Ver también [07 — Dependencias de secciones](07-dependencias-secciones.md) para `relevant`.

---

## 2. Inventario en la plantilla de ejemplo

Análisis sobre las plantillas incluidas en el repositorio:

| Archivo | Binds con `calculate` |
|---------|----------------------|
| `684_F1b_MIXTO_480_Solicitud_v39.txt` | **141** |
| `684_F1b_MIXTO_480_Solicitud_PRE.txt` | **139** |

Listado completo (id, ref, tipo, expresión resumida):  
**[datos/calculadoras-inventario-v39.csv](datos/calculadoras-inventario-v39.csv)**

### Clasificación por tipo (v39)

| Tipo | Cantidad | Descripción |
|------|----------|-------------|
| Vaciar según autónomo | 29 | Limpia persona física o autónomo según `$documentoIdent-autonomo` |
| Vaciar según condición | 18 | Representante, notificación papel/electrónica, firma, SPJ, etc. |
| Vaciar si no vinculadas | 11 | Repetición de empresas vinculadas si el checkbox está desmarcado |
| Contador por provincia | 9 | `prov05`…`prov49`: cuenta centros de trabajo por provincia CL |
| Normalizar NIF/IBAN | 8 | Mayúsculas, elimina caracteres no alfanuméricos, rellena ceros DNI |
| Inferir tipo documento | 4 | Deduce `dni` / `nie` / `cif` del NIF por regex |
| API externa CNAE/IAE | 3 | `doc(concat('https://www.ae.jcyl.es/cnaeiae/...'))` |
| Centro directivo / provincia | 3 | Código y descripción según `$provincializador` |
| Tamaño de empresa | 2 | `tipoEmpresa` según número de trabajadores |
| Otros | 50 | Lógica específica (ayudas, certificaciones, anexos SUFO, etc.) |

---

## 3. Grupos funcionales destacados

### 3.1 Provincializador y centro gestor

| Bind | Expresión (resumen) |
|------|---------------------|
| `provincializador-bind` | `$empresa-provincia` |
| `centroDirectivo.codigo-bind` | Código de centro según código provincia (`05`→`00015214`, …) |
| `centroDirectivo.descripcion-bind` | Nombre de gerencia provincial según provincia |
| `centroGestor-bind` | Etiqueta del centro gestor según `$provincializador` |

### 3.2 Contadores de centros de trabajo

Bloque `contadores-bind` (sección oculta, `relevant="false()"`):

1. `prov05-bind` … `prov49-bind` — `count($centroTrabajo-provinciaCL[string()='XX'])`
2. `maxProvincia-bind` — máximo de los nueve contadores
3. `posMax-bind` — código de provincia con más centros

### 3.3 Persona física / autónomo / empresa

Patrón repetido en decenas de binds:

```xpath
if ($documentoIdent-autonomo/string() !='false') then ('') else .
```

- Campos de **persona física** se vacían si el solicitante es autónomo o empresa.
- Campos de **autónomo** se vacían si no es autónomo (`!='true'`).
- Campos de **empresa** se vacían si el tipo de documento no es CIF.

### 3.4 Normalización e inferencia de documentos

| Bind | Función |
|------|---------|
| `documentoIdent-nifSol-bind` | Normaliza NIF (mayúsculas, ceros a la izquierda en DNI) |
| `documentoIdent-tipodoc-bind` | Infiere dni/nie/cif |
| `representante-nif-bind`, `notificacion-nif-bind`, … | Misma lógica en otros bloques |
| `datosBancarios-iban-bind` | `replace(upper-case(.),'[^0-9A-Z]','')` |

### 3.5 APIs externas JCYL (runtime Orbeon)

Estas calculadoras usan `doc()` contra servicios HTTP. **El editor no las ejecuta**; solo las conserva en el XML.

| Bind | URL (v39) |
|------|-----------|
| `autonomo-dCnae-bind` | `https://www.ae.jcyl.es/cnaeiae/get/cnaeid/{cnae}` |
| `empresa-dCnae-bind` | Igual para empresa |
| `autonomo-descripcionIae-bind` | `https://www.ae.jcyl.es/cnaeiae/get/nseciaeid/{iae}/{seccion}` |

En **PRE** las URLs de CNAE apuntan a `wwwpre.ae.jcyl.es/cnaeiae2/api/cnaes2025/`.

### 3.6 Anexos SUFO (solo v39)

| Bind | Expresión |
|------|-----------|
| `anexos-idRepresentante-bind` | `if($permisosConsultaSufo-sufoid1/string()='true') then 'false' else .` |
| `anexos-certSegSocial-bind` | `if($permisosConsultaSufo-sufoid26/string()='true') then 'false' else .` |

---

## 4. Diferencias PRE vs v39

| Aspecto | PRE | v39 |
|---------|-----|-----|
| `centroDirectivo.codigo/descripcion` | XPath con `index-of` y arrays de provincias | Cadena de `if ($provincializador = '05') then …` |
| `provincializador-bind` | `/form/destinatario/centroGestor` | `$empresa-provincia` |
| APIs CNAE | Entorno pre (`wwwpre.ae.jcyl.es`) | Producción (`www.ae.jcyl.es`) |
| Anexos SUFO | No presentes | 2 calculadoras nuevas |
| Total calculadoras | 139 | 141 |

---

## 5. Cómo editar calculadoras en el editor

### Pestaña Calculadoras (recomendado)

1. Cargar plantilla XML.
2. Pestaña **Calculadoras**.
3. Cada tarjeta muestra:
   - **Obtiene datos de:** variables `$campo`, rutas `/form/...` o URLs de API (`doc()`).
   - Expresión `calculate` completa.
4. **Clic en la tarjeta** → editor CRUD contextual + XML localizado.
5. **Editar aquí** → modificar XPath inline → **Guardar en XML**.
6. **Quitar calculate** → elimina el atributo del bind.
7. **Exportar XML de Salida** para obtener el fichero modificado.

### Opción A — Código XML

1. Cargar plantilla.
2. Pestaña **Código XML**.
3. Buscar el `bindId` (p. ej. `documentoIdent-tipodoc-bind`).
4. Editar el atributo `calculate="..."`.
5. **Sincronizar** o exportar.

### Opción B — Modificar JSON (`update-calculator`)

```json
{
  "changes": [
    {
      "type": "update-calculator",
      "bindId": "datosEcono-tipoEmpresa-bind",
      "calculate": "if ($datosEcono-numTrabajadores<=9) then '3' else '2'"
    }
  ]
}
```

Eliminar calculadora:

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

### Opción C — Modificar JSON (`update-bind`)

```json
{
  "changes": [
    {
      "type": "update-bind",
      "bindId": "datosEcono-tipoEmpresa-bind",
      "attributes": {
        "calculate": "if ($datosEcono-numTrabajadores>=0 and $datosEcono-numTrabajadores<=9) then '3' else if ($datosEcono-numTrabajadores<=49) then '2' else '1'"
      }
    }
  ]
}
```

Consulta el esquema: `GET /api/formulario/esquema-modificaciones`.

### Limitaciones del editor

| Limitación | Detalle |
|------------|---------|
| Sin ejecución XPath | El editor no recalcula valores en tiempo real; solo modifica el XML estático |
| API `doc()` | Las URLs se muestran como fuente; el editor no las invoca |

---

## 6. Cómo localizar una calculadora

| Si conoces… | Busca en XML… |
|-------------|---------------|
| Id del control `personaFisica-nombre-control` | `personaFisica-nombre-bind` |
| Nombre de instancia `documentoIdent-tipodoc` | `id="documentoIdent-tipodoc-bind"` |
| Campo en CSV | Columna `Id` del inventario |

Búsqueda rápida en el repositorio:

```bash
# PowerShell
Select-String -Path 684_F1b_MIXTO_480_Solicitud_v39.txt -Pattern 'calculate=' | Measure-Object
Select-String -Path 684_F1b_MIXTO_480_Solicitud_v39.txt -Pattern 'id="mi-campo-bind"' -Context 0,5
```

---

## 8. API y arquitectura en el editor

| Elemento | Detalle |
|----------|---------|
| Servicio | `OrbeonCalculatorService` — detecta binds, clasifica tipo, extrae fuentes |
| Modelos | `CalculadoraFormulario`, `AnalisisCalculadoras` |
| Carga | `POST /cargar` y `/sincronizar-codigo` incluyen `calculadoras` en `FormularioResponse` |
| Análisis bajo demanda | `POST /api/formulario/analizar-calculadoras` con `{ "xml": "..." }` |
| Modificación | `update-calculator` en `OrbeonModificationService` (`calculate` o `removeCalculate: true`) |
| UI | Pestaña **Calculadoras** en `index.html`; fallback a `/analizar-calculadoras` si la respuesta de carga no trae el campo (servidor antiguo) |
| Tests | `OrbeonCalculatorServiceTest` — 141 calculadoras en v39, update/remove `calculate` |

Desarrollador: [04 — Documentación de desarrollador](04-documentacion-desarrollador.md) §4.6.

---

## 7. Referencias

- [06 — HOWTO arranque y CRUD](06-howto-arranque-y-crud.md) — `update-bind`
- [07 — Dependencias de secciones](07-dependencias-secciones.md) — `relevant` vs `calculate`
- [05 — APIs externas](05-apis-externas.md) — servicios JCYL referenciados en `doc()`
- [Inventario CSV v39](datos/calculadoras-inventario-v39.csv)
