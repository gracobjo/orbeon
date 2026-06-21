package com.orbeon.editor.service;

import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.RecursoFormulario;
import com.orbeon.editor.util.OrbeonResourceParser;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servicio de parseo y modificación de plantillas XML Orbeon Form Runner
 * mediante DOM estándar y expresiones XPath tolerantes a namespaces.
 */
@Service
public class OrbeonFormService {

    private static final Set<String> TIPOS_CONTROL_VISUAL = Set.of(
            "input", "select", "select1", "textarea", "upload", "secret",
            "output", "image", "number", "checkbox-input", "explanation",
            "date", "time", "currency", "email", "phone", "static-attachment",
            "databound-select1", "yesno-input"
    );

    /** Contenedores estructurales de fr:view que no son controles editables. */
    private static final Set<String> TIPOS_CONTENEDOR = Set.of(
            "section", "grid", "c", "body", "view", "repeat", "iteration", "template"
    );

    /**
     * Lee el XML Orbeon y extrae los componentes visuales con sus labels, hints
     * y metadatos asociados (p. ej. ruta de imagen en atributo ref).
     */
    public List<ComponenteFormulario> parsearEstructuraDesdeString(String xml) {
        try {
            Document documento = parsearDocumento(xml);
            XPath xpath = XPathFactory.newInstance().newXPath();
            Map<String, String> bindToRef = construirMapaBindARef(documento, xpath);
            Map<String, RecursoFormulario> resources = OrbeonResourceParser.extraerRecursos(documento, xpath);

            /*
             * Localiza todos los controles visuales dentro de fr:view que declaran un id.
             * local-name() evita depender del prefijo de namespace (fr:, xf:, etc.).
             */
            NodeList controles = (NodeList) xpath.evaluate(
                    "//*[local-name()='view']//*[@id]",
                    documento,
                    XPathConstants.NODESET
            );

            List<ComponenteFormulario> componentes = new ArrayList<>();
            Set<String> idsProcesados = new HashSet<>();

            for (int i = 0; i < controles.getLength(); i++) {
                Element control = (Element) controles.item(i);
                String id = control.getAttribute("id");
                if (id.isBlank() || idsProcesados.contains(id)) {
                    continue;
                }

                String tipoLocal = control.getLocalName();
                if (!esControlFormulario(tipoLocal, control)) {
                    continue;
                }

                idsProcesados.add(id);
                String claveRecurso = extraerClaveRecurso(id, control);
                String label = obtenerTextoRecurso(documento, xpath, claveRecurso, "label");
                String hint = obtenerTextoRecurso(documento, xpath, claveRecurso, "hint");
                String alert = obtenerTextoRecurso(documento, xpath, claveRecurso, "alert");

                ComponenteFormulario componente = new ComponenteFormulario(id, tipoLocal, label, hint);
                componente.setAlert(alert);
                Map<String, String> metadatos = new HashMap<>();
                metadatos.put("resourceKey", claveRecurso);

                String bind = control.getAttribute("bind");
                if (!bind.isBlank()) {
                    metadatos.put("bind", bind);
                }

                if ("image".equals(tipoLocal) || "static-attachment".equals(tipoLocal)) {
                    /*
                     * Para fr:image se extrae el atributo ref (ruta del logo/imagen).
                     * Si no existe ref, se resuelve la ruta desde la instancia de datos
                     * usando el mapeo bind → ref definido en xf:bind.
                     */
                    String ref = control.getAttribute("ref");
                    if (ref.isBlank() && !bind.isBlank()) {
                        String refBind = bindToRef.get(bind);
                        if (refBind != null) {
                            ref = obtenerValorInstancia(documento, xpath, refBind);
                            metadatos.put("rutaInstancia", refBind);
                        }
                    }
                    metadatos.put("ref", ref != null ? ref : "");
                }

                componente.setMetadatos(metadatos);

                if ("select1".equals(tipoLocal) || "select".equals(tipoLocal)
                        || "databound-select1".equals(tipoLocal)) {
                    OrbeonResourceParser.configurarItemsSelect(
                            control, claveRecurso, resources, componente);
                }

                componentes.add(componente);
            }

            return componentes;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al parsear el XML Orbeon: " + e.getMessage(), e);
        }
    }

