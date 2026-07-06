# Roadmap y mejoras a implementar

Fichero vivo de **pendientes**, **ideas de eficiencia** y **deuda técnica** para Orbeon Form Editor.

Última revisión: junio 2026.

---

## Cómo usar este documento

| Prioridad | Significado |
|-----------|-------------|
| **P0** | Bloquea eficiencia diaria o calidad del resultado |
| **P1** | Alto impacto; conviene en la siguiente iteración |
| **P2** | Mejora deseable; planificar cuando haya capacidad |
| **P3** | Investigación o nice-to-have |

Estados: `pendiente` · `en curso` · `hecho` · `descartado`

---

## 1. Eficiencia para el usuario (qué debemos hacer)

Estas acciones reducirían tiempo y errores en el flujo real **instrucciones PDF → XML PRE**.

### P0 — Flujo de trabajo

| ID | Mejora | Por qué | Estado |
|----|--------|---------|--------|
| M-001 | **Autoguardado local** (localStorage / IndexedDB) del XML y changelog | Evita pérdida al recargar el navegador (RF-L06) | pendiente |
| M-002 | **Aplicar propuestas seleccionadas** en el modal PDF (checkbox por propuesta) | Hoy solo «todo automático» o manual uno a uno | pendiente |
| M-003 | **Asistente para altas «Revisar»**: copiar bloque XML desde archivo PRE pegado o cargado | Las altas de anexos son el cuello de botella manual | pendiente |
| M-004 | **Comparar resultado vs PRE** con un clic tras exportar/analizar | Validación habitual; hoy son 3–4 pasos | pendiente |
| M-005 | **Deshacer / rehacer** último cambio (al menos 1 nivel) | Reduce miedo a «Aplicar al XML» | pendiente |

### P1 — Menos fricción en la UI

| ID | Mejora | Por qué | Estado |
|----|--------|---------|--------|
| M-010 | Restaurar **Vista PDF** en panel derecho (pestaña o botón) | API existe; usuarios validan contra PDF oficial | pendiente |
| M-011 | **Cargar JSON** desde Modificar JSON (fichero además de pegar) | Se quitó del header; falta sustituto claro | pendiente |
| M-012 | **Atajos de teclado** documentados (Ctrl+S exportar, Esc cerrar modales) | Usuarios avanzados editan más rápido | pendiente |
| M-013 | **Indicador de progreso** en Analizar PDF y comparar (spinner en botón) | Operaciones de varios segundos sin feedback claro | pendiente |
| M-014 | **Historial de archivos recientes** (solo nombres, sin subir al servidor) | Reabrir el mismo PRE/v39 a diario | pendiente |
| M-015 | En modal PDF: botón **«Aplicar solo automáticos»** sin re-analizar | Evita segundo análisis con checkbox | pendiente |

### P1 — Calidad del análisis PDF

| ID | Mejora | Por qué | Estado |
|----|--------|---------|--------|
| M-020 | Ampliar catálogo `instrucciones-684-mapeo.json` con reglas pendientes del PDF | Cobertura incompleta → trabajo manual | pendiente |
| M-021 | Catálogo **auto desde XML** para cualquier formulario (jun 2026); catálogos estáticos como complemento opcional | Implementado | hecho |
| M-022 | Nivel **baja** confianza + filtro en modal | Diferenciar inferencias débiles | pendiente |
| M-023 | Vista lado a lado: **anotación PDF** (texto + página) ↔ **campo XML** | Revisar propuestas **media** más rápido | parcial (pestaña Estructura en modal) |
| M-024 | Test de regresión con PDF real versionado en repo (o fixture mínimo) | Evitar regresiones del intérprete | pendiente |

### P2 — Colaboración y trazabilidad

| ID | Mejora | Por qué | Estado |
|----|--------|---------|--------|
| M-030 | Exportar **informe** de comparación / análisis PDF (Markdown o PDF) | Entregar a negocio sin capturas | pendiente |
| M-031 | Exportar changelog como JSON adjunto al XML | Trazabilidad en Git / Jira | pendiente |
| M-032 | Modo «diff XML» línea a línea en comparador | Complemento al diff por componente | pendiente |

