package com.orbeon.editor;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Servicio singleton que gestiona la carga, análisis y modificación del XML de Orbeon.
 */
public class OrbeonXmlService {

    // ── Namespaces Orbeon ──────────────────────────────────────────────────
    public static final String NS_XH  = "http://www.w3.org/1999/xhtml";
    public static final String NS_XF  = "http://www.w3.org/2002/xforms";
    public static final String NS_FR  = "http://orbeon.org/oxf/xml/form-runner";
    public static final String NS_XXF = "http://orbeon.org/oxf/xml/xforms";
    public static final String NS_FB  = "http://orbeon.org/oxf/xml/form-builder";

    private static final OrbeonXmlService INSTANCE = new OrbeonXmlService();
    private Document doc;
    private File currentFile;
    private final List<String> changeLog = new ArrayList<>();

    private OrbeonXmlService() {}

    public static OrbeonXmlService getInstance() { return INSTANCE; }

    // ── Carga ──────────────────────────────────────────────────────────────

    public synchronized void loadXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        doc = builder.parse(file);
        currentFile = file;
        changeLog.clear();
        System.out.println("XML parseado correctamente: " + file.getName());
    }

    public synchronized void loadXmlFromBytes(byte[] bytes, String filename) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        doc = builder.parse(new ByteArrayInputStream(bytes));
        currentFile = new File(filename);
        changeLog.clear();
    }

    public boolean isLoaded() { return doc != null; }
    public String getCurrentFileName() { return currentFile != null ? currentFile.getName() : null; }

    // ── Análisis de estructura ─────────────────────────────────────────────

    /**
     * Devuelve la estructura completa del formulario como Map serializable a JSON.
     */
    public synchronized Map<String, Object> getFormStructure() {
        if (doc == null) throw new IllegalStateException("No hay XML cargado");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", getFormTitle());
        result.put("fileName", currentFile != null ? currentFile.getName() : "");
        result.put("sections", getSections());
        result.put("images", getImages());
        result.put("instances", getInstanceIds());
        result.put("changeLog", new ArrayList<>(changeLog));
        return result;
    }

    private String getFormTitle() {
        NodeList titles = doc.getElementsByTagNameNS(NS_XH, "title");
        if (titles.getLength() > 0) return titles.item(0).getTextContent().trim();
        return "Sin título";
    }

    private List<String> getInstanceIds() {
        List<String> ids = new ArrayList<>();
        NodeList instances = doc.getElementsByTagNameNS(NS_XF, "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element el = (Element) instances.item(i);
            if (el.hasAttribute("id")) ids.add(el.getAttribute("id"));
        }
        return ids;
    }

    /**
     * Extrae todas las secciones (fr:section) con sus campos.
     */
    private List<Map<String, Object>> getSections() {
        List<Map<String, Object>> sections = new ArrayList<>();

        // Obtener resources para labels/hints
        Map<String, Map<String, String>> resources = extractResources();

        NodeList sectionNodes = doc.getElementsByTagNameNS(NS_FR, "section");
        for (int i = 0; i < sectionNodes.getLength(); i++) {
            Element section = (Element) sectionNodes.item(i);
            Map<String, Object> sectionMap = new LinkedHashMap<>();

            String id = section.getAttribute("id");
            String bind = section.getAttribute("bind");
            String cssClass = section.getAttribute("class");

            sectionMap.put("id", id);
            sectionMap.put("bind", bind);
            sectionMap.put("cssClass", cssClass);
            sectionMap.put("noPrintInPdf", cssClass.contains("noprintinpdf"));

            // Label de la sección desde resources
            String sectionKey = bind.replace("-bind", "");
            Map<String, String> sectionRes = resources.get(sectionKey);
            String sectionLabel = "";
            if (sectionRes != null && sectionRes.containsKey("label")) {
                sectionLabel = sectionRes.get("label");
            }
            // También buscar xf:label directo
            NodeList labels = section.getElementsByTagNameNS(NS_XF, "label");
            if (labels.getLength() > 0 && sectionLabel.isEmpty()) {
                sectionLabel = labels.item(0).getTextContent().trim();
            }
            sectionMap.put("label", sectionLabel);

            // Campos de la sección
            List<Map<String, Object>> fields = extractFields(section, resources);
            sectionMap.put("fields", fields);
            sectionMap.put("fieldCount", fields.size());

            // Grids
            NodeList grids = section.getElementsByTagNameNS(NS_FR, "grid");
            sectionMap.put("gridCount", grids.getLength());

            sections.add(sectionMap);
        }
        return sections;
    }

    /**
     * Extrae los campos (inputs, selects, checkboxes, dates, etc.) de una sección.
     */
    private List<Map<String, Object>> extractFields(Element section, Map<String, Map<String, String>> resources) {
        List<Map<String, Object>> fields = new ArrayList<>();

        // Tipos de campos Orbeon
        String[][] fieldTypes = {
            {NS_XF, "input",          "input"},
            {NS_XF, "select1",        "select1"},
            {NS_XF, "select",         "select"},
            {NS_XF, "textarea",       "textarea"},
            {NS_FR, "number",         "number"},
            {NS_FR, "date",           "date"},
            {NS_FR, "checkbox-input", "checkbox"},
            {NS_FR, "databound-select1", "databound-select1"},
            {NS_FR, "explanation",    "explanation"},
            {NS_FR, "text",           "text"},
            {NS_FR, "image",          "image"},
            {NS_FR, "attachment",     "attachment"},
        };

        for (String[] ft : fieldTypes) {
            NodeList nodes = section.getElementsByTagNameNS(ft[0], ft[1]);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                Map<String, Object> field = new LinkedHashMap<>();

                String bind = el.getAttribute("bind");
                String ref = el.getAttribute("ref");
                String id = el.getAttribute("id");
                field.put("type", ft[2]);
                field.put("id", id);
                field.put("bind", bind);
                field.put("ref", ref);

                // Resolver key para resources
                String resKey = bind.isEmpty() ? ref : bind.replace("-control", "").replace("-bind","");
                Map<String, String> res = resources.get(resKey);

                // Label
                String label = getChildText(el, NS_XF, "label");
                if (label.startsWith("$form-resources/") || label.isEmpty()) {
                    String refKey = label.replace("$form-resources/", "").replace("/label", "");
                    Map<String, String> altRes = resources.get(refKey);
                    label = (altRes != null && altRes.containsKey("label")) ? altRes.get("label") : label;
                }
                if (label.isEmpty() && res != null) label = res.getOrDefault("label", "");
                field.put("label", label);

                // Hint
                String hint = "";
                if (res != null) hint = res.getOrDefault("hint", "");
                if (hint.isEmpty()) hint = getChildText(el, NS_XF, "hint");
                field.put("hint", hint);

                // Alert
                String alert = "";
                if (res != null) alert = res.getOrDefault("alert", "");
                field.put("alert", alert);

                // Appearance
                if (el.hasAttribute("appearance")) field.put("appearance", el.getAttribute("appearance"));

                // Items para selects
                if ("select1".equals(ft[2]) || "select".equals(ft[2])) {
                    List<Map<String, String>> items = extractItems(el);
                    field.put("items", items);
                }

                fields.add(field);
            }
        }
        return fields;
    }

    private List<Map<String, String>> extractItems(Element el) {
        List<Map<String, String>> items = new ArrayList<>();
        NodeList itemNodes = el.getElementsByTagNameNS(NS_XF, "item");
        for (int i = 0; i < itemNodes.getLength(); i++) {
            Element item = (Element) itemNodes.item(i);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("label", getChildText(item, NS_XF, "label"));
            m.put("value", getChildText(item, NS_XF, "value"));
            items.add(m);
        }
        return items;
    }

    private String getChildText(Element el, String ns, String localName) {
        NodeList children = el.getElementsByTagNameNS(ns, localName);
        if (children.getLength() > 0) {
            return children.item(0).getTextContent().trim();
        }
        return "";
    }

    /**
     * Extrae todas las imágenes del formulario.
     */
    private List<Map<String, Object>> getImages() {
        List<Map<String, Object>> images = new ArrayList<>();
        // Buscar en fr-form-instance
        NodeList instances = doc.getElementsByTagNameNS(NS_XF, "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element inst = (Element) instances.item(i);
            if ("fr-form-instance".equals(inst.getAttribute("id"))) {
                findImgElements(inst, images);
            }
        }
        return images;
    }

    private void findImgElements(Node node, List<Map<String, Object>> images) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) node;
            String tag = el.getLocalName();
            if (tag != null && tag.contains("img")) {
                Map<String, Object> img = new LinkedHashMap<>();
                img.put("tag", tag);
                img.put("filename", el.getAttribute("filename"));
                img.put("mediatype", el.getAttribute("mediatype"));
                img.put("src", el.getTextContent().trim());
                images.add(img);
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            findImgElements(children.item(i), images);
        }
    }

    /**
     * Extrae los resources (label/hint/alert) de fr-form-resources.
     */
    private Map<String, Map<String, String>> extractResources() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        NodeList instances = doc.getElementsByTagNameNS(NS_XF, "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element inst = (Element) instances.item(i);
            if ("fr-form-resources".equals(inst.getAttribute("id"))) {
                // resources > resource > campo > label/hint/alert
                NodeList resources = inst.getElementsByTagName("resources");
                if (resources.getLength() == 0) resources = inst.getChildNodes();
                for (int r = 0; r < resources.getLength(); r++) {
                    Node res = resources.item(r);
                    if (res.getNodeType() != Node.ELEMENT_NODE) continue;
                    NodeList langResources = res.getChildNodes();
                    for (int l = 0; l < langResources.getLength(); l++) {
                        Node langRes = langResources.item(l);
                        if (langRes.getNodeType() != Node.ELEMENT_NODE) continue;
                        // Cada hijo es un campo
                        NodeList fields = langRes.getChildNodes();
                        for (int f = 0; f < fields.getLength(); f++) {
                            Node field = fields.item(f);
                            if (field.getNodeType() != Node.ELEMENT_NODE) continue;
                            String key = field.getLocalName();
                            Map<String, String> data = new LinkedHashMap<>();
                            NodeList subchildren = field.getChildNodes();
                            for (int s = 0; s < subchildren.getLength(); s++) {
                                Node sub = subchildren.item(s);
                                if (sub.getNodeType() == Node.ELEMENT_NODE) {
                                    String txt = sub.getTextContent().trim();
                                    if (!txt.isEmpty()) data.put(sub.getLocalName(), txt);
                                }
                            }
                            result.put(key, data);
                        }
                    }
                }
            }
        }
        return result;
    }

    // ── Modificaciones ─────────────────────────────────────────────────────

    /**
     * Aplica un fichero de modificaciones JSON sobre el XML cargado.
     * Formato del JSON de modificaciones: ver documentación en la interfaz.
     */
    public synchronized List<String> applyModifications(Map<String, Object> modifications) {
        if (doc == null) throw new IllegalStateException("No hay XML cargado");

        List<String> applied = new ArrayList<>();
        Map<String, Map<String, String>> resources = extractResources();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) modifications.get("changes");
        if (changes == null) throw new IllegalArgumentException("El JSON debe tener un array 'changes'");

        for (Map<String, Object> change : changes) {
            String type = (String) change.get("type");
            try {
                String msg = applyChange(change, type, resources);
                applied.add("✓ " + msg);
                changeLog.add(msg);
            } catch (Exception e) {
                applied.add("✗ Error en cambio " + type + ": " + e.getMessage());
            }
        }
        return applied;
    }

    private String applyChange(Map<String, Object> change, String type, Map<String, Map<String, String>> resources) {
        switch (type) {
            case "update-label":   return updateLabel(change);
            case "update-hint":    return updateHint(change);
            case "update-text":    return updateText(change);
            case "update-image":   return updateImage(change);
            case "hide-section":   return toggleSection(change, true);
            case "show-section":   return toggleSection(change, false);
            case "update-resource": return updateResource(change);
            case "add-field":      return addField(change);
            case "remove-field":   return removeField(change);
            case "update-bind":    return updateBind(change);
            default: throw new IllegalArgumentException("Tipo desconocido: " + type);
        }
    }

    private String updateLabel(Map<String, Object> change) {
        String fieldId = (String) change.get("fieldId");
        String newLabel = (String) change.get("label");
        return updateResourceValue(fieldId, "label", newLabel);
    }

    private String updateHint(Map<String, Object> change) {
        String fieldId = (String) change.get("fieldId");
        String newHint = (String) change.get("hint");
        return updateResourceValue(fieldId, "hint", newHint);
    }

    private String updateText(Map<String, Object> change) {
        String xpath = (String) change.get("xpath");
        String newValue = (String) change.get("value");
        // Buscar por id de elemento
        String elementId = (String) change.get("elementId");
        if (elementId != null) {
            Element el = findElementById(elementId);
            if (el != null) {
                el.setTextContent(newValue);
                return "Texto actualizado en elemento id=" + elementId;
            }
        }
        throw new IllegalArgumentException("No se encontró el elemento");
    }

    private String updateImage(Map<String, Object> change) {
        String imageTag = (String) change.get("imageTag");
        String newSrc = (String) change.get("src");
        String newFilename = (String) change.get("filename");
        String newMediatype = (String) change.get("mediatype");

        NodeList instances = doc.getElementsByTagNameNS(NS_XF, "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element inst = (Element) instances.item(i);
            if ("fr-form-instance".equals(inst.getAttribute("id"))) {
                NodeList allEls = inst.getElementsByTagName("*");
                for (int j = 0; j < allEls.getLength(); j++) {
                    Element el = (Element) allEls.item(j);
                    if (imageTag.equals(el.getLocalName())) {
                        if (newSrc != null) el.setTextContent(newSrc);
                        if (newFilename != null) el.setAttribute("filename", newFilename);
                        if (newMediatype != null) el.setAttribute("mediatype", newMediatype);
                        return "Imagen actualizada: " + imageTag;
                    }
                }
            }
        }
        throw new IllegalArgumentException("No se encontró la imagen: " + imageTag);
    }

    private String toggleSection(Map<String, Object> change, boolean hide) {
        String sectionId = (String) change.get("sectionId");
        Element section = findElementById(sectionId);
        if (section == null) throw new IllegalArgumentException("Sección no encontrada: " + sectionId);

        // Buscar el bind asociado y cambiar relevant
        String bind = section.getAttribute("bind");
        if (bind != null && !bind.isEmpty()) {
            Element bindEl = findElementById(bind);
            if (bindEl != null) {
                bindEl.setAttribute("relevant", hide ? "false()" : "true()");
                return (hide ? "Sección ocultada: " : "Sección mostrada: ") + sectionId;
            }
        }
        // Alternativa: añadir clase CSS
        String css = section.getAttribute("class");
        if (hide && !css.contains("noprintinpdf")) {
            section.setAttribute("class", css + " noprintinpdf");
        }
        return (hide ? "Sección ocultada: " : "Sección mostrada: ") + sectionId;
    }

    private String updateResource(Map<String, Object> change) {
        String fieldId = (String) change.get("fieldId");
        String subType = (String) change.get("resourceType"); // label, hint, alert
        String value = (String) change.get("value");
        return updateResourceValue(fieldId, subType, value);
    }

    private String updateResourceValue(String fieldId, String resourceType, String newValue) {
        NodeList instances = doc.getElementsByTagNameNS(NS_XF, "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element inst = (Element) instances.item(i);
            if ("fr-form-resources".equals(inst.getAttribute("id"))) {
                NodeList allEls = inst.getElementsByTagName("*");
                for (int j = 0; j < allEls.getLength(); j++) {
                    Element el = (Element) allEls.item(j);
                    if (fieldId.equals(el.getLocalName())) {
                        NodeList subEls = el.getChildNodes();
                        for (int k = 0; k < subEls.getLength(); k++) {
                            Node sub = subEls.item(k);
                            if (sub.getNodeType() == Node.ELEMENT_NODE
                                    && resourceType.equals(sub.getLocalName())) {
                                sub.setTextContent(newValue);
                                return resourceType + " de '" + fieldId + "' actualizado a: " + newValue;
                            }
                        }
                        // Si no existe el subelemento, crearlo
                        Element newEl = doc.createElement(resourceType);
                        newEl.setTextContent(newValue);
                        el.appendChild(newEl);
                        return resourceType + " de '" + fieldId + "' creado: " + newValue;
                    }
                }
            }
        }
        throw new IllegalArgumentException("No se encontró el campo en resources: " + fieldId);
    }

    private String addField(Map<String, Object> change) {
        // Añadir campo al fr-form-instance y resources
        String sectionId = (String) change.get("sectionId");
        String fieldName = (String) change.get("fieldName");
        String label = (String) change.getOrDefault("label", fieldName);

        // Añadir al instance
        NodeList instances = doc.getElementsByTagNameNS(NS_XF, "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element inst = (Element) instances.item(i);
            if ("fr-form-instance".equals(inst.getAttribute("id"))) {
                Element formEl = (Element) inst.getFirstChild();
                while (formEl != null && formEl.getNodeType() != Node.ELEMENT_NODE) {
                    formEl = (Element) formEl.getNextSibling();
                }
                if (formEl != null) {
                    // Buscar sección por nombre
                    String sectionName = sectionId.replace("-section", "");
                    NodeList sectionEls = formEl.getElementsByTagName(sectionName);
                    if (sectionEls.getLength() > 0) {
                        Element sectionEl = (Element) sectionEls.item(0);
                        Element newField = doc.createElement(fieldName);
                        sectionEl.appendChild(newField);
                    }
                }
            }
        }
        return "Campo añadido: " + fieldName + " en sección " + sectionId;
    }

    private String removeField(Map<String, Object> change) {
        String fieldId = (String) change.get("fieldId");
        // Buscar y eliminar el control del view
        Element el = findElementById(fieldId);
        if (el != null && el.getParentNode() != null) {
            // Eliminar la celda contenedora (fr:c)
            Node parent = el.getParentNode();
            Node grandParent = parent.getParentNode();
            if (grandParent != null) {
                grandParent.removeChild(parent);
                return "Campo eliminado del view: " + fieldId;
            }
        }
        throw new IllegalArgumentException("No se encontró el campo: " + fieldId);
    }

    private String updateBind(Map<String, Object> change) {
        String bindId = (String) change.get("bindId");
        Element bindEl = findElementById(bindId);
        if (bindEl == null) throw new IllegalArgumentException("Bind no encontrado: " + bindId);

        Map<String, Object> attribs = (Map<String, Object>) change.get("attributes");
        if (attribs != null) {
            for (Map.Entry<String, Object> entry : attribs.entrySet()) {
                bindEl.setAttribute(entry.getKey(), entry.getValue().toString());
            }
        }
        return "Bind actualizado: " + bindId;
    }

    private Element findElementById(String id) {
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            if (id.equals(el.getAttribute("id"))) return el;
        }
        return null;
    }

    // ── Exportación ────────────────────────────────────────────────────────

    /**
     * Serializa el XML actual a bytes (para descarga).
     */
    public synchronized byte[] exportXml() throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    /**
     * Guarda el XML modificado en un fichero.
     */
    public synchronized void saveXml(File outputFile) throws Exception {
        byte[] bytes = exportXml();
        Files.write(outputFile.toPath(), bytes);
    }

    public List<String> getChangeLog() { return Collections.unmodifiableList(changeLog); }
}
