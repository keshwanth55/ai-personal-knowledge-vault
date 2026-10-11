package com.knowledgevault.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class DocumentTextExtractor {
    public String extract(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".pdf")) {
            try (var document = Loader.loadPDF(file.getBytes())) {
                return new PDFTextStripper().getText(document);
            }
        }
        if (filename.endsWith(".docx")) {
            try (var document = new XWPFDocument(file.getInputStream());
                 var extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        }
        if (filename.endsWith(".pptx")) {
            try (var slides = new XMLSlideShow(file.getInputStream())) {
                return slides.getSlides().stream()
                        .flatMap(slide -> slide.getShapes().stream())
                        .filter(shape -> shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape)
                        .map(shape -> ((org.apache.poi.xslf.usermodel.XSLFTextShape) shape).getText())
                        .collect(java.util.stream.Collectors.joining("\n"));
            }
        }
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
