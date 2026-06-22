# Requisitos funcionales

**Proyecto:** Orbeon Form Editor  
**Versión:** 1.0.0-SNAPSHOT  
**Alcance:** Lectura, visualización, edición y comparación de plantillas XML exportadas desde Orbeon Form Builder / Form Runner.

---

## 1. Gestión de plantillas XML

| ID | Requisito | Prioridad | Estado |
|----|-----------|-----------|--------|
| RF-001 | El sistema debe permitir cargar un archivo XML de Orbeon Form Runner (`.xml` o `.txt`) mediante interfaz web o API REST. | Alta | Implementado |
| RF-002 | Tras la carga, el sistema debe parsear el XML y extraer todos los controles visuales de `fr:view` con atributo `id`. | Alta | Implementado |
| RF-003 | El sistema debe resolver labels, hints y alerts desde la instancia `fr-form-resources`. | Alta | Implementado |
| RF-004 | El sistema debe mantener en memoria el XML completo para edición, sincronización y exportación. | Alta | Implementado |
| RF-005 | El usuario debe poder sincronizar manualmente el XML editado en la pestaña «Código XML» con el modelo interno. | Media | Implementado |
| RF-006 | El sistema debe exportar el XML modificado como archivo descargable. | Alta | Implementado |

---

## 2. Visualización de componentes

| ID | Requisito | Prioridad | Estado |
|----|-----------|-----------|--------|
| RF-010 | El sistema debe mostrar una lista plana de todos los componentes detectados con id, tipo, label y hint. | Alta | Implementado |
| RF-011 | El sistema debe mostrar la estructura jerárquica por secciones (`fr:section`) con sus campos anidados. | Alta | Implementado |
| RF-012 | El usuario debe poder buscar y filtrar componentes por id, label o tipo en lista y secciones. | Media | Implementado |
| RF-013 | El sistema debe renderizar una vista previa de diseño aproximada del formulario (campos, desplegables, imágenes, textos). | Alta | Implementado |
| RF-014 | Los desplegables (`select1`, `select`) deben mostrar opciones resueltas desde `xf:itemset ref="$form-resources/.../item"`. | Alta | Implementado |
| RF-015 | Los desplegables dinámicos (`fr:databound-select1`) deben identificarse y mostrarse como «opciones dinámicas» con referencia al servicio REST. | Alta | Implementado |
| RF-016 | Las imágenes y adjuntos deben mostrar metadatos (`ref`, ruta de instancia) en la vista diseño. | Media | Implementado |
| RF-017 | El sistema debe generar una vista previa PDF del formulario respetando nodos con `class="noprintinpdf"`. | Media | Implementado (mock) |

---

## 3. Edición de contenido

| ID | Requisito | Prioridad | Estado |
|----|-----------|-----------|--------|
| RF-020 | El usuario debe poder editar label y hint de un componente desde la lista guiada. | Alta | Implementado |
| RF-021 | Las modificaciones deben aplicarse al XML en `fr-form-resources`, no solo en la vista. | Alta | Implementado |
| RF-022 | El sistema debe soportar un motor de cambios tipados vía JSON (`changes[]`) con tipos: `update-label`, `update-hint`, `update-text`, `update-image`, `hide-section`, `show-section`, `update-resource`, `update-bind`, `update-calculator`, `remove-field`, `add-field`, `add-select-item`, `update-select-item`, `remove-select-item`, `add-image`, `update-section-relevant`. | Alta | Implementado |
| RF-023 | El usuario debe poder pegar o cargar un fichero JSON de modificaciones y aplicarlo. | Media | Implementado |
| RF-024 | El sistema debe registrar un changelog de cambios aplicados en la sesión de edición. | Media | Implementado |
| RF-025 | El endpoint `GET /api/formulario/esquema-modificaciones` debe documentar el formato JSON de cambios. | Baja | Implementado |
| RF-026 | La exportación debe aceptar modificaciones en formato `changes[]` o lista de `ComponenteFormulario`. | Media | Implementado |