    /**
     * Aplica modificaciones sobre el XML original sin alterar el resto del documento.
     * Actualiza labels/hints en fr-form-resources y atributos ref en imágenes.
     */
    public String aplicarModificacionesDesdeString(String xmlOriginal, List<ComponenteFormulario> modificaciones) {
        if (modificaciones == null || modificaciones.isEmpty()) {
            return xmlOriginal;
        }

        try {
            Document documento = parsearDocumento(xmlOriginal);
            XPath xpath = XPathFactory.newInstance().newXPath();
            Map<String, String> bindToRef = construirMapaBindARef(documento, xpath);

            for (ComponenteFormulario mod : modificaciones) {
                if (mod == null || mod.getId() == null || mod.getId().isBlank()) {
                    continue;
                }

                String claveRecurso = mod.getMetadatos() != null && mod.getMetadatos().containsKey("resourceKey")
                        ? mod.getMetadatos().get("resourceKey")
                        : extraerClaveRecurso(mod.getId(), null);

                if (mod.getLabel() != null) {
                    actualizarTextoRecurso(documento, xpath, claveRecurso, "label", mod.getLabel());
                }
                if (mod.getHint() != null) {
                    actualizarTextoRecurso(documento, xpath, claveRecurso, "hint", mod.getHint());
                }
                if (mod.getAlert() != null) {
                    actualizarTextoRecurso(documento, xpath, claveRecurso, "alert", mod.getAlert());
                }

                String tipo = mod.getTipo();
                if ("image".equals(tipo) || "static-attachment".equals(tipo)) {
                    String nuevaRuta = mod.getMetadatos() != null ? mod.getMetadatos().get("ref") : null;
                    if (nuevaRuta != null) {
                        /*
                         * Busca el nodo fr:image por id y actualiza su atributo ref.
                         * Si la imagen no usa ref (patrón Orbeon con bind), actualiza
                         * el contenido textual en la instancia de datos del formulario.
                         */
                        Element imagen = (Element) xpath.evaluate(
                                "//*[local-name()='view']//*[@id='" + escaparXPathLiteral(mod.getId()) + "']",
                                documento,
                                XPathConstants.NODE
                        );

                        if (imagen != null) {
                            if (imagen.hasAttribute("ref")) {
                                imagen.setAttribute("ref", nuevaRuta);
                            } else {
                                String bind = imagen.getAttribute("bind");
                                String refInstancia = bindToRef.get(bind);
                                if (refInstancia == null && mod.getMetadatos() != null) {
                                    refInstancia = mod.getMetadatos().get("rutaInstancia");
                                }
                                if (refInstancia != null) {
                                    actualizarValorInstancia(documento, xpath, refInstancia, nuevaRuta);
                                } else {
                                    imagen.setAttribute("ref", nuevaRuta);
                                }
                            }
                        }
                    }
                }
            }

            return serializarDocumento(documento);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al aplicar modificaciones al XML: " + e.getMessage(), e);
        }
    }

