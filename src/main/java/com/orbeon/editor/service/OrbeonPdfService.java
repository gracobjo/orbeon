package com.orbeon.editor.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfTemplate;
import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.ItemSelect;
import com.orbeon.editor.model.RecursoFormulario;
import com.orbeon.editor.model.SeccionFormulario;
import com.orbeon.editor.util.OrbeonResourceParser;
import org.springframework.stereotype.Service;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Genera vista PDF al estilo impreso Orbeon / formularios JCYL:
 * secciones en mayúsculas, rejillas de 12 columnas, etiqueta + valor y paginación.
 */
@Service
public class OrbeonPdfService {

    private static final int COLUMNAS_REJILLA = 12;
    private static final Set<String> TIPOS_CONTROL = Set.of(
            "input", "select", "select1", "textarea", "upload", "secret",
            "output", "image", "number", "checkbox-input", "explanation",
            "date", "time", "currency", "email", "phone", "static-attachment",
            "image-attachment", "databound-select1"
    );

    private static final Pattern REF_RECURSO = Pattern.compile("\\$form-resources/([^/]+)/");
    private static final Pattern IGUALDAD_RELEVANT = Pattern.compile("\\$([\\w.\\-]+)\\s*=\\s*'([^']*)'");
    private static final Pattern DESIGUALDAD_RELEVANT = Pattern.compile("\\$([\\w.\\-]+)\\s*!=\\s*'([^']*)'");
    private static final Pattern NON_BLANK_RELEVANT = Pattern.compile("xxf:non-blank\\(\\s*\\$([\\w.\\-]+)\\s*\\)");
    private static final Set<String> SECCIONES_OCULTAS_PDF = Set.of("Adme-section", "verFirma-section");

    private final OrbeonFormService orbeonFormService;

    public OrbeonPdfService(OrbeonFormService orbeonFormService) {
        this.orbeonFormService = orbeonFormService;
    }

    public byte[] generarPdf(String xml, List<ComponenteFormulario> modificaciones) {
        return generarPdf(xml, modificaciones, Map.of());
    }

    public byte[] generarPdf(String xml, List<ComponenteFormulario> modificaciones, Map<String, String> etiquetas) {
        String xmlProcesado = orbeonFormService.aplicarModificacionesDesdeString(xml, modificaciones);
        try {
            org.w3c.dom.Document doc = parsearDocumento(xmlProcesado);
            XPath xpath = XPathFactory.newInstance().newXPath();
            return construirPdfFormal(doc, xpath, etiquetas != null ? etiquetas : Map.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al generar el PDF: " + e.getMessage(), e);
        }
    }

    private byte[] construirPdfFormal(org.w3c.dom.Document documento, XPath xpath, Map<String, String> etiquetas)
            throws Exception {
        ContextoPdf ctx = new ContextoPdf(documento, xpath, etiquetas);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4, 36, 36, 42, 48);
        PdfWriter writer = PdfWriter.getInstance(pdf, salida);
        writer.setPageEvent(new NumeracionPaginas());
        pdf.open();

        NodeList secciones = (NodeList) xpath.evaluate(
                "//*[local-name()='view']//*[local-name()='section' and @id]",
                documento,
                XPathConstants.NODESET
        );

        boolean contenido = false;
        for (int i = 0; i < secciones.getLength(); i++) {
            org.w3c.dom.Element seccion = (org.w3c.dom.Element) secciones.item(i);
            if (!esSeccionImprimible(seccion, ctx)) {
                continue;
            }
            contenido |= renderizarSeccion(pdf, seccion, ctx);
        }

        if (!contenido) {
            pdf.add(new Paragraph("No se detectaron secciones imprimibles en el XML.", ctx.valorFont));
        }

        pdf.close();
        return salida.toByteArray();
    }

