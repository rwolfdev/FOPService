package dev.robertwolf.FOPService;

import org.apache.fop.apps.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@RestController
public class PdfController {

    private final Path fontStorageDir = Paths.get("/data/fonts");
    private final FopFactory fopFactory;
    private final TransformerFactory transformerFactory;

    public PdfController() {
        try {
            Files.createDirectories(fontStorageDir);

            /* ---------- Secure TransformerFactory ---------- */
            transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            /* ---------- Initialize FOP once ---------- */
            InputStream fopConfigStream = buildFopConfiguration();
            fopFactory = FopFactory.newInstance(new File(".").toURI(), fopConfigStream);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize PDF controller", e);
        }
    }

    /* ============================================================
       FONT UPLOAD
       ============================================================ */

    @PostMapping(value = "/fonts/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFonts(@RequestPart("fonts") MultipartFile[] fontFiles) {
        if (fontFiles == null || fontFiles.length == 0) {
            return ResponseEntity.badRequest().body("No font files provided.");
        }

        int storedFonts = 0;

        for (MultipartFile fontFile : fontFiles) {
            if (fontFile.isEmpty() || fontFile.getOriginalFilename() == null) {
                continue;
            }

            try {
                Path target = fontStorageDir.resolve(
                        Path.of(fontFile.getOriginalFilename()).getFileName()
                );
                Files.write(target, fontFile.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                storedFonts++;
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to store font '" + fontFile.getOriginalFilename() + "': " + e.getMessage());
            }
        }

        if (storedFonts == 0) {
            return ResponseEntity.badRequest().body("No valid font files were uploaded.");
        }

        return ResponseEntity.ok("Stored " + storedFonts + " font file(s).");
    }

    /* ============================================================
       RENDER ENDPOINTS
       ============================================================ */

    @PostMapping(value = "/render/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> renderFromFiles(
            @RequestPart("xml") MultipartFile xmlFile,
            @RequestPart("xsl") MultipartFile xslFile) {

        try (InputStream xml = xmlFile.getInputStream();
             InputStream xsl = xslFile.getInputStream()) {

            return generatePdf(xml, xsl);

        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(("Error reading input files: " + e.getMessage())
                            .getBytes(StandardCharsets.UTF_8));
        }
    }

    @PostMapping(value = "/render/strings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> renderFromStrings(
            @RequestPart("xml") String xmlString,
            @RequestPart("xsl") String xslString) {

        InputStream xml = new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8));
        InputStream xsl = new ByteArrayInputStream(xslString.getBytes(StandardCharsets.UTF_8));
        return generatePdf(xml, xsl);
    }

    /* ============================================================
       CORE PDF GENERATION
       ============================================================ */

    private ResponseEntity<byte[]> generatePdf(InputStream xmlStream, InputStream xslStream) {
        try {
            /* ---------- XML + XSL → XSL-FO ---------- */
            ByteArrayOutputStream foBuffer = new ByteArrayOutputStream();

            Transformer xsltTransformer =
                    transformerFactory.newTransformer(new StreamSource(xslStream));

            xsltTransformer.transform(
                    new StreamSource(xmlStream),
                    new StreamResult(foBuffer)
            );

            /* ---------- XSL-FO → PDF ---------- */
            ByteArrayOutputStream pdfBuffer = new ByteArrayOutputStream();

            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, pdfBuffer);
            Transformer identityTransformer = transformerFactory.newTransformer();

            identityTransformer.transform(
                    new StreamSource(new ByteArrayInputStream(foBuffer.toByteArray())),
                    new SAXResult(fop.getDefaultHandler())
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBuffer.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("PDF generation failed: " + e.getMessage())
                            .getBytes(StandardCharsets.UTF_8));
        }
    }

    /* ============================================================
       FOP CONFIGURATION
       ============================================================ */

    private InputStream buildFopConfiguration() {
        String fontDir = fontStorageDir.toAbsolutePath().toString();

        String config =
                "<fop version=\"1.0\">" +
                        "<renderers>" +
                        "<renderer mime=\"application/pdf\">" +
                        "<fonts>" +
                        "<directory recursive=\"true\">" + fontDir + "</directory>" +
                        "</fonts>" +
                        "</renderer>" +
                        "</renderers>" +
                        "</fop>";

        return new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8));
    }
}
