package com.orbeon.editor.util;

import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.ItemSelect;
import com.orbeon.editor.model.RecursoFormulario;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae recursos (label, hint, alert, items) desde fr-form-resources
 * y resuelve opciones de xf:select1 con xf:itemset.
 */
public final class OrbeonResourceParser {

    private static final Pattern REF_ITEMSET = Pattern.compile("\\$form-resources/([^/]+)/item");

    private OrbeonResourceParser() {
    }

    /**
     * Lee todos los campos bajo fr-form-resources incluyendo items de desplegables.
     */
    public static Map<String, RecursoFormulario> extraerRecursos(Document doc, XPath xpath) throws Exception {
        Map<String, RecursoFormulario> result = new LinkedHashMap<>();
        NodeList campos = (NodeList) xpath.evaluate(
                "//*[local-name()='instance' and @id='fr-form-resources']" +
                        "//*[local-name()='resource']/*",
                doc, XPathConstants.NODESET
        );
        for (int i = 0; i < campos.getLength(); i++) {
            Element campo = (Element) campos.item(i);
            String key = campo.getLocalName();
            RecursoFormulario recurso = new RecursoFormulario();
            List<ItemSelect> items = new ArrayList<>();

            NodeList subs = campo.getChildNodes();
            for (int j = 0; j < subs.getLength(); j++) {
                Node sub = subs.item(j);
                if (!(sub instanceof Element el)) {
                    continue;
                }
                String nombre = el.getLocalName();
                if ("item".equals(nombre)) {
                    items.add(new ItemSelect(
                            textoHijoDirecto(el, "label"),
                            textoHijoDirecto(el, "value")
                    ));
                } else if (List.of("label", "hint", "alert").contains(nombre)) {
                    String txt = el.getTextContent().trim();
                    switch (nombre) {
                        case "label" -> recurso.setLabel(txt);
                        case "hint" -> recurso.setHint(txt);
                        case "alert" -> recurso.setAlert(txt);
                        default -> { }
                    }
                }
            }
            recurso.setItems(items);
            result.put(key, recurso);
        }
        return result;
    }

    /**
     * Resuelve opciones y marca desplegables dinámicos (Provincia, Municipio, servicios REST).
     */
    public static void configurarItemsSelect(Element control, String claveRecurso,
                                              Map<String, RecursoFormulario> resources,
                                              ComponenteFormulario componente) {
        List<ItemSelect> items = resolverItemsSelect(control, claveRecurso, resources);
        componente.setItems(items);

        NodeList itemsets = control.getElementsByTagNameNS("*", "itemset");
        if (itemsets.getLength() > 0) {
            String ref = ((Element) itemsets.item(0)).getAttribute("ref");
            if (!ref.isBlank()) {
                componente.getMetadatos().put("itemsetRef", ref);
                if (!ref.startsWith("$form-resources") && items.isEmpty()) {
                    componente.getMetadatos().put("itemsetDinamico", "true");
                }
            }
        }
        String resource = control.getAttribute("resource");
        if (resource != null && !resource.isBlank()) {
            componente.getMetadatos().put("resourceUrl", resource.trim());
        }
    }

    /**
     * Orbeon declara opciones con xf:itemset ref="$form-resources/campo/item",
     * no como xf:item hijos directos del control.
     */
    public static List<ItemSelect> resolverItemsSelect(Element control, String claveRecurso,
                                                        Map<String, RecursoFormulario> resources) {
        List<ItemSelect> inline = extraerItemsInline(control);
        if (!inline.isEmpty()) {
            return inline;
        }

        NodeList itemsets = control.getElementsByTagNameNS("*", "itemset");
        if (itemsets.getLength() > 0) {
            String ref = ((Element) itemsets.item(0)).getAttribute("ref");
            Matcher m = REF_ITEMSET.matcher(ref);
            if (m.find()) {
                RecursoFormulario rec = resources.get(m.group(1));
                if (rec != null && !rec.getItems().isEmpty()) {
                    return new ArrayList<>(rec.getItems());
                }
            }
        }

        RecursoFormulario porClave = resources.get(claveRecurso);
        if (porClave != null && !porClave.getItems().isEmpty()) {
            return new ArrayList<>(porClave.getItems());
        }
        return List.of();
    }

    private static List<ItemSelect> extraerItemsInline(Element control) {
        List<ItemSelect> items = new ArrayList<>();
        NodeList hijos = control.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node n = hijos.item(i);
            if (n instanceof Element el && "item".equals(el.getLocalName())) {
                items.add(new ItemSelect(
                        textoHijoDirecto(el, "label"),
                        textoHijoDirecto(el, "value")
                ));
            }
        }
        return items;
    }

    private static String textoHijoDirecto(Element el, String localName) {
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element child && localName.equals(child.getLocalName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }
}
