# Requisitos no funcionales

**Proyecto:** Orbeon Form Editor  
**Versión:** 1.0.0-SNAPSHOT

---

## 1. Rendimiento

| ID | Requisito | Criterio de aceptación |
|----|-----------|------------------------|
| RNF-001 | El parseo de plantillas de ~8.000 líneas XML debe completarse en menos de 5 segundos en hardware de desarrollo estándar. | Medido con `684_F1b_MIXTO_480_Solicitud_PRE.txt` (~300 controles). |
| RNF-002 | La respuesta de la API REST de carga no debe superar 10 MB de payload JSON para plantillas típicas. | Depende del tamaño del XML y número de componentes. |
| RNF-003 | La generación de vista PDF debe completarse en menos de 10 segundos para formularios medianos. | Mock PDF con OpenPDF. |
| RNF-004 | El frontend debe permanecer usable durante operaciones asíncronas (indicadores de carga). | Spinner/estado en barra superior. |

---

## 2. Usabilidad

| ID | Requisito | Criterio de aceptación |
|----|-----------|------------------------|
| RNF-010 | Interfaz en español con terminología Orbeon (label, hint, sección, bind). | Textos UI en `index.html`. |
| RNF-011 | Diseño responsive básico con Tailwind CSS; usable en pantallas ≥ 1280 px (dos paneles). | Layout flex 50/50. |
| RNF-012 | Búsqueda en tiempo real en lista y secciones sin recargar página. | Filtro client-side. |
| RNF-013 | Mensajes de error comprensibles ante XML inválido o peticiones mal formadas. | `GlobalExceptionHandler` + alertas JS. |
| RNF-014 | Vista diseño diferencia visualmente desplegables estáticos vs dinámicos. | Badge «Opciones dinámicas». |

---

## 3. Compatibilidad

| ID | Requisito | Criterio de aceptación |
|----|-----------|------------------------|
| RNF-020 | Java 17 o superior. | `pom.xml` → `java.version=17`. |
| RNF-021 | Navegadores modernos: Chrome, Edge, Firefox (últimas 2 versiones). | Sin polyfills legacy. |
| RNF-022 | Compatibilidad con XML Orbeon Form Runner exportado desde Form Builder (namespaces `fr:`, `xf:`, `xxf:`). | XPath con `local-name()`. |
| RNF-023 | Codificación UTF-8 en lectura/escritura XML. | `StandardCharsets.UTF_8`. |

---

## 4. Mantenibilidad

| ID | Requisito | Criterio de aceptación |
|----|-----------|------------------------|
| RNF-030 | Arquitectura en capas: controller → service → util/model. | Paquetes `controller`, `service`, `model`, `util`, `dto`. |
| RNF-031 | Servicios Spring con responsabilidad única (parseo, estructura, modificación, comparación, PDF). | 5 servicios especializados. |
| RNF-032 | Tests automatizados para regresión de desplegables. | `SelectItemsVerificationTest`. |
| RNF-033 | Documentación de API de modificaciones auto-generada vía endpoint. | `/esquema-modificaciones`. |

---

## 5. Seguridad

| ID | Requisito | Criterio de aceptación |
|----|-----------|------------------------|
| RNF-040 | La aplicación es de uso interno/desarrollo; sin autenticación por defecto. | CORS abierto `*`. |
| RNF-041 | No almacenar credenciales ni datos personales en servidor. | Stateless; XML en memoria de petición. |
| RNF-042 | Validar entrada XML para evitar XXE: parser DOM sin entidades externas. | `DocumentBuilderFactory` seguro. |
| RNF-043 | Limitar tamaño de upload en producción (configurable vía Spring `spring.servlet.multipart.max-*`). | Por configurar en despliegue. |

---

## 6. Disponibilidad y despliegue

| ID | Requisito | Criterio de aceptación |
|----|-----------|------------------------|
| RNF-050 | Ejecutable como JAR Spring Boot empaquetado con `mvn package`. | `spring-boot-maven-plugin`. |
| RNF-051 | Puerto por defecto 8080; configurable vía `server.port`. | `application.properties` opcional. |
| RNF-052 | Sin dependencia de base de datos externa. | Solo archivos en disco del cliente. |

---

## 7. Escalabilidad

| ID | Requisito | Criterio de aceptación |
|----|-----------|------------------------|
| RNF-060 | Diseño stateless en backend: cada petición es independiente. | XML enviado en cada POST. |
| RNF-061 | Escalado horizontal posible detrás de balanceador (sin sesión servidor). | Sin `@SessionScope`. |

---

## 8. Interoperabilidad

| ID | Requisito | Criterio de aceptación |
|----|-----------|------------------------|
| RNF-070 | API REST JSON/multipart consumible por herramientas externas (scripts, CI). | Endpoints documentados. |
| RNF-071 | XML exportado compatible con reimportación en Orbeon Form Builder. | Preserva estructura DOM; cambios mínimos. |
| RNF-072 | Formato `changes[]` compatible con prototipo `orbeon-editor/`. | Migrado desde `OrbeonXmlService`. |
| RNF-073 | El backend no realiza peticiones HTTP salientes en operación normal. | Sin cliente HTTP en Java; ver [05-apis-externas.md](05-apis-externas.md). |
| RNF-074 | El asistente NL funciona sin servicios de IA en la nube. | `OrbeonNaturalLanguageService` local. |
| RNF-075 | Dependencia de internet limitada al CDN Tailwind en el navegador. | Empaquetable offline. |
