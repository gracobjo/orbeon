package com.orbeon.editor.service;

import com.orbeon.editor.model.ComponenteFormulario;
import com.orbeon.editor.model.EstructuraFormulario;
import com.orbeon.editor.model.ImagenFormulario;
import com.orbeon.editor.model.LogoEnFormulario;
import com.orbeon.editor.model.SeccionFormulario;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detecta logos/imágenes en el formulario y calcula su posición en la vista.
 */
@Service
public class OrbeonLogoService {

    private static final Set<String> TIPOS_IMAGEN = Set.of(
            "image", "static-attachment", "image-attachment"
    );

    private final OrbeonStructureService structureService;

    public OrbeonLogoService(OrbeonStructureService structureService) {
        this.structureService = structureService;
    }

    public List<LogoEnFormulario> analizarLogos(String xml) {
        EstructuraFormulario estructura = structureService.parsearEstructuraCompleta(xml);
        Map<String, ImagenFormulario> porTag = indexarImagenesInstancia(estructura.getImagenes());

        List<LogoEnFormulario> logos = new ArrayList<>();
        int posicionGlobal = 0;

        for (SeccionFormulario seccion : estructura.getSecciones()) {
            int posicionEnSeccion = 0;
            for (ComponenteFormulario comp : seccion.getComponentes()) {
                if (!TIPOS_IMAGEN.contains(comp.getTipo())) {
                    continue;
                }
                posicionGlobal++;
                posicionEnSeccion++;

                LogoEnFormulario logo = new LogoEnFormulario();
                logo.setPosicionGlobal(posicionGlobal);
                logo.setPosicionEnSeccion(posicionEnSeccion);
                logo.setControlId(comp.getId());
                logo.setLabel(comp.getLabel());
                logo.setBind(comp.getMetadatos().getOrDefault("bind", ""));

                String resourceKey = comp.getMetadatos().getOrDefault("resourceKey", "");
                String tag = resourceKey;
                if (tag.isBlank() && comp.getId().endsWith("-control")) {
                    tag = comp.getId().substring(0, comp.getId().length() - 8);
                }
                logo.setTag(tag);

                ImagenFormulario inst = porTag.get(tag);
                if (inst != null) {
                    logo.setFilename(inst.getFilename());
                    logo.setMediatype(inst.getMediatype());
                    logo.setSrc(inst.getSrc());
                } else {
                    String ref = comp.getMetadatos().getOrDefault("ref", "");
                    logo.setSrc(ref);
                }

                logo.setSectionId(seccion.getId());
                logo.setSectionTitulo(seccion.getTitulo());
                logos.add(logo);
            }
        }

        // Imágenes en instancia sin control visual (solo adjuntos binarios reales)
        for (ImagenFormulario img : estructura.getImagenes()) {
            if (!esImagenBinaria(img)) {
                continue;
            }
            if (logos.stream().anyMatch(l -> img.getTag().equals(l.getTag()))) {
                continue;
            }
            LogoEnFormulario logo = new LogoEnFormulario();
            logo.setPosicionGlobal(++posicionGlobal);
            logo.setPosicionEnSeccion(0);
            logo.setTag(img.getTag());
            logo.setControlId("(sin control en vista)");
            logo.setFilename(img.getFilename());
            logo.setMediatype(img.getMediatype());
            logo.setSrc(img.getSrc());
            logo.setSectionId("(solo instancia)");
            logo.setSectionTitulo("Datos en fr-form-instance");
            logos.add(logo);
        }

        return logos;
    }

    public String describirLogos(String xml) {
        List<LogoEnFormulario> logos = analizarLogos(xml);
        if (logos.isEmpty()) {
            return "El formulario no contiene logos ni imágenes detectadas.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("El formulario tiene ").append(logos.size())
                .append(logos.size() == 1 ? " logo/imagen:\n" : " logos/imágenes:\n");
        for (LogoEnFormulario l : logos) {
            sb.append("  ").append(l.getPosicionGlobal()).append(". ");
            sb.append("tag=").append(l.getTag());
            sb.append(", control=").append(l.getControlId());
            if (l.getPosicionEnSeccion() > 0) {
                sb.append(", posición en sección=").append(l.getPosicionEnSeccion());
            }
            sb.append(", sección=\"").append(l.getSectionTitulo()).append("\" (").append(l.getSectionId()).append(")");
            if (l.getFilename() != null && !l.getFilename().isBlank()) {
                sb.append(", archivo=").append(l.getFilename());
            }
            if (l.getSrc() != null && !l.getSrc().isBlank()) {
                String src = l.getSrc();
                sb.append(", src=").append(src.length() > 60 ? src.substring(0, 60) + "…" : src);
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private Map<String, ImagenFormulario> indexarImagenesInstancia(List<ImagenFormulario> imagenes) {
        Map<String, ImagenFormulario> mapa = new HashMap<>();
        for (ImagenFormulario img : imagenes) {
            mapa.put(img.getTag(), img);
        }
        return mapa;
    }

    private boolean esImagenBinaria(ImagenFormulario img) {
        String mt = img.getMediatype();
        if (mt != null && mt.startsWith("image/")) {
            return true;
        }
        String fn = img.getFilename();
        if (fn != null && fn.matches("(?i).+\\.(png|jpg|jpeg|gif|svg|webp|bin)$")) {
            return true;
        }
        String src = img.getSrc();
        return src != null && (src.contains("/persistence/") || src.endsWith(".bin"));
    }
}
