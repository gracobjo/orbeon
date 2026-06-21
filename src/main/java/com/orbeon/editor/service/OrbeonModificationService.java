package com.orbeon.editor.service;

import com.orbeon.editor.dto.ModificacionResponse;
import com.orbeon.editor.util.OrbeonXmlUtil;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Motor de modificaciones tipadas sobre XML Orbeon (formato JSON changes[]).
 * Portado desde orbeon-editor con soporte XPath tolerante a namespaces.
 */
@Service
public class OrbeonModificationService {

    public ModificacionResponse aplicarCambios(String xml, List<Map<String, Object>> changes) {
        if (changes == null || changes.isEmpty()) {
            ModificacionResponse resp = new ModificacionResponse();
            resp.setXml(xml);
            return resp;
        }
        try {
            Document doc = OrbeonXmlUtil.parsear(xml);
            List<String> applied = new ArrayList<>();
            List<String> changeLog = new ArrayList<>();

            for (Map<String, Object> change : changes) {
                String type = (String) change.get("type");
                try {
                    String msg = aplicarCambio(doc, change, type);
                    applied.add("✓ " + msg);
                    changeLog.add(msg);
                } catch (Exception e) {
                    applied.add("✗ " + type + ": " + e.getMessage());
                }
            }

            ModificacionResponse resp = new ModificacionResponse();
            resp.setXml(OrbeonXmlUtil.serializar(doc));
            resp.setApplied(applied);
            resp.setChangeLog(changeLog);
            return resp;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al aplicar modificaciones: " + e.getMessage(), e);
        }
    }

    private String aplicarCambio(Document doc, Map<String, Object> change, String type) {
        return switch (type) {
            case "update-label" -> updateLabel(doc, change);
            case "update-hint" -> updateHint(doc, change);
            case "update-text" -> updateText(doc, change);
            case "update-image" -> updateImage(doc, change);
            case "hide-section" -> toggleSection(doc, change, true);
            case "show-section" -> toggleSection(doc, change, false);
            case "update-resource" -> updateResource(doc, change);
            case "update-bind" -> updateBind(doc, change);
            case "remove-field" -> removeField(doc, change);
            case "add-field" -> addField(doc, change);
            case "add-select-item" -> addSelectItem(doc, change);
            case "update-select-item" -> updateSelectItem(doc, change);
            case "remove-select-item" -> removeSelectItem(doc, change);
            case "add-image" -> addImage(doc, change);
            default -> throw new IllegalArgumentException("Tipo desconocido: " + type);
        };
    }

    private String updateLabel(Document doc, Map<String, Object> change) {
        return updateResourceValue(doc, (String) change.get("fieldId"), "label", (String) change.get("label"));
    }

    private String updateHint(Document doc, Map<String, Object> change) {
        return updateResourceValue(doc, (String) change.get("fieldId"), "hint", (String) change.get("hint"));
    }

    private String updateText(Document doc, Map<String, Object> change) {
        String elementId = (String) change.get("elementId");
        Element el = OrbeonXmlUtil.buscarPorId(doc, elementId);
        if (el == null) {
            throw new IllegalArgumentException("Elemento no encontrado: " + elementId);
        }
        el.setTextContent((String) change.get("value"));
        return "Texto actualizado en id=" + elementId;
    }

    /**
     * Actualiza imagen en fr-form-instance: tag (p. ej. iapa-img), src, filename, mediatype.
     */
    private String updateImage(Document doc, Map<String, Object> change) {
        String imageTag = (String) change.get("imageTag");
        String newSrc = (String) change.get("src");
        String newFilename = (String) change.get("filename");
        String newMediatype = (String) change.get("mediatype");

        NodeList instances = doc.getElementsByTagNameNS("*", "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element inst = (Element) instances.item(i);
            if (!"fr-form-instance".equals(inst.getAttribute("id"))) {
                continue;
            }
            NodeList all = inst.getElementsByTagName("*");
            for (int j = 0; j < all.getLength(); j++) {
                Element el = (Element) all.item(j);
                if (imageTag.equals(el.getLocalName())) {
                    if (newSrc != null) {
                        el.setTextContent(newSrc);
                    }
                    if (newFilename != null) {
                        el.setAttribute("filename", newFilename);
                    }
                    if (newMediatype != null) {
                        el.setAttribute("mediatype", newMediatype);
                    }
                    return "Imagen actualizada: " + imageTag;
                }
            }
        }
        throw new IllegalArgumentException("Imagen no encontrada: " + imageTag);
    }

