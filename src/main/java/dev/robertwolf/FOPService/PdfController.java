package dev.robertwolf.FOPService;

import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;

import org.xml.sax.SAXException;

@RestController
public class PdfController {

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
            FopFactory fopFactory = createFopFactory();
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

    private FopFactory createFopFactory() throws IOException, SAXException {
        String configPath = System.getenv("FOP_CONFIG_PATH");
        String resourceBase = System.getenv("FOP_RESOURCE_BASE");

        URI baseUri = resourceBase != null && !resourceBase.isBlank()
                ? new File(resourceBase).toURI()
                : new File(".").toURI();

        if (configPath != null && !configPath.isBlank()) {
            File configFile = new File(configPath);
            if (!configFile.isFile()) {
                throw new FileNotFoundException("FOP configuration file not found at " + configPath);
            }
            FopFactory fopFactory = FopFactory.newInstance(configFile);
            fopFactory.setBaseURI(baseUri.toString());
            return fopFactory;
        }

        return FopFactory.newInstance(baseUri);
    }
}
