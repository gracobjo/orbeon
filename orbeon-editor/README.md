# Orbeon XML Editor

Aplicación Java con Jetty embebido para visualizar y modificar formularios Orbeon XForms.

## Requisitos

- Java 11+
- Maven 3.6+

## Compilar

```bash
cd orbeon-editor
mvn clean package -q
```

Genera: `target/orbeon-editor-1.0.0.jar`

## Ejecutar

```bash
# Con interfaz web (carga el XML desde la UI)
java -jar target/orbeon-editor-1.0.0.jar

# Cargando directamente un XML
java -jar target/orbeon-editor-1.0.0.jar --xml ruta/al/formulario.xml

# En otro puerto
java -jar target/orbeon-editor-1.0.0.jar --port 9090 --xml formulario.xml
```

Accede en: **http://localhost:8080**

---

## API REST

| Método | Endpoint        | Descripción                            |
|--------|-----------------|----------------------------------------|
| GET    | /api/status     | Estado del servidor                    |
| GET    | /api/structure  | Estructura completa del formulario     |
| POST   | /api/load       | Cargar XML (multipart o JSON con path) |
| POST   | /api/modify     | Aplicar fichero de modificaciones JSON |
| GET    | /api/export     | Descargar XML modificado               |
| GET    | /api/changelog  | Log de cambios aplicados               |
| GET    | /api/schema     | Esquema del fichero de modificaciones  |

---

## Fichero de modificaciones

Formato JSON con un array `changes`. Tipos disponibles:

### update-label — Cambiar el label de un campo
```json
{ "type": "update-label", "fieldId": "personaFisica-nombre", "label": "Nombre completo" }
```

### update-hint — Cambiar el hint/ayuda de un campo
```json
{ "type": "update-hint", "fieldId": "representante-nif", "hint": "Introduzca el NIF sin espacios" }
```

### update-image — Cambiar una imagen/logo
```json
{
  "type": "update-image",
  "imageTag": "iapa-img",
  "filename": "nuevo_logo.png",
  "mediatype": "image/png",
  "src": "/fr/service/persistence/crud/orbeon/builder/data/nuevo_logo.bin"
}
```

### hide-section / show-section — Ocultar o mostrar sección
```json
{ "type": "hide-section", "sectionId": "datosEcono-section" }
{ "type": "show-section", "sectionId": "datosEcono-section" }
```

### update-bind — Modificar atributos de un bind XForms
```json
{
  "type": "update-bind",
  "bindId": "datosBancarios-iban-bind",
  "attributes": { "required": "false()" }
}
```

### update-resource — Actualizar cualquier resource (label/hint/alert)
```json
{
  "type": "update-resource",
  "fieldId": "personaJuridica-cif",
  "resourceType": "alert",
  "value": "El CIF introducido no es válido"
}
```

### remove-field — Eliminar un campo del view
```json
{ "type": "remove-field", "fieldId": "campo-id-control" }
```

### Ejemplo completo
```json
{
  "changes": [
    { "type": "update-label",   "fieldId": "personaFisica-nombre", "label": "Nombre completo del solicitante" },
    { "type": "update-hint",    "fieldId": "personaFisica-nif",    "hint": "DNI, NIE o pasaporte" },
    { "type": "hide-section",   "sectionId": "datosEcono-section" },
    { "type": "update-image",   "imageTag": "logo-img", "filename": "logo_nuevo.png", "mediatype": "image/png", "src": "" },
    { "type": "update-bind",    "bindId": "telefono-bind", "attributes": { "required": "true()" } }
  ]
}
```

---

## Estructura del proyecto

```
orbeon-editor/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/orbeon/editor/
    │   ├── Main.java               ← Punto de entrada, configura Jetty
    │   ├── OrbeonXmlService.java   ← Parser y motor de modificaciones
    │   ├── ApiServlet.java         ← API REST (/api/*)
    │   └── StaticHtmlServlet.java  ← Sirve la interfaz web
    └── resources/static/
        └── index.html              ← Interfaz web completa (embebida en JAR)
```