    private String toggleSection(Document doc, Map<String, Object> change, boolean hide) {
        String sectionId = (String) change.get("sectionId");
        Element section = OrbeonXmlUtil.buscarPorId(doc, sectionId);
        if (section == null) {
            throw new IllegalArgumentException("Sección no encontrada: " + sectionId);
        }

        String bind = section.getAttribute("bind");
        if (!bind.isBlank()) {
            Element bindEl = OrbeonXmlUtil.buscarPorId(doc, bind);
            if (bindEl != null) {
                bindEl.setAttribute("relevant", hide ? "false()" : "true()");
                return (hide ? "Sección ocultada: " : "Sección mostrada: ") + sectionId;
            }
        }

        String css = section.getAttribute("class");
        if (hide && !css.contains("noprintinpdf")) {
            section.setAttribute("class", css.isBlank() ? "noprintinpdf" : css + " noprintinpdf");
        } else if (!hide) {
            section.setAttribute("class", css.replace("noprintinpdf", "").trim());
        }
        return (hide ? "Sección ocultada: " : "Sección mostrada: ") + sectionId;
    }

    private String updateResource(Document doc, Map<String, Object> change) {
        return updateResourceValue(doc,
                (String) change.get("fieldId"),
                (String) change.get("resourceType"),
                (String) change.get("value"));
    }

    /**
     * Busca en fr-form-resources el campo por local-name y actualiza label/hint/alert.
     */
    private String updateResourceValue(Document doc, String fieldId, String resourceType, String newValue) {
        NodeList instances = doc.getElementsByTagNameNS("*", "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element inst = (Element) instances.item(i);
            if (!"fr-form-resources".equals(inst.getAttribute("id"))) {
                continue;
            }
            NodeList all = inst.getElementsByTagName("*");
            for (int j = 0; j < all.getLength(); j++) {
                Element el = (Element) all.item(j);
                if (!fieldId.equals(el.getLocalName())) {
                    continue;
                }
                NodeList subs = el.getChildNodes();
                for (int k = 0; k < subs.getLength(); k++) {
                    Node sub = subs.item(k);
                    if (sub instanceof Element subEl && resourceType.equals(subEl.getLocalName())) {
                        subEl.setTextContent(newValue != null ? newValue : "");
                        return resourceType + " de '" + fieldId + "' → " + newValue;
                    }
                }
                Element nuevo = doc.createElementNS(el.getNamespaceURI(), resourceType);
                nuevo.setTextContent(newValue != null ? newValue : "");
                el.appendChild(nuevo);
                return resourceType + " de '" + fieldId + "' creado: " + newValue;
            }
        }
        throw new IllegalArgumentException("Campo no encontrado en resources: " + fieldId);
    }

