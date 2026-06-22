package com.orbeon.editor.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.orbeon.editor.dto.CumplimentarInstanciaRequest;
import com.orbeon.editor.dto.ComparacionResponse;
import com.orbeon.editor.dto.FormularioResponse;
import com.orbeon.editor.dto.ModificacionRequest;
import com.orbeon.editor.dto.ModificacionResponse;
import com.orbeon.editor.dto.NaturalLanguageRequest;
import com.orbeon.editor.dto.NaturalLanguageResponse;
import com.orbeon.editor.dto.SincronizarCodigoRequest;
import com.orbeon.editor.dto.VistaPdfRequest;
import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.LogoEnFormulario;
import com.orbeon.editor.model.AnalisisCalculadoras;
import com.orbeon.editor.model.AnalisisDependencias;
import com.orbeon.editor.model.ResultadoCumplimentacion;
import com.orbeon.editor.service.OrbeonCalculatorService;
import com.orbeon.editor.service.OrbeonInstanceService;
import com.orbeon.editor.service.OrbeonCompareService;
import com.orbeon.editor.service.OrbeonDependencyService;
import com.orbeon.editor.service.OrbeonFormService;
import com.orbeon.editor.service.OrbeonLogoService;
import com.orbeon.editor.service.OrbeonModificationService;
import com.orbeon.editor.service.OrbeonNaturalLanguageService;
import com.orbeon.editor.service.OrbeonPdfService;
import com.orbeon.editor.service.OrbeonStructureService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/formulario")
@CrossOrigin("*")
public class FormularioController {

    private final OrbeonFormService orbeonFormService;
    private final OrbeonStructureService orbeonStructureService;
    private final OrbeonModificationService orbeonModificationService;
    private final OrbeonPdfService orbeonPdfService;
    private final OrbeonCompareService orbeonCompareService;
    private final OrbeonNaturalLanguageService naturalLanguageService;
    private final OrbeonLogoService logoService;
    private final OrbeonDependencyService dependencyService;
    private final OrbeonCalculatorService calculatorService;
    private final OrbeonInstanceService instanceService;
    private final ObjectMapper objectMapper;

    public FormularioController(OrbeonFormService orbeonFormService,
                                OrbeonStructureService orbeonStructureService,
                                OrbeonModificationService orbeonModificationService,
                                OrbeonPdfService orbeonPdfService,
                                OrbeonCompareService orbeonCompareService,
                                OrbeonNaturalLanguageService naturalLanguageService,
                                OrbeonLogoService logoService,
                                OrbeonDependencyService dependencyService,
                                OrbeonCalculatorService calculatorService,
                                OrbeonInstanceService instanceService,
                                ObjectMapper objectMapper) {
        this.orbeonFormService = orbeonFormService;
        this.orbeonStructureService = orbeonStructureService;
        this.orbeonModificationService = orbeonModificationService;
        this.orbeonPdfService = orbeonPdfService;
        this.orbeonCompareService = orbeonCompareService;
        this.naturalLanguageService = naturalLanguageService;
        this.logoService = logoService;
        this.dependencyService = dependencyService;
        this.calculatorService = calculatorService;
        this.instanceService = instanceService;
        this.objectMapper = objectMapper;
    }

    private FormularioResponse construirRespuesta(String xml) {
        FormularioResponse resp = new FormularioResponse();
        resp.setXml(xml);
        resp.setComponentes(orbeonFormService.parsearEstructuraDesdeString(xml));
        resp.setEstructura(orbeonStructureService.parsearEstructuraCompleta(xml));
        resp.setDependencias(dependencyService.analizar(xml));
        resp.setCalculadoras(calculatorService.analizar(xml));
        return resp;
    }

