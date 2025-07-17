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
            FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI());
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
}
