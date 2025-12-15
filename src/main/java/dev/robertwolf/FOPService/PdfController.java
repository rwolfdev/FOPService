package dev.robertwolf.FOPService;

import org.apache.fop.apps.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class PdfController {

    private final Path fontStorageDir = Paths.get("/data/fonts");

    public PdfController() {
        try {
            Files.createDirectories(fontStorageDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize font storage directory", e);
        }
    }

    // Endpoint 3: Upload custom font files to be used automatically during rendering
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
                Path target = fontStorageDir.resolve(Path.of(fontFile.getOriginalFilename()).getFileName());
                Files.write(target, fontFile.getBytes());
                storedFonts++;
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to store font '" + fontFile.getOriginalFilename() + "': " + e.getMessage());
            }
        }

        if (storedFonts == 0) {
            return ResponseEntity.badRequest().body("No valid font files were uploaded.");
        }

        return ResponseEntity.ok("Stored " + storedFonts + " font file(s). They will be used for subsequent renders.");
    }

    // Endpoint 1: Accepts multipart/form-data with file uploads (xml and xsl)
    @PostMapping(value = "/render/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> renderFromFiles(
            @RequestPart("xml") MultipartFile xmlFile,
            @RequestPart("xsl") MultipartFile xslFile) {
        try {
            return generatePdf(xmlFile.getInputStream(), xslFile.getInputStream());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(("Error reading files: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    // Endpoint 2: Accepts multipart/form-data with raw string parts (xml and xsl)
    @PostMapping(value = "/render/strings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> renderFromStrings(
            @RequestPart("xml") String xmlString,
            @RequestPart("xsl") String xslString) {
        InputStream xmlStream = new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8));
        InputStream xslStream = new ByteArrayInputStream(xslString.getBytes(StandardCharsets.UTF_8));
        return generatePdf(xmlStream, xslStream);
    }

    // Internal method: Transforms XML + XSL into PDF using Apache FOP
    private ResponseEntity<byte[]> generatePdf(InputStream xmlStream, InputStream xslStream) {
        try {
            // Step 1: Transform XML + XSL → XSL-FO
            ByteArrayOutputStream foStream = new ByteArrayOutputStream();
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer(new StreamSource(xslStream));
            transformer.transform(new StreamSource(xmlStream), new StreamResult(foStream));

            // Step 2: Transform XSL-FO → PDF using Apache FOP
            FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI(), buildFopConfiguration());
            ByteArrayOutputStream pdfStream = new ByteArrayOutputStream();
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, pdfStream);
            Transformer pdfTransformer = factory.newTransformer();
            pdfTransformer.transform(
                    new StreamSource(new ByteArrayInputStream(foStream.toByteArray())),
                    new SAXResult(fop.getDefaultHandler())
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfStream.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("PDF generation error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private InputStream buildFopConfiguration() throws IOException {
        String fontDir = fontStorageDir.toAbsolutePath().toString();
        String config = "<fop version=\"1.0\">" +
                "<fonts>" +
                "<directory recursive=\"true\">" + fontDir + "</directory>" +
                "</fonts>" +
                "</fop>";
        return new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8));
    }
}
