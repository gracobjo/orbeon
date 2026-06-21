# Orbeon Form Editor

Editor visual de plantillas XML de **Orbeon Form Runner**: carga, edición, comparación, vista PDF y asistente en lenguaje natural.

## Inicio rápido

```bash
mvn spring-boot:run
```

Abrir [http://localhost:8080](http://localhost:8080)

**Requisitos:** Java 17+, Maven 3.8+

## Funcionalidades

- Parseo y edición de XML Orbeon (labels, hints, alerts, desplegables, logos)
- Vista diseño y PDF mock
- Comparación entre versiones (PRE vs v39, etc.)
- Asistente en español + panel CRUD de desplegables
- Editor CRUD contextual: clic en resultados → XML + previsualizar antes de guardar
- API REST en `/api/formulario/*`

## Documentación

Ver carpeta [`docs/`](docs/README.md):

| Doc | Contenido |
|-----|-----------|
| [HOWTO arranque y CRUD](docs/06-howto-arranque-y-crud.md) | Cómo arrancar y editar labels/hints/desplegables |
| [Editor CRUD contextual](docs/08-editor-crud-contextual-y-preview.md) | Navegar desde resultados, preview antes de guardar |
| [Dependencias de secciones](docs/07-dependencias-secciones.md) | Visibilidad condicional y CRUD de `relevant` |
| [Desarrollador](docs/04-documentacion-desarrollador.md) | Arquitectura y API |
| [APIs externas](docs/05-apis-externas.md) | Dependencias de red |

## Plantillas de ejemplo

- `684_F1b_MIXTO_480_Solicitud_PRE.txt`
- `684_F1b_MIXTO_480_Solicitud_v39.txt`

## Stack

Spring Boot 3.2 · Java 17 · OpenPDF · Tailwind (CDN)

## Licencia

Uso interno / proyecto propio.