    private boolean renderizarSeccion(Document pdf, org.w3c.dom.Element seccion, ContextoPdf ctx)
            throws Exception {
        String bindSeccion = seccion.getAttribute("bind");
        if (!bindSeccion.isBlank() && !ctx.esVisibleEnPdf(bindSeccion)) {
            return false;
        }

        String titulo = ctx.resolverTituloSeccion(seccion);
        boolean esTituloPrograma = "titulo-section".equals(seccion.getAttribute("id"));

        if (titulo != null && !titulo.isBlank()) {
            if (esTituloPrograma) {
                Paragraph p = new Paragraph(titulo.toUpperCase(), ctx.tituloProgramaFont);
                p.setAlignment(Element.ALIGN_CENTER);
                p.setSpacingAfter(4);
                pdf.add(p);
            } else {
                agregarBarraSeccion(pdf, titulo, ctx);
            }
        }

        boolean algo = false;
        List<org.w3c.dom.Element> hijos = hijosDirectos(seccion);
        for (org.w3c.dom.Element hijo : hijos) {
            String nombre = hijo.getLocalName();
            if ("grid".equals(nombre)) {
                if (hijo.hasAttribute("repeat")) {
                    continue;
                }
                algo |= renderizarRejilla(pdf, hijo, ctx);
            }
        }
        return algo;
    }

    private void agregarBarraSeccion(Document pdf, String titulo, ContextoPdf ctx) throws Exception {
        PdfPTable barra = new PdfPTable(1);
        barra.setWidthPercentage(100);
        barra.setSpacingBefore(8);
        barra.setSpacingAfter(4);
        PdfPCell celda = new PdfPCell(new Phrase(titulo.toUpperCase(), ctx.seccionFont));
        celda.setBackgroundColor(new Color(225, 225, 225));
        celda.setBorder(Rectangle.BOX);
        celda.setBorderColor(new Color(160, 160, 160));
        celda.setPadding(5);
        celda.setHorizontalAlignment(Element.ALIGN_LEFT);
        barra.addCell(celda);
        pdf.add(barra);
    }

    private boolean renderizarRejilla(Document pdf, org.w3c.dom.Element grid, ContextoPdf ctx)
            throws Exception {
        Map<Integer, List<org.w3c.dom.Element>> filas = new TreeMap<>();
        for (org.w3c.dom.Element celda : hijosDirectos(grid)) {
            if (!"c".equals(celda.getLocalName())) {
                continue;
            }
            int y = parseEntero(celda.getAttribute("y"), 1);
            filas.computeIfAbsent(y, k -> new ArrayList<>()).add(celda);
        }

        boolean algo = false;
        for (List<org.w3c.dom.Element> fila : filas.values()) {
            fila.sort((a, b) -> Integer.compare(
                    parseEntero(a.getAttribute("x"), 1),
                    parseEntero(b.getAttribute("x"), 1)
            ));
            boolean filaSoloExplicacion = esFilaDeclaracionConExplicacion(fila);
            PdfPTable tabla = new PdfPTable(COLUMNAS_REJILLA);
            tabla.setWidthPercentage(100);
            tabla.setSpacingAfter(2);
            boolean filaConContenido = false;

            for (org.w3c.dom.Element celda : fila) {
                int ancho = Math.min(COLUMNAS_REJILLA, Math.max(1, parseEntero(celda.getAttribute("w"), 1)));
                PdfPCell celdaPdf = construirCeldaRejilla(celda, ctx, filaSoloExplicacion);
                if (!esCeldaVacia(celdaPdf)) {
                    filaConContenido = true;
                }
                celdaPdf.setColspan(ancho);
                tabla.addCell(celdaPdf);
            }

            int ocupadas = fila.stream()
                    .mapToInt(c -> Math.min(COLUMNAS_REJILLA, Math.max(1, parseEntero(c.getAttribute("w"), 1))))
                    .sum();
            while (ocupadas < COLUMNAS_REJILLA) {
                PdfPCell vacia = celdaVacia();
                vacia.setColspan(COLUMNAS_REJILLA - ocupadas);
                tabla.addCell(vacia);
                ocupadas = COLUMNAS_REJILLA;
            }

            if (filaConContenido) {
                pdf.add(tabla);
                algo = true;
            }
        }
        return algo;
    }