---

## 3b. Asistente en lenguaje natural

| ID | Requisito | Prioridad | Estado |
|----|-----------|-----------|--------|
| RF-050 | El usuario debe poder escribir instrucciones en español natural para consultar o modificar el XML. | Alta | Implementado |
| RF-051 | El asistente debe detectar cuántos logos/imágenes hay, su posición (global y en sección), control, sección y rutas. | Alta | Implementado |
| RF-052 | El asistente debe permitir sustituir un logo por otra ruta (`update-image`) y añadir logos nuevos (`add-image`). | Alta | Implementado |
| RF-053 | El asistente debe listar desplegables y sus opciones por nombre de campo o label. | Alta | Implementado |
| RF-054 | El asistente debe hacer CRUD de opciones en desplegables estáticos (`fr-form-resources`). | Alta | Implementado |
| RF-055 | El asistente debe rechazar CRUD en desplegables dinámicos (JCYL) e informar del servicio REST. | Media | Implementado |
| RF-056 | El asistente debe poder cambiar labels y hints por instrucción natural. | Media | Implementado |
| RF-057 | Modo «solo consulta»: aplicar cambios desactivable desde la UI. | Baja | Implementado |
| RF-058 | `POST /api/formulario/lenguaje-natural` y `POST /api/formulario/analizar-logos` deben exponer la funcionalidad vía API. | Media | Implementado |

**Nota:** el asistente usa **parser de reglas locales**; no integra LLM ni APIs de IA externas (ver [05-apis-externas.md](05-apis-externas.md)).

---

## 3c. Editor CRUD contextual y previsualización

| ID | Requisito | Prioridad | Estado |
|----|-----------|-----------|--------|
| RF-060 | Al pulsar un resultado (logo, campo, sección, dependencia) la UI debe abrir el panel CRUD contextual y localizar el fragmento en Código XML. | Alta | Implementado |
| RF-061 | El panel CRUD debe permitir editar logos (`update-image`), labels/hints/alerts de campos, expresiones `relevant` de secciones y expresiones `calculate` de calculadoras. | Alta | Implementado |
| RF-062 | Debe existir acción **Previsualizar** que aplique cambios en Vista Diseño sin persistir `xmlActual`. | Alta | Implementado |
| RF-063 | Debe existir acción **Aplicar al XML** que confirme los cambios y actualice XML, componentes, estructura y changelog. | Alta | Implementado |
| RF-064 | Debe existir acción **Descartar vista previa** que restaure el estado anterior al preview. | Media | Implementado |
| RF-065 | Los resultados del Asistente (tarjetas de logos y desplegables) deben ser clicables y abrir el editor contextual. | Media | Implementado |

Guía de usuario: [08-editor-crud-contextual-y-preview.md](08-editor-crud-contextual-y-preview.md).

---

## 3d. Calculadoras XForms (`xf:bind @calculate`)

| ID | Requisito | Prioridad | Estado |
|----|-----------|-----------|--------|
| RF-070 | Al cargar o sincronizar XML, el sistema debe detectar todos los `xf:bind` con atributo `calculate` en cualquier plantilla Orbeon. | Alta | Implementado |
| RF-071 | Para cada calculadora, el sistema debe exponer bind id, ref, label, control asociado, expresión `calculate` y clasificación por tipo. | Alta | Implementado |
| RF-072 | El sistema debe identificar **fuentes de datos** referenciadas: variables `$campo`, rutas `/form/...`, valor del nodo (`.`) y URLs en `doc()`. | Alta | Implementado |
| RF-073 | La pestaña **Calculadoras** debe listar, filtrar y mostrar glosario de fuentes con edición inline y editor CRUD contextual. | Alta | Implementado |
| RF-074 | El usuario debe poder actualizar la expresión `calculate` o eliminarla (`removeCalculate`) y exportar el XML modificado. | Alta | Implementado |
| RF-075 | `POST /api/formulario/analizar-calculadoras` debe analizar calculadoras sin recargar el formulario completo. | Media | Implementado |
| RF-076 | La respuesta de `/cargar` y `/sincronizar-codigo` debe incluir `calculadoras` (`AnalisisCalculadoras`). | Alta | Implementado |

