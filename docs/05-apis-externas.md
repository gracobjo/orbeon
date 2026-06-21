# APIs y dependencias externas

**Proyecto:** Orbeon Form Editor  
**Versión:** 1.0.0-SNAPSHOT  
**Última revisión:** junio 2026

---

## Resumen ejecutivo

| Tipo | ¿La aplicación llama en runtime? | Detalle |
|------|----------------------------------|---------|
| **API REST propia** | Sí (interna) | `http://localhost:8080/api/formulario/*` |
| **Tailwind CSS CDN** | Sí (navegador) | `https://cdn.tailwindcss.com` |
| **Servicios JCYL (geolrest)** | **No** | Solo referenciados en el XML Orbeon |
| **Orbeon Persistence** | **No** | Rutas en imágenes del XML; no se descargan |
| **LLM / OpenAI / IA cloud** | **No** | Asistente NL basado en reglas locales |
| **Maven Central** | Solo build | Dependencias Java al compilar |

**Conclusión:** el editor es **autocontenido** en backend. Las únicas salidas de red en uso normal son el **CDN de Tailwind** (UI) y, opcionalmente, **Maven** al compilar. No hay `RestTemplate`, `WebClient` ni `HttpClient` en el código Java.

---

## 1. APIs consumidas por la aplicación

### 1.1 API REST interna (Spring Boot)

El frontend (`index.html`) solo habla con el backend local:

| Endpoint | Método | Uso |
|----------|--------|-----|
| `/api/formulario/cargar` | POST | Cargar XML |
| `/api/formulario/sincronizar-codigo` | POST | Reparsear XML editado |
| `/api/formulario/exportar` | POST | Descargar XML |
| `/api/formulario/vista-pdf` | POST | Generar PDF |
| `/api/formulario/comparar` | POST | Comparar dos XML |
| `/api/formulario/modificar` | POST | Aplicar `changes[]` |
| `/api/formulario/esquema-modificaciones` | GET | Documentación JSON |
| `/api/formulario/lenguaje-natural` | POST | Asistente en español |
| `/api/formulario/analizar-logos` | POST | Inventario de logos |

Base URL por defecto: `http://localhost:8080`

### 1.2 Tailwind CSS (CDN)

```html
<script src="https://cdn.tailwindcss.com"></script>
```

- **Archivo:** `src/main/resources/static/index.html`
- **Propósito:** estilos de la interfaz
- **Requisito de red:** el navegador necesita internet (o sustituir por build local de Tailwind para entornos offline)
- **Datos enviados:** petición GET al CDN; no se envía XML ni datos del formulario

---

## 2. APIs referenciadas en plantillas XML (no invocadas por el editor)

Estas URLs aparecen en las plantillas Orbeon exportadas. El editor **las detecta y muestra** en metadatos / vista diseño, pero **no realiza peticiones HTTP** a ellas.

### 2.1 Junta de Castilla y León — GeoREST (`servicios.jcyl.es`)

Servicio de datos geográficos usado por controles `fr:databound-select1` en formularios JCYL.

| Endpoint (patrón) | Uso en formulario |
|-------------------|-------------------|
| `http://servicios.jcyl.es/geolrest/geolServicio/Provincias` | Listado de provincias |
| `https://servicios.jcyl.es/geolrest/geolServicio/ProvinciasComunidad?cComunidad=CL` | Provincias de Castilla y León |
| `.../MunicipiosProvincia?cProv={...}` | Municipios por provincia |
| `.../LocalidadesMunicipioProvincia?cProv={...}&cMuni={...}` | Localidades |

**Ejemplo en `684_F1b_MIXTO_480_Solicitud_PRE.txt`:**

```xml
<fr:databound-select1
    resource="http://servicios.jcyl.es/geolrest/geolServicio/Provincias"
    ...>
  <xf:itemset ref="Provincia"/>
</fr:databound-select1>
```

En **Orbeon Form Runner** (runtime real), estos desplegables cargan opciones vía XForms. En **este editor**, se marcan como `itemsetDinamico: true` y se muestra la URL en la vista diseño.

### 2.2 Orbeon Persistence (rutas de imágenes)

Las imágenes adjuntas en `fr-form-instance` suelen apuntar a rutas del servidor Orbeon:

```
/fr/service/persistence/crud/orbeon/builder/data/{hash}/{file}.bin
```

**Ejemplo:** logo IAPA en la plantilla PRE:

```xml
<iapa-img filename="iapa_684_480.png" mediatype="image/png">
  /fr/service/persistence/crud/orbeon/builder/data/ce7fcca2.../03ca30b7....bin
</iapa-img>
```

El editor **no descarga** estos binarios. Solo lee y puede modificar la ruta en el XML (`update-image`, asistente NL).

---

## 3. Dependencias de build (Maven)

| Artefacto | Origen | Uso |
|-----------|--------|-----|
| `spring-boot-starter-web` | Maven Central | Servidor HTTP + REST |
| `openpdf` 2.0.3 | Maven Central | Generación PDF |
| `spring-boot-starter-test` | Maven Central | Tests JUnit |

Repositorio: configurado por `spring-boot-starter-parent` → [repo.maven.apache.org](https://repo.maven.apache.org/maven2/)

---

## 4. Asistente de lenguaje natural — sin API de IA

El módulo `OrbeonNaturalLanguageService` interpreta instrucciones en **español** mediante:

- Expresiones regulares y normalización de texto (sin tildes para coincidencia)
- Resolución de campos por label / id / `resourceKey`
- Traducción a operaciones `changes[]` del motor XML

**No utiliza:** OpenAI, Azure OpenAI, Anthropic, Google Gemini ni ningún servicio de IA externo.

---

## 5. Diagrama de flujo de red

```mermaid
flowchart TB
    subgraph Navegador
        UI[index.html]
    end

    subgraph Internet_opcional["Internet (opcional)"]
        CDN[cdn.tailwindcss.com]
    end

    subgraph Servidor_local["Servidor local :8080"]
        API[Spring Boot REST]
        DOM[Parser XML DOM]
        PDF[OpenPDF]
        NL[OrbeonNaturalLanguageService]
    end

    subgraph Solo_en_XML["Solo metadatos en XML — sin HTTP"]
        JCYL[servicios.jcyl.es/geolrest]
        ORB[/fr/service/persistence/...]
    end

    UI -->|fetch /api/formulario/*| API
    UI -.->|GET estilos| CDN
    API --> DOM
    API --> PDF
    API --> NL
    DOM -.->|referencia| JCYL
    DOM -.->|referencia| ORB
```

---

## 6. Implicaciones para despliegue

| Escenario | Requisito |
|-----------|-----------|
| Uso en intranet sin internet | Empaquetar Tailwind localmente o usar CSS estático |
| Editor sin acceso a JCYL | Funciona; desplegables dinámicos sin opciones en vista |
| Sustituir logos | Editar rutas en XML; no hace falta que Orbeon Persistence esté accesible |
| CI/CD | Solo `mvn test` / `mvn package`; sin claves API |

---

## 7. Referencias

- [Documentación desarrollador](04-documentacion-desarrollador.md) — endpoints y servicios
- [Requisitos funcionales RF-L02](01-requisitos-funcionales.md) — limitación desplegables dinámicos
- [Orbeon Form Runner](https://doc.orbeon.com/) — runtime que sí consume servicios del XML