    private boolean esFilaDeclaracionConExplicacion(List<org.w3c.dom.Element> fila) {
        boolean explicacionAncha = false;
        boolean checkboxEstrecho = false;
        for (org.w3c.dom.Element celda : fila) {
            int ancho = parseEntero(celda.getAttribute("w"), 1);
            for (org.w3c.dom.Element control : controlesEnCelda(celda)) {
                String tipo = control.getLocalName();
                if ("explanation".equals(tipo) && ancho >= 10) {
                    explicacionAncha = true;
                }
                if ("checkbox-input".equals(tipo) && ancho <= 1) {
                    checkboxEstrecho = true;
                }
            }
        }
        return explicacionAncha && checkboxEstrecho;
    }

    private PdfPCell construirCeldaRejilla(org.w3c.dom.Element celdaGrid, ContextoPdf ctx, boolean filaSoloExplicacion)
            throws Exception {
        List<org.w3c.dom.Element> controles = controlesEnCelda(celdaGrid);
        if (controles.isEmpty()) {
            return celdaVacia();
        }

        PdfPCell celda = new PdfPCell();
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(2);
        celda.setPaddingBottom(4);
        celda.setVerticalAlignment(Element.ALIGN_TOP);

        boolean conContenido = false;
        for (org.w3c.dom.Element control : controles) {
            if (filaSoloExplicacion && "checkbox-input".equals(control.getLocalName())) {
                continue;
            }
            if (!esControlImprimible(control, ctx)) {
                continue;
            }
            if (agregarControlACelda(celda, control, ctx)) {
                conContenido = true;
            }
        }

        if (!conContenido) {
            return celdaVacia();
        }
        return celda;
    }

    private boolean agregarControlACelda(PdfPCell celda, org.w3c.dom.Element control, ContextoPdf ctx)
            throws Exception {
        String tipo = control.getLocalName();

        if ("explanation".equals(tipo)) {
            String html = ctx.obtenerTextoHtmlControl(control);
            if (!html.isBlank()) {
                Paragraph p = new Paragraph(html, ctx.textoFont);
                p.setLeading(11f);
                p.setSpacingAfter(3);
                celda.addElement(p);
                return true;
            }
            return false;
        }

        String clave = ctx.claveRecurso(control);
        RecursoFormulario recurso = ctx.recursos.get(clave);
        if (recurso != null && esCampoOculto(recurso)) {
            return false;
        }

        String etiqueta = ctx.obtenerLabel(control);
        if (etiqueta.isBlank()) {
            etiqueta = clave;
        }
        if (esEtiquetaInterna(etiqueta, clave)) {
            etiqueta = "";
        }

        if ("checkbox-input".equals(tipo)) {
            boolean marcado = ctx.valorBooleano(control);
            Paragraph p = new Paragraph();
            p.add(new Phrase((marcado ? "☑ " : "☐ ") + etiqueta, ctx.textoFont));
            p.setSpacingAfter(2);
            celda.addElement(p);
            return true;
        }

        if ("select1".equals(tipo) || "select".equals(tipo)) {
            String appearance = control.getAttribute("appearance");
            if ("full".equals(appearance)) {
                celda.addElement(renderizarSelectHorizontal(control, recurso, ctx, etiqueta));
                return true;
            }
        }

        if (!etiqueta.isBlank()) {
            Paragraph pLabel = new Paragraph(etiqueta, ctx.labelFont);
            pLabel.setSpacingAfter(1);
            celda.addElement(pLabel);
        }

        String valor = ctx.obtenerValorMostrar(control, recurso);
        Paragraph pValor = new Paragraph(valor.isBlank() ? " " : valor, ctx.valorFont);
        pValor.setSpacingAfter(2);
        celda.addElement(pValor);

        if (recurso != null && recurso.getHint() != null && !recurso.getHint().isBlank()
                && valor.isBlank()
                && !esHintFormato(recurso.getHint())
                && !esCampoOculto(recurso)) {
            Paragraph hint = new Paragraph(recurso.getHint(), ctx.hintFont);
            hint.setSpacingAfter(2);
            celda.addElement(hint);
        }
        return true;
    }