Guía de usuario: [09-calculadoras-xforms.md](09-calculadoras-xforms.md).

---

## 4. Comparación de versiones

| ID | Requisito | Prioridad | Estado |
|----|-----------|-----------|--------|
| RF-030 | El usuario debe poder comparar el XML cargado con otro archivo XML. | Alta | Implementado |
| RF-031 | El usuario debe poder comparar dos archivos XML sin cargar previamente uno en la sesión. | Media | Implementado |
| RF-032 | La comparación debe detectar componentes añadidos, eliminados y modificados por `id`. | Alta | Implementado |
| RF-033 | Para componentes modificados, el sistema debe detallar cambios en label, hint, alert, tipo y appearance. | Alta | Implementado |
| RF-034 | La comparación debe mostrar resumen numérico (totales, añadidos, eliminados, modificados, sin cambios). | Media | Implementado |

---

## 5. API REST

| ID | Requisito | Prioridad | Estado |
|----|-----------|-----------|--------|
| RF-040 | `POST /api/formulario/cargar` — carga multipart y devuelve `{ xml, componentes, estructura, dependencias, calculadoras }`. | Alta | Implementado |
| RF-041 | `POST /api/formulario/sincronizar-codigo` — reparsea XML desde JSON (incluye dependencias y calculadoras). | Media | Implementado |
| RF-042 | `POST /api/formulario/exportar` — genera XML con modificaciones opcionales. | Alta | Implementado |
| RF-043 | `POST /api/formulario/vista-pdf` — genera PDF en binario. | Media | Implementado |
| RF-044 | `POST /api/formulario/comparar` — compara dos archivos multipart. | Alta | Implementado |
| RF-045 | `POST /api/formulario/modificar` — aplica `changes[]` y devuelve XML + log. | Alta | Implementado |
| RF-046 | `POST /api/formulario/lenguaje-natural` — procesa instrucción en español. | Alta | Implementado |
| RF-047 | `POST /api/formulario/analizar-logos` — inventario de logos con posición. | Media | Implementado |
| RF-048 | `POST /api/formulario/analizar-calculadoras` — inventario de `xf:bind @calculate` con fuentes de datos. | Media | Implementado |

---

## 6. Tipos de control soportados

El parseo reconoce, entre otros:

`input`, `select`, `select1`, `textarea`, `upload`, `secret`, `output`, `image`, `number`, `checkbox-input`, `explanation`, `date`, `time`, `currency`, `email`, `phone`, `static-attachment`, `databound-select1`, `yesno-input`.

---

## 7. Limitaciones conocidas (fuera de alcance actual)

| ID | Limitación |
|----|------------|
| RF-L01 | No se ejecuta el motor XForms/Orbeon real; no hay validación de reglas `xf:bind` en runtime. |
| RF-L02 | Los desplegables dinámicos (Provincia/Municipio JCYL) no cargan datos del servicio REST `servicios.jcyl.es`; solo se muestra la URL. |
| RF-L07 | El asistente NL no entiende frases completamente libres; usa patrones predefinidos en español. |
| RF-L08 | La UI depende de `cdn.tailwindcss.com` salvo que se empaquete Tailwind localmente. |
| RF-L03 | El PDF es una aproximación visual; no replica el renderizado exacto de Orbeon Form Runner. |
| RF-L04 | No hay autenticación ni control de acceso multiusuario. |
| RF-L05 | `yesno-input` y `appearance="full"` se renderizan de forma simplificada en vista diseño. |
| RF-L06 | No se persiste el estado en base de datos; todo es en memoria del navegador/sesión HTTP. |
| RF-L09 | El editor no ejecuta XPath en tiempo real; las calculadoras se analizan y editan en XML estático (sin recalcular valores como Orbeon Form Runner). |