    private Document parsearDocumento(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private String serializarDocumento(Document documento) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(documento), new StreamResult(writer));
        return writer.toString();
    }

    private boolean esControlFormulario(String tipoLocal, Element control) {
        if (TIPOS_CONTENEDOR.contains(tipoLocal)) {
            return false;
        }
        if (TIPOS_CONTROL_VISUAL.contains(tipoLocal)) {
            return true;
        }
        return control.hasAttribute("bind") && control.hasAttribute("id");
    }

    private String extraerClaveRecurso(String idControl, Element control) {
        if (idControl.endsWith("-control")) {
            return idControl.substring(0, idControl.length() - "-control".length());
        }
        if (control != null) {
            String bind = control.getAttribute("bind");
            if (bind.endsWith("-bind")) {
                return bind.substring(0, bind.length() - "-bind".length());
            }
        }
        return idControl;
    }

    /**
     * Obtiene el texto de label o hint desde fr-form-resources.
     * Ruta XPath: //instance[@id='fr-form-resources']//resource/[clave]/[label|hint]
     * Usamos local-name() para ser independientes del prefijo xf:.
     */
    private String obtenerTextoRecurso(Document documento, XPath xpath, String claveRecurso, String campo)
            throws Exception {
        String expresion = String.format(
                "//*[local-name()='instance' and @id='fr-form-resources']" +
                        "//*[local-name()='resource']/*[local-name()='%s']/*[local-name()='%s']",
                escaparXPathLiteral(claveRecurso),
                escaparXPathLiteral(campo)
        );
        Node nodo = (Node) xpath.evaluate(expresion, documento, XPathConstants.NODE);
        return nodo != null ? nodo.getTextContent().trim() : "";
    }

    private void actualizarTextoRecurso(Document documento, XPath xpath, String claveRecurso,
                                        String campo, String nuevoTexto) throws Exception {
        String expresion = String.format(
                "//*[local-name()='instance' and @id='fr-form-resources']" +
                        "//*[local-name()='resource']/*[local-name()='%s']/*[local-name()='%s']",
                escaparXPathLiteral(claveRecurso),
                escaparXPathLiteral(campo)
        );
        Node nodo = (Node) xpath.evaluate(expresion, documento, XPathConstants.NODE);
        if (nodo != null) {
            nodo.setTextContent(nuevoTexto);
        }
    }

    /**
     * Construye un mapa bind-id → ref relativo dentro de la instancia fr-form-instance.
     */
    private Map<String, String> construirMapaBindARef(Document documento, XPath xpath) throws Exception {
        Map<String, String> mapa = new LinkedHashMap<>();
        NodeList binds = (NodeList) xpath.evaluate(
                "//*[local-name()='bind' and @id and @ref]",
                documento,
                XPathConstants.NODESET
        );
        for (int i = 0; i < binds.getLength(); i++) {
            Element bind = (Element) binds.item(i);
            mapa.put(bind.getAttribute("id"), bind.getAttribute("ref"));
        }
        return mapa;
    }

    /**
     * Lee el valor textual de un nodo en fr-form-instance dado un ref relativo (p. ej. iapa-img).
     */
    private String obtenerValorInstancia(Document documento, XPath xpath, String refRelativo) throws Exception {
        String expresion = String.format(
                "//*[local-name()='instance' and @id='fr-form-instance']//*[local-name()='%s']",
                escaparXPathLiteral(refRelativo)
        );
        Node nodo = (Node) xpath.evaluate(expresion, documento, XPathConstants.NODE);
        return nodo != null ? nodo.getTextContent().trim() : "";
    }

    private void actualizarValorInstancia(Document documento, XPath xpath, String refRelativo,
                                            String nuevoValor) throws Exception {
        String expresion = String.format(
                "//*[local-name()='instance' and @id='fr-form-instance']//*[local-name()='%s']",
                escaparXPathLiteral(refRelativo)
        );
        Node nodo = (Node) xpath.evaluate(expresion, documento, XPathConstants.NODE);
        if (nodo != null) {
            nodo.setTextContent(nuevoValor);
        }
    }

    /**
     * Escapa comillas simples en literales XPath para evitar inyección en expresiones dinámicas.
     */
    private String escaparXPathLiteral(String valor) {
        if (valor == null) {
            return "";
        }
        if (!valor.contains("'")) {
            return valor;
        }
        return valor.replace("'", "', \"'\", '");
    }
}