    @PostMapping(value = "/cargar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FormularioResponse cargar(@RequestParam("archivo") MultipartFile archivo) {
        try {
            String xml = new String(archivo.getBytes(), StandardCharsets.UTF_8);
            return construirRespuesta(xml);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo cargar el archivo XML: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/sincronizar-codigo", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FormularioResponse sincronizarCodigo(@RequestBody SincronizarCodigoRequest request) {
        try {
            if (request.getXml() == null || request.getXml().isBlank()) {
                throw new IllegalArgumentException("El campo 'xml' es obligatorio");
            }
            String xml = request.getXml();
            return construirRespuesta(xml);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al sincronizar el código XML: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/exportar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> exportar(
            @RequestParam("xmlActual") String xmlActual,
            @RequestParam(value = "modificaciones", required = false) MultipartFile modificaciones) {
        try {
            String xmlFinal = xmlActual;
            if (modificaciones != null && !modificaciones.isEmpty()) {
                JsonNode root = objectMapper.readTree(modificaciones.getBytes());
                if (root.has("changes")) {
                    List<Map<String, Object>> changes = objectMapper.convertValue(
                            root.get("changes"),
                            new TypeReference<List<Map<String, Object>>>() {}
                    );
                    ModificacionResponse mod = orbeonModificationService.aplicarCambios(xmlActual, changes);
                    xmlFinal = mod.getXml();
                } else if (root.isArray()) {
                    List<ComponenteFormulario> lista = objectMapper.convertValue(
                            root, new TypeReference<List<ComponenteFormulario>>() {}
                    );
                    xmlFinal = orbeonFormService.aplicarModificacionesDesdeString(xmlActual, lista);
                }
            }
            byte[] contenido = xmlFinal.getBytes(StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"orbeon_formulario_modificado.xml\"")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(contenido);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al exportar el XML: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/vista-pdf", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> vistaPdf(@RequestBody VistaPdfRequest request) {
        try {
            if (request.getXml() == null || request.getXml().isBlank()) {
                throw new IllegalArgumentException("El campo 'xml' es obligatorio");
            }
            List<ComponenteFormulario> mods = request.getComponentes() != null
                    ? request.getComponentes()
                    : Collections.emptyList();
            String xmlPdf = request.getXml();
            Map<String, String> etiquetas = request.getEtiquetas() != null ? request.getEtiquetas() : Map.of();
            if (request.isCumplimentarEjemplo()) {
                String preset = request.getPresetInstancia() != null && !request.getPresetInstancia().isBlank()
                        ? request.getPresetInstancia() : "instrucciones-684";
                ResultadoCumplimentacion cumpl = instanceService.aplicarPreset(xmlPdf, preset);
                xmlPdf = cumpl.getXml();
                etiquetas = cumpl.getEtiquetas();
            }
            byte[] pdf = orbeonPdfService.generarPdf(xmlPdf, mods, etiquetas);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"orbeon_formulario_vista.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al generar la vista PDF: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/comparar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComparacionResponse comparar(
            @RequestParam("archivoBase") MultipartFile archivoBase,
            @RequestParam("archivoNuevo") MultipartFile archivoNuevo) {
        try {
            String xmlBase = new String(archivoBase.getBytes(), StandardCharsets.UTF_8);
            String xmlNuevo = new String(archivoNuevo.getBytes(), StandardCharsets.UTF_8);
            return orbeonCompareService.comparar(
                    xmlBase,
                    xmlNuevo,
                    archivoBase.getOriginalFilename(),
                    archivoNuevo.getOriginalFilename()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al comparar los XML: " + e.getMessage(), e);
        }
    }

    @GetMapping("/esquema-modificaciones")
    public Map<String, Object> esquemaModificaciones() {
        return orbeonModificationService.obtenerEsquema();
    }

    @PostMapping(value = "/modificar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ModificacionResponse modificar(@RequestBody ModificacionRequest request) {
        try {
            if (request.getXml() == null || request.getXml().isBlank()) {
                throw new IllegalArgumentException("El campo 'xml' es obligatorio");
            }
            if (request.getChanges() == null || request.getChanges().isEmpty()) {
                throw new IllegalArgumentException("El array 'changes' es obligatorio");
            }
            return orbeonModificationService.aplicarCambios(request.getXml(), request.getChanges());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al modificar: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/lenguaje-natural", consumes = MediaType.APPLICATION_JSON_VALUE)
    public NaturalLanguageResponse lenguajeNatural(@RequestBody NaturalLanguageRequest request) {
        try {
            if (request.getXml() == null || request.getXml().isBlank()) {
                throw new IllegalArgumentException("El campo 'xml' es obligatorio");
            }
            if (request.getInstruccion() == null || request.getInstruccion().isBlank()) {
                throw new IllegalArgumentException("El campo 'instruccion' es obligatorio");
            }
            return naturalLanguageService.procesar(
                    request.getXml(),
                    request.getInstruccion(),
                    request.isAplicarCambios()
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al procesar instrucción: " + e.getMessage(), e);
        }
    }

    @PostMapping(value = "/analizar-dependencias", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnalisisDependencias analizarDependencias(@RequestBody SincronizarCodigoRequest request) {
        if (request.getXml() == null || request.getXml().isBlank()) {
            throw new IllegalArgumentException("El campo 'xml' es obligatorio");
        }
        return dependencyService.analizar(request.getXml());
    }

    @PostMapping(value = "/analizar-calculadoras", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnalisisCalculadoras analizarCalculadoras(@RequestBody SincronizarCodigoRequest request) {
        if (request.getXml() == null || request.getXml().isBlank()) {
            throw new IllegalArgumentException("El campo 'xml' es obligatorio");
        }
        return calculatorService.analizar(request.getXml());
    }

    @PostMapping(value = "/cumplimentar-instancia", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FormularioResponse cumplimentarInstancia(@RequestBody CumplimentarInstanciaRequest request) {
        if (request.getXml() == null || request.getXml().isBlank()) {
            throw new IllegalArgumentException("El campo 'xml' es obligatorio");
        }
        ResultadoCumplimentacion resultado;
        if (request.getPreset() != null && !request.getPreset().isBlank()) {
            resultado = instanceService.aplicarPreset(request.getXml(), request.getPreset());
            if (request.getValores() != null && !request.getValores().isEmpty()) {
                resultado = instanceService.aplicarValores(
                        resultado.getXml(), request.getValores(), mergeEtiquetas(resultado, request));
            }
        } else if (request.getValores() != null && !request.getValores().isEmpty()) {
            resultado = instanceService.aplicarValores(
                    request.getXml(), request.getValores(), request.getEtiquetas());
        } else {
            throw new IllegalArgumentException("Indique 'preset' o 'valores' para cumplimentar la instancia");
        }
        return construirRespuesta(resultado.getXml());
    }

    private Map<String, String> mergeEtiquetas(ResultadoCumplimentacion base, CumplimentarInstanciaRequest request) {
        Map<String, String> merged = new LinkedHashMap<>(base.getEtiquetas());
        if (request.getEtiquetas() != null) {
            merged.putAll(request.getEtiquetas());
        }
        return merged;
    }

    @PostMapping(value = "/analizar-logos", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> analizarLogos(@RequestBody SincronizarCodigoRequest request) {
        if (request.getXml() == null || request.getXml().isBlank()) {
            throw new IllegalArgumentException("El campo 'xml' es obligatorio");
        }
        List<LogoEnFormulario> logos = logoService.analizarLogos(request.getXml());
        return Map.of(
                "total", logos.size(),
                "descripcion", logoService.describirLogos(request.getXml()),
                "logos", logos
        );
    }
}