    @SuppressWarnings("unchecked")
    private String updateBind(Document doc, Map<String, Object> change) {
        String bindId = (String) change.get("bindId");
        Element bindEl = OrbeonXmlUtil.buscarPorId(doc, bindId);
        if (bindEl == null) {
            throw new IllegalArgumentException("Bind no encontrado: " + bindId);
        }
        Map<String, Object> attribs = (Map<String, Object>) change.get("attributes");
        if (attribs != null) {
            for (Map.Entry<String, Object> e : attribs.entrySet()) {
                bindEl.setAttribute(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        return "Bind actualizado: " + bindId;
    }

    private String removeField(Document doc, Map<String, Object> change) {
        String fieldId = (String) change.get("fieldId");
        Element el = OrbeonXmlUtil.buscarPorId(doc, fieldId);
        if (el == null) {
            throw new IllegalArgumentException("Campo no encontrado: " + fieldId);
        }
        Node parent = el.getParentNode();
        if (parent != null && parent.getParentNode() != null) {
            parent.getParentNode().removeChild(parent);
            return "Campo eliminado del view: " + fieldId;
        }
        throw new IllegalArgumentException("No se pudo eliminar: " + fieldId);
    }

    private String addField(Document doc, Map<String, Object> change) {
        String sectionId = (String) change.get("sectionId");
        String fieldName = (String) change.get("fieldName");
        NodeList instances = doc.getElementsByTagNameNS("*", "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element inst = (Element) instances.item(i);
            if (!"fr-form-instance".equals(inst.getAttribute("id"))) {
                continue;
            }
            String sectionName = sectionId.replace("-section", "");
            NodeList sections = inst.getElementsByTagNameNS("*", sectionName);
            if (sections.getLength() > 0) {
                Element sectionEl = (Element) sections.item(0);
                Element newField = doc.createElementNS(sectionEl.getNamespaceURI(), fieldName);
                sectionEl.appendChild(newField);
                return "Campo añadido: " + fieldName + " en " + sectionId;
            }
        }
        throw new IllegalArgumentException("Sección no encontrada en instance: " + sectionId);
    }

    private String addSelectItem(Document doc, Map<String, Object> change) {
        String fieldId = (String) change.get("fieldId");
        String label = (String) change.get("label");
        String value = (String) change.get("value");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El valor de la opción es obligatorio");
        }
        Element campo = buscarCampoResources(doc, fieldId);
        Element item = doc.createElementNS(campo.getNamespaceURI(), "item");
        Element labelEl = doc.createElementNS(campo.getNamespaceURI(), "label");
        labelEl.setTextContent(label != null ? label : value);
        Element valueEl = doc.createElementNS(campo.getNamespaceURI(), "value");
        valueEl.setTextContent(value);
        item.appendChild(labelEl);
        item.appendChild(valueEl);
        campo.appendChild(item);
        return "Opción añadida en '" + fieldId + "': " + label + " (" + value + ")";
    }

    private String updateSelectItem(Document doc, Map<String, Object> change) {
        String fieldId = (String) change.get("fieldId");
        String value = (String) change.get("value");
        String newLabel = (String) change.get("label");
        String newValue = change.containsKey("newValue") ? (String) change.get("newValue") : null;
        Element item = buscarItemPorValor(doc, fieldId, value);
        if (newLabel != null) {
            Element labelEl = primerHijo(item, "label");
            if (labelEl != null) {
                labelEl.setTextContent(newLabel);
            }
        }
        if (newValue != null && !newValue.isBlank()) {
            Element valueEl = primerHijo(item, "value");
            if (valueEl != null) {
                valueEl.setTextContent(newValue);
            }
        }
        return "Opción actualizada en '" + fieldId + "' (valor=" + value + ")";
    }

    private String removeSelectItem(Document doc, Map<String, Object> change) {
        String fieldId = (String) change.get("fieldId");
        String value = (String) change.get("value");
        Element item = buscarItemPorValor(doc, fieldId, value);
        item.getParentNode().removeChild(item);
        return "Opción eliminada de '" + fieldId + "' (valor=" + value + ")";
    }

    /**
     * Añade imagen en instancia + recursos. Opcionalmente control fr:image en sección.
     */
    private String addImage(Document doc, Map<String, Object> change) {
        String imageTag = (String) change.get("imageTag");
        String src = (String) change.getOrDefault("src", "");
        String filename = (String) change.getOrDefault("filename", imageTag + ".png");
        String mediatype = (String) change.getOrDefault("mediatype", "image/png");
        String sectionId = (String) change.get("sectionId");

        if (imageTag == null || imageTag.isBlank()) {
            throw new IllegalArgumentException("imageTag es obligatorio");
        }

        Element instancia = buscarInstancia(doc, "fr-form-instance");
        Element tagEl = doc.createElementNS(instancia.getNamespaceURI(), imageTag);
        tagEl.setAttribute("filename", filename);
        tagEl.setAttribute("mediatype", mediatype);
        tagEl.setTextContent(src);
        instancia.appendChild(tagEl);

        Element resources = buscarInstancia(doc, "fr-form-resources");
        Element resourceRoot = primerHijoPorLocalName(resources, "resource");
        if (resourceRoot == null) {
            throw new IllegalArgumentException("No se encontró nodo resource en fr-form-resources");
        }
        Element resField = doc.createElementNS(resourceRoot.getNamespaceURI(), imageTag);
        Element lbl = doc.createElementNS(resourceRoot.getNamespaceURI(), "label");
        lbl.setTextContent((String) change.getOrDefault("label", ""));
        Element hint = doc.createElementNS(resourceRoot.getNamespaceURI(), "hint");
        resField.appendChild(lbl);
        resField.appendChild(hint);
        resourceRoot.appendChild(resField);

        String bindId = imageTag + "-bind";
        Element bindEl = doc.createElementNS(instancia.getNamespaceURI(), "bind");
        bindEl.setAttribute("id", bindId);
        bindEl.setAttribute("ref", imageTag);
        bindEl.setAttribute("name", imageTag);
        bindEl.setAttribute("type", "xf:anyURI");
        Element modelo = buscarModelo(doc);
        if (modelo != null) {
            modelo.appendChild(bindEl);
        }

        if (sectionId != null && !sectionId.isBlank()) {
            Element section = OrbeonXmlUtil.buscarPorId(doc, sectionId);
            if (section != null) {
                Element grid = primerDescendientePorLocalName(section, "grid");
                Element celda = grid != null ? primerDescendientePorLocalName(grid, "c") : null;
                if (celda != null) {
                    String ns = "http://orbeon.org/oxf/xml/form-builder";
                    Element imageControl = doc.createElementNS(ns, "image");
                    imageControl.setAttribute("id", imageTag + "-control");
                    imageControl.setAttribute("bind", bindId);
                    imageControl.setAttribute("class", "fr-static-attachment");
                    Element xfLabel = doc.createElementNS("http://www.w3.org/2002/xforms", "label");
                    xfLabel.setAttribute("ref", "$form-resources/" + imageTag + "/label");
                    imageControl.appendChild(xfLabel);
                    celda.appendChild(imageControl);
                }
            }
        }

        return "Imagen añadida: " + imageTag
                + (sectionId != null ? " en sección " + sectionId : " (solo instancia/recursos)");
    }

    private Element buscarCampoResources(Document doc, String fieldId) {
        Element inst = buscarInstancia(doc, "fr-form-resources");
        NodeList resources = inst.getElementsByTagNameNS("*", "resource");
        for (int r = 0; r < resources.getLength(); r++) {
            Element resource = (Element) resources.item(r);
            NodeList children = resource.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n instanceof Element el && fieldId.equals(el.getLocalName())) {
                    return el;
                }
            }
        }
        throw new IllegalArgumentException("Campo no encontrado en resources: " + fieldId);
    }