    private Paragraph renderizarSelectHorizontal(org.w3c.dom.Element control, RecursoFormulario recurso,
                                                  ContextoPdf ctx, String etiqueta) throws Exception {
        String valorActual = ctx.valorInstancia(control).trim();
        List<ItemSelect> items = recurso != null ? recurso.getItems() : List.of();
        Paragraph p = new Paragraph();
        p.setLeading(11f);
        if (!etiqueta.isBlank()) {
            p.add(new Phrase(etiqueta + "  ", ctx.textoFont));
        }
        for (ItemSelect item : items) {
            boolean sel = valorActual.equalsIgnoreCase(item.getValue() != null ? item.getValue().trim() : "");
            String texto = stripHtml(item.getLabel() != null ? item.getLabel() : item.getValue());
            Font f = sel ? ctx.opcionSelFont : ctx.opcionFont;
            p.add(new Phrase((sel ? "▪ " : "  ") + texto + "    ", f));
        }
        p.setSpacingAfter(3);
        return p;
    }

    private boolean esHintFormato(String hint) {
        return hint != null && hint.trim().toLowerCase().startsWith("formato:");
    }

    private boolean esEtiquetaInterna(String etiqueta, String clave) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return true;
        }
        if (etiqueta.equals(clave)) {
            return true;
        }
        return etiqueta.matches("^[\\w]+-[\\w]+$") && !etiqueta.contains(" ");
    }

    private boolean esCampoOculto(RecursoFormulario recurso) {
        String hint = recurso.getHint();
        return hint != null && hint.toLowerCase().contains("no visible");
    }

    private boolean esSeccionImprimible(org.w3c.dom.Element seccion, ContextoPdf ctx) {
        String idSeccion = seccion.getAttribute("id");
        if (SECCIONES_OCULTAS_PDF.contains(idSeccion)) {
            return false;
        }
        if (tieneClase(seccion, "noprintinpdf") || esAncestroNoPrint(seccion)) {
            return false;
        }
        String bind = seccion.getAttribute("bind");
        return bind.isBlank() || ctx.esVisibleEnPdf(bind);
    }

    private boolean esControlImprimible(org.w3c.dom.Element control, ContextoPdf ctx) {
        if (tieneClase(control, "noprintinpdf") || esAncestroNoPrint(control)) {
            return false;
        }
        String tipo = control.getLocalName();
        if (!TIPOS_CONTROL.contains(tipo) && !control.hasAttribute("bind")) {
            return false;
        }
        String bind = control.getAttribute("bind");
        return bind.isBlank() || ctx.esVisibleEnPdf(bind);
    }

    private List<org.w3c.dom.Element> controlesEnCelda(org.w3c.dom.Element celda) {
        List<org.w3c.dom.Element> lista = new ArrayList<>();
        NodeList todos = celda.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < todos.getLength(); i++) {
            if (!(todos.item(i) instanceof org.w3c.dom.Element el) || !el.hasAttribute("id")) {
                continue;
            }
            String tipo = el.getLocalName();
            if (TIPOS_CONTROL.contains(tipo) || ("number".equals(tipo) && el.hasAttribute("bind"))) {
                if (esDescendienteDirectoDeCelda(celda, el)) {
                    lista.add(el);
                }
            }
        }
        return lista;
    }

    private boolean esDescendienteDirectoDeCelda(org.w3c.dom.Element celda, org.w3c.dom.Element control) {
        Node padre = control.getParentNode();
        while (padre instanceof org.w3c.dom.Element el) {
            if (el == celda) {
                return true;
            }
            if ("c".equals(el.getLocalName()) && el != celda) {
                return false;
            }
            padre = padre.getParentNode();
        }
        return false;
    }

    private PdfPCell celdaVacia() {
        PdfPCell celda = new PdfPCell(new Phrase(" "));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0);
        return celda;
    }

    private boolean esCeldaVacia(PdfPCell celda) {
        return celda.getCompositeElements() == null || celda.getCompositeElements().isEmpty();
    }

    private static int parseEntero(String texto, int defecto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (Exception e) {
            return defecto;
        }
    }

    private static List<org.w3c.dom.Element> hijosDirectos(org.w3c.dom.Element padre) {
        List<org.w3c.dom.Element> lista = new ArrayList<>();
        NodeList hijos = padre.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            if (hijos.item(i) instanceof org.w3c.dom.Element el) {
                lista.add(el);
            }
        }
        return lista;
    }

    private static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html
                .replace("&#xA;", "\n")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n")
                .replaceAll("(?is)</div>", "\n")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private boolean tieneClase(org.w3c.dom.Element elemento, String clase) {
        String clases = elemento.getAttribute("class");
        return clases != null && clases.contains(clase);
    }

    private boolean esAncestroNoPrint(org.w3c.dom.Element elemento) {
        Node padre = elemento.getParentNode();
        while (padre instanceof org.w3c.dom.Element elPadre) {
            if ("section".equals(elPadre.getLocalName()) && tieneClase(elPadre, "noprintinpdf")) {
                return true;
            }
            padre = padre.getParentNode();
        }
        return false;
    }

    private org.w3c.dom.Document parsearDocumento(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    /** Contexto de renderizado: recursos, instancia, binds y fuentes. */
    private static final class ContextoPdf {
        final Map<String, RecursoFormulario> recursos;
        final Map<String, String> bindToRef;
        final Map<String, String> bindRelevant;
        final Map<String, String> valoresInstancia;
        final Map<String, String> etiquetasLegibles;
        final Font tituloProgramaFont;
        final Font seccionFont;
        final Font labelFont;
        final Font valorFont;
        final Font textoFont;
        final Font hintFont;
        final Font opcionFont;
        final Font opcionSelFont;
        private final XPath xpath;
        private final org.w3c.dom.Document documento;

        ContextoPdf(org.w3c.dom.Document documento, XPath xpath, Map<String, String> etiquetas) throws Exception {
            this.documento = documento;
            this.xpath = xpath;
            this.etiquetasLegibles = etiquetas != null ? etiquetas : Map.of();
            this.recursos = OrbeonResourceParser.extraerRecursos(documento, xpath);
            this.bindToRef = construirMapaBindARef(documento, xpath);
            this.bindRelevant = construirMapaBindRelevant(documento, xpath);
            this.valoresInstancia = construirMapaValoresInstancia(documento, xpath);

            BaseFont base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont baseBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            tituloProgramaFont = new Font(baseBold, 11, Font.NORMAL, Color.BLACK);
            seccionFont = new Font(baseBold, 9, Font.NORMAL, Color.BLACK);
            labelFont = new Font(baseBold, 8, Font.NORMAL, new Color(60, 60, 60));
            valorFont = new Font(base, 9, Font.NORMAL, Color.BLACK);
            textoFont = new Font(base, 8, Font.NORMAL, Color.BLACK);
            hintFont = new Font(base, 7, Font.ITALIC, new Color(120, 120, 120));
            opcionFont = new Font(base, 8, Font.NORMAL, new Color(80, 80, 80));
            opcionSelFont = new Font(baseBold, 8, Font.NORMAL, Color.BLACK);
        }

        boolean esVisibleEnPdf(String bindId) {
            String relevant = bindRelevant.get(bindId);
            if (relevant == null || relevant.isBlank()) {
                return true;
            }
            return evaluarRelevant(relevant.trim().replaceAll("\\s+", " "));
        }

        private boolean evaluarRelevant(String r) {
            if ("false()".equals(r)) {
                return false;
            }
            if ("true()".equals(r)) {
                return true;
            }
            if (r.contains("fr:mode()!='pdf'") || r.contains("fr:mode() != 'pdf'")) {
                return false;
            }
            if (r.contains("fr:mode()='pdf'")) {
                return true;
            }
            if (r.contains(" and ")) {
                for (String parte : r.split(" and ")) {
                    if (!evaluarRelevant(parte.trim())) {
                        return false;
                    }
                }
                return true;
            }
            if (r.contains(" or ")) {
                for (String parte : r.split(" or ")) {
                    if (evaluarRelevant(parte.trim())) {
                        return true;
                    }
                }
                return false;
            }
            if (r.startsWith("xxf:valid(")) {
                return true;
            }
            Matcher nonBlank = NON_BLANK_RELEVANT.matcher(r);
            if (nonBlank.find()) {
                String var = nonBlank.group(1);
                return !valoresInstancia.getOrDefault(var, "").isBlank();
            }
            Matcher desigual = DESIGUALDAD_RELEVANT.matcher(r);
            if (desigual.find()) {
                String var = desigual.group(1);
                String esperado = desigual.group(2);
                String actual = valoresInstancia.getOrDefault(var, "");
                return !esperado.equals(actual);
            }
            Matcher igual = IGUALDAD_RELEVANT.matcher(r);
            if (igual.find()) {
                String var = igual.group(1);
                String esperado = igual.group(2);
                String actual = valoresInstancia.getOrDefault(var, "");
                return esperado.equals(actual);
            }
            return !r.startsWith("false()");
        }

        String resolverTituloSeccion(org.w3c.dom.Element seccion) throws Exception {
            NodeList labels = seccion.getElementsByTagNameNS("*", "label");
            for (int i = 0; i < labels.getLength(); i++) {
                if (!(labels.item(i) instanceof org.w3c.dom.Element labelEl)) {
                    continue;
                }
                String ref = labelEl.getAttribute("ref");
                Matcher matcher = REF_RECURSO.matcher(ref);
                if (matcher.find()) {
                    RecursoFormulario rec = recursos.get(matcher.group(1));
                    if (rec != null && rec.getLabel() != null && !rec.getLabel().isBlank()) {
                        return rec.getLabel();
                    }
                }
            }
            String id = seccion.getAttribute("id");
            return id.replace("-section", "").replace("-", " ");
        }

        String claveRecurso(org.w3c.dom.Element control) {
            String id = control.getAttribute("id");
            if (id.endsWith("-control")) {
                return id.substring(0, id.length() - "-control".length());
            }
            String bind = control.getAttribute("bind");
            if (bind.endsWith("-bind")) {
                return bind.substring(0, bind.length() - "-bind".length());
            }
            return id;
        }

        String obtenerLabel(org.w3c.dom.Element control) throws Exception {
            RecursoFormulario rec = recursos.get(claveRecurso(control));
            if (rec != null && rec.getLabel() != null && !rec.getLabel().isBlank()) {
                return stripHtml(rec.getLabel());
            }
            return "";
        }

        String obtenerTextoHtmlControl(org.w3c.dom.Element control) throws Exception {
            NodeList textos = control.getElementsByTagNameNS("*", "text");
            for (int i = 0; i < textos.getLength(); i++) {
                if (!(textos.item(i) instanceof org.w3c.dom.Element textEl)) {
                    continue;
                }
                String ref = textEl.getAttribute("ref");
                Matcher m = REF_RECURSO.matcher(ref);
                if (m.find()) {
                    String clave = m.group(1);
                    String raw = obtenerTextoRecursoCampo(clave, "text");
                    return stripHtml(raw);
                }
            }
            return "";
        }

        String obtenerValorMostrar(org.w3c.dom.Element control, RecursoFormulario recurso) {
            String bind = control.getAttribute("bind");
            String ref = bind.isBlank() ? "" : bindToRef.getOrDefault(bind, "");
            if (!ref.isBlank() && etiquetasLegibles.containsKey(ref)) {
                return etiquetasLegibles.get(ref);
            }
            String clave = claveRecurso(control);
            if (etiquetasLegibles.containsKey(clave)) {
                return etiquetasLegibles.get(clave);
            }

            String valor = valorInstancia(control).trim();
            if (valor.isEmpty() || esValorHint(valor, recurso)) {
                return "";
            }
            if (recurso != null && recurso.getItems() != null && !recurso.getItems().isEmpty()) {
                for (ItemSelect item : recurso.getItems()) {
                    if (valor.equalsIgnoreCase(item.getValue() != null ? item.getValue().trim() : "")) {
                        return stripHtml(item.getLabel() != null ? item.getLabel() : valor);
                    }
                }
            }
            if ("true".equalsIgnoreCase(valor) || "false".equalsIgnoreCase(valor)) {
                return "true".equalsIgnoreCase(valor) ? "Sí" : "No";
            }
            return valor;
        }

        private boolean esValorHint(String valor, RecursoFormulario recurso) {
            if (valor.toLowerCase().startsWith("formato:")) {
                return true;
            }
            if (recurso != null && recurso.getHint() != null) {
                String hint = stripHtml(recurso.getHint()).trim();
                if (!hint.isBlank() && hint.equalsIgnoreCase(valor.trim())) {
                    return true;
                }
            }
            return false;
        }

        boolean valorBooleano(org.w3c.dom.Element control) {
            String v = valorInstancia(control).trim();
            return "true".equalsIgnoreCase(v) || "1".equals(v);
        }

        String valorInstancia(org.w3c.dom.Element control) {
            String bind = control.getAttribute("bind");
            if (bind.isBlank()) {
                return "";
            }
            String ref = bindToRef.get(bind);
            if (ref == null) {
                ref = bind.endsWith("-bind") ? bind.substring(0, bind.length() - "-bind".length()) : bind;
            }
            return valoresInstancia.getOrDefault(ref, "");
        }

        private String obtenerTextoRecursoCampo(String claveRecurso, String campo) throws Exception {
            String expresion = String.format(
                    "//*[local-name()='instance' and @id='fr-form-resources']" +
                            "//*[local-name()='resource']/*[local-name()='%s']/*[local-name()='%s']",
                    escaparXPathLiteral(claveRecurso), escaparXPathLiteral(campo)
            );
            Node nodo = (Node) xpath.evaluate(expresion, documento, XPathConstants.NODE);
            return nodo != null ? nodo.getTextContent().trim() : "";
        }

        private static Map<String, String> construirMapaBindARef(org.w3c.dom.Document documento, XPath xpath)
                throws Exception {
            Map<String, String> mapa = new LinkedHashMap<>();
            NodeList binds = (NodeList) xpath.evaluate(
                    "//*[local-name()='bind' and @id and @ref]", documento, XPathConstants.NODESET
            );
            for (int i = 0; i < binds.getLength(); i++) {
                org.w3c.dom.Element bind = (org.w3c.dom.Element) binds.item(i);
                mapa.put(bind.getAttribute("id"), bind.getAttribute("ref"));
            }
            return mapa;
        }

        private static Map<String, String> construirMapaBindRelevant(org.w3c.dom.Document documento, XPath xpath)
                throws Exception {
            Map<String, String> mapa = new LinkedHashMap<>();
            NodeList binds = (NodeList) xpath.evaluate(
                    "//*[local-name()='bind' and @id and @relevant]", documento, XPathConstants.NODESET
            );
            for (int i = 0; i < binds.getLength(); i++) {
                org.w3c.dom.Element bind = (org.w3c.dom.Element) binds.item(i);
                mapa.put(bind.getAttribute("id"), bind.getAttribute("relevant"));
            }
            return mapa;
        }

        private static Map<String, String> construirMapaValoresInstancia(org.w3c.dom.Document documento, XPath xpath)
                throws Exception {
            Map<String, String> mapa = new LinkedHashMap<>();
            NodeList nodos = (NodeList) xpath.evaluate(
                    "//*[local-name()='instance' and @id='fr-form-instance']//*[not(*)]",
                    documento,
                    XPathConstants.NODESET
            );
            for (int i = 0; i < nodos.getLength(); i++) {
                if (nodos.item(i) instanceof org.w3c.dom.Element el) {
                    String nombre = el.getLocalName();
                    String texto = el.getTextContent() != null ? el.getTextContent().trim() : "";
                    mapa.put(nombre, texto);
                }
            }
            return mapa;
        }

        private static String escaparXPathLiteral(String valor) {
            if (valor == null) {
                return "";
            }
            if (!valor.contains("'")) {
                return valor;
            }
            return valor.replace("'", "', \"'\", '");
        }
    }

    private static final class NumeracionPaginas extends PdfPageEventHelper {
        private PdfTemplate plantillaTotal;
        private final Font fontPie = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        @Override
        public void onOpenDocument(com.lowagie.text.pdf.PdfWriter writer, Document document) {
            plantillaTotal = writer.getDirectContent().createTemplate(24, 12);
        }

        @Override
        public void onEndPage(com.lowagie.text.pdf.PdfWriter writer, Document document) {
            PdfPTable tabla = new PdfPTable(1);
            tabla.setTotalWidth(document.right() - document.left());
            tabla.setLockedWidth(true);
            PdfPCell celda = new PdfPCell();
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
            celda.setPadding(0);
            Paragraph p = new Paragraph(writer.getPageNumber() + " / ", fontPie);
            try {
                p.add(new Chunk(Image.getInstance(plantillaTotal), 0, 0));
            } catch (Exception ignored) {
                // Si falla la plantilla, se muestra al menos la página actual.
            }
            celda.addElement(p);
            tabla.addCell(celda);
            tabla.writeSelectedRows(0, -1, document.left(), document.bottom() - 10, writer.getDirectContent());
        }

        @Override
        public void onCloseDocument(com.lowagie.text.pdf.PdfWriter writer, Document document) {
            int total = Math.max(1, writer.getPageNumber() - 1);
            ColumnText.showTextAligned(
                    plantillaTotal,
                    Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(total), fontPie),
                    0, 0, 0
            );
        }
    }

    // --- API legada usada en tests / compatibilidad ---

    List<SeccionFormulario> parsearSeccionesPdf(org.w3c.dom.Document documento, XPath xpath) throws Exception {
        ContextoPdf ctx = new ContextoPdf(documento, xpath, Map.of());
        NodeList seccionesNodo = (NodeList) xpath.evaluate(
                "//*[local-name()='view']//*[local-name()='section' and @id]",
                documento,
                XPathConstants.NODESET
        );
        List<SeccionFormulario> secciones = new ArrayList<>();
        for (int i = 0; i < seccionesNodo.getLength(); i++) {
            org.w3c.dom.Element seccion = (org.w3c.dom.Element) seccionesNodo.item(i);
            if (!esSeccionImprimible(seccion, ctx)) {
                continue;
            }
            String id = seccion.getAttribute("id");
            String titulo = ctx.resolverTituloSeccion(seccion);
            SeccionFormulario sf = new SeccionFormulario(id, titulo);
            for (org.w3c.dom.Element grid : hijosDirectos(seccion)) {
                if (!"grid".equals(grid.getLocalName()) || grid.hasAttribute("repeat")) {
                    continue;
                }
                for (org.w3c.dom.Element celda : hijosDirectos(grid)) {
                    for (org.w3c.dom.Element control : controlesEnCelda(celda)) {
                        if (esControlImprimible(control, ctx)) {
                            sf.getComponentes().add(crearComponenteResumen(control, ctx));
                        }
                    }
                }
            }
            if (!sf.getComponentes().isEmpty()) {
                secciones.add(sf);
            }
        }
        return secciones;
    }

    private ComponenteFormulario crearComponenteResumen(org.w3c.dom.Element control, ContextoPdf ctx) {
        String id = control.getAttribute("id");
        String tipo = control.getLocalName();
        String clave = ctx.claveRecurso(control);
        RecursoFormulario rec = ctx.recursos.get(clave);
        String label = rec != null ? rec.getLabel() : clave;
        String hint = rec != null ? rec.getHint() : "";
        ComponenteFormulario c = new ComponenteFormulario(id, tipo, label, hint);
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("resourceKey", clave);
        meta.put("valorInstancia", ctx.valorInstancia(control));
        c.setMetadatos(meta);
        return c;
    }
}