---

## 2. Eficiencia técnica / arquitectura

| ID | Mejora | Impacto | Estado |
|----|--------|---------|--------|
| T-001 | Empaquetar **Tailwind local** (eliminar CDN) | Offline, arranque más predecible (RF-L08) | pendiente |
| T-002 | Dividir `index.html` en módulos JS | Mantenibilidad; menos riesgo al editar UI | pendiente |
| T-003 | Cache de parseo XML en cliente (hash del texto) | Re-sincronizar más rápido en plantillas grandes | pendiente |
| T-004 | Endpoint **batch**: analizar PDF + comparar con PRE en una petición | Menos round-trips | pendiente |
| T-005 | Validación XSD o esquema Orbeon post-modificación | Detectar XML roto antes de exportar | pendiente |
| T-006 | CI en GitHub Actions (`mvn test` en cada push) | Regresiones detectadas antes de merge | pendiente |
| T-007 | Límite configurable de tamaño multipart en producción | Seguridad (RNF-043) | pendiente |

---

## 3. Funcionalidad pendiente (visión producto)

Cosas que **queremos** pero aún no están implementadas:

| Área | Descripción | Prioridad |
|------|-------------|-----------|
| Motor XForms ligero | Evaluar `relevant` y `calculate` al cumplimentar instancia en vista diseño | P2 |
| Integración JCYL | Cargar provincia/municipio en preview (con cache y sin bloquear UI) | P2 |
| Asistente ampliado | Más patrones NL o integración LLM local/opcional | P3 |
| Autenticación | LDAP / básica para despliegue compartido | P2 |
| Multi-plantilla | Proyectos con varios XML en una sesión | P2 |
| Importar desde Orbeon API | Pull directo de Form Builder sin exportar manual | P3 |
| Publicar a Orbeon | Push del XML exportado vía API | P3 |
| Editor visual de secciones | Arrastrar/soltar campos (ambicioso) | P3 |

---

## 4. Lo ya conseguido (referencia)

Para no duplicar esfuerzo, esto **ya está operativo** (junio 2026):

- [x] Carga, edición y exportación XML
- [x] Vista diseño y motor `changes[]`
- [x] Asistente NL (reglas español)
- [x] Dependencias, calculadoras, controles `control-N`
- [x] CRUD contextual + preview
- [x] Búsqueda XML resaltada
- [x] Comparador (sesión y dos archivos)
- [x] Análisis PDF instrucciones + catálogo dinámico desde XML + tests
- [x] Modal PDF: pestañas Estructura y XML formulario
- [x] Comparar dos PDFs de instrucciones sobre el mismo XML
- [x] API REST documentada
- [x] Despliegue JAR portable
- [x] UI header unificada (botones azul orbeon)
- [x] Manual de usuario y documentación técnica

---

## 5. Deuda documental / consistencia

| ID | Tarea | Estado |
|----|-------|--------|
| D-001 | Actualizar CU-07 Vista PDF en [03-casos-de-uso](03-casos-de-uso.md) (UI eliminada, API sigue) | pendiente |
| D-002 | Diagrama UML con servicios PDF instrucciones | pendiente |
| D-003 | Vídeo corto o GIF del flujo PDF → exportar | pendiente |

---

## 6. Orden de implementación recomendado

Si el objetivo es **máxima eficiencia en el flujo 684 PRE**:

```
1. M-001 Autoguardado local
2. M-002 / M-015 Aplicar propuestas PDF de forma granular
3. M-003 Asistente copia desde PRE para altas «Revisar»
4. M-004 Comparación rápida post-análisis
5. M-020 Ampliar catálogo 684
6. M-010 Restaurar Vista PDF en UI
7. T-006 CI automatizado
```

---

## 7. Cómo proponer nuevas mejoras

Añada una fila en la tabla correspondiente con:

- **ID** siguiente (M-xxx / T-xxx / D-xxx)
- Descripción breve
- Motivo / impacto
- Prioridad P0–P3
- Estado `pendiente`

O abra una issue en el repositorio enlazando a este fichero.