    private Element buscarItemPorValor(Document doc, String fieldId, String value) {
        Element campo = buscarCampoResources(doc, fieldId);
        NodeList children = campo.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element item) || !"item".equals(item.getLocalName())) {
                continue;
            }
            Element valueEl = primerHijo(item, "value");
            if (valueEl != null && value.equals(valueEl.getTextContent().trim())) {
                return item;
            }
        }
        throw new IllegalArgumentException("Opción no encontrada: " + value + " en " + fieldId);
    }

    private Element buscarInstancia(Document doc, String id) {
        NodeList instances = doc.getElementsByTagNameNS("*", "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element inst = (Element) instances.item(i);
            if (id.equals(inst.getAttribute("id"))) {
                return inst;
            }
        }
        throw new IllegalArgumentException("Instancia no encontrada: " + id);
    }

    private Element buscarModelo(Document doc) {
        NodeList models = doc.getElementsByTagNameNS("*", "model");
        return models.getLength() > 0 ? (Element) models.item(0) : null;
    }

    private Element primerHijo(Element padre, String localName) {
        NodeList children = padre.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    private Element primerHijoPorLocalName(Element padre, String localName) {
        NodeList children = padre.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    private Element primerDescendientePorLocalName(Element padre, String localName) {
        NodeList all = padre.getElementsByTagNameNS("*", localName);
        return all.getLength() > 0 ? (Element) all.item(0) : null;
    }

    public Map<String, Object> obtenerEsquema() {
        return Map.of(
                "description", "Esquema del fichero de modificaciones Orbeon (array changes)",
                "availableTypes", List.of(
                        "update-label    → fieldId, label",
                        "update-hint     → fieldId, hint",
                        "update-text     → elementId, value",
                        "update-image    → imageTag, src, filename, mediatype",
                        "hide-section    → sectionId",
                        "show-section    → sectionId",
                        "update-resource → fieldId, resourceType (label|hint|alert), value",
                        "update-bind     → bindId, attributes{}",
                        "remove-field    → fieldId",
                        "add-field       → sectionId, fieldName",
                        "add-select-item → fieldId, label, value",
                        "update-select-item → fieldId, value, label?, newValue?",
                        "remove-select-item → fieldId, value",
                        "add-image       → imageTag, src?, filename?, mediatype?, sectionId?, label?"
                ),
                "exampleFile", Map.of("changes", List.of(
                        Map.of("type", "update-label", "fieldId", "personaFisica-nombre", "label", "Nombre completo"),
                        Map.of("type", "update-hint", "fieldId", "personaFisica-nif", "hint", "DNI o NIE"),
                        Map.of("type", "update-image", "imageTag", "iapa-img",
                                "filename", "logo.png", "mediatype", "image/png", "src", "/fr/service/.../logo.bin"),
                        Map.of("type", "hide-section", "sectionId", "datosEcono-section")
                ))
        );
    }
}
