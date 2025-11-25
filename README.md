
# Apache FOP REST Service

This is a minimal REST API for generating PDF documents from XML and XSL templates using [Apache FOP](https://xmlgraphics.apache.org/fop/).  
It is built with Java 21 and Spring Boot, and packaged using a secure distroless Docker image.

---

## 🔧 Features

- Accepts `multipart/form-data` with XML and XSL
- Supports both file uploads and raw string values
- Transforms XML + XSL to XSL-FO via XSLT
- Renders XSL-FO to PDF using Apache FOP
- Returns the generated PDF as HTTP response
- Fully containerized and ready for production

---

## 🚀 API Usage

### Endpoint 1: `/render/files`

Accepts multipart form with uploaded files.

**Request**

```
POST /render/files
Content-Type: multipart/form-data
```

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `xml` | File | ✅ Yes | XML file |
| `xsl` | File | ✅ Yes | XSL stylesheet file |

---

### Endpoint 2: `/render/strings`

Accepts multipart form with raw XML and XSL strings.

**Request**

```
POST /render/strings
Content-Type: multipart/form-data
```

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `xml` | String | ✅ Yes | XML content |
| `xsl` | String | ✅ Yes | XSL stylesheet content |

---

## 📦 Example: Using `curl`

### Upload files

```bash
curl -X POST http://localhost:5000/render/files \
  -F "xml=@test-input.xml" \
  -F "xsl=@test-template.xsl" \
  --output result.pdf
```

### Upload strings

```bash
curl -X POST http://localhost:5000/render/strings \
  -F "xml=<document><title>Hello</title><body>World</body></document>" \
  -F "xsl=$(< test-template.xsl)" \
  --output result.pdf
```

---

## 💻 Example: Using C# (for /render/strings)

```csharp
using System.Net.Http;
using System.Text;

string xml = @"<document><title>Hello</title><body>World</body></document>";
string xsl = File.ReadAllText("test-template.xsl");

using var client = new HttpClient();
var content = new MultipartFormDataContent
{
    { new StringContent(xml, Encoding.UTF8, "application/xml"), "xml" },
    { new StringContent(xsl, Encoding.UTF8, "application/xml"), "xsl" }
};

HttpResponseMessage response = await client.PostAsync("http://localhost:5000/render/strings", content);
byte[] pdfBytes = await response.Content.ReadAsByteArrayAsync();
File.WriteAllBytes("output.pdf", pdfBytes);
```

---

## 🐳 Docker

### Build the image

```bash
docker build -t fopservice .
```

### Run the container

```bash
docker run -p 5000:5000 fopservice --server.port=5000
```

### Mount configuration and resources

You can mount a host directory to provide a custom `fop.xconf` (including fonts, logos, and other resources):

```bash
docker run -p 5000:5000 \
  -v $(pwd)/fop-config:/opt/fop \
  -e FOP_CONFIG_PATH=/opt/fop/config/fop.xconf \
  -e FOP_RESOURCE_BASE=/opt/fop/resources \
  fopservice --server.port=5000
```

Place your `fop.xconf` in `fop-config/config/` and any referenced assets (fonts, images, etc.) under `fop-config/resources/` so they are available to Apache FOP inside the container.

---

## 📂 Project Structure

```
src/
 └── main/
     ├── java/dev/robertwolf/fopservice/
     │    └── PdfController.java
     └── resources/
          └── application.properties

build.gradle
Dockerfile
README.md
```

---

## 📜 License

This project is licensed under the [MIT License](LICENSE).

---

## 👤 Author

**Robert Wolf**  
📧 hello@robertwolf.dev  
🔗 [robertwolf.dev](https://robertwolf.dev)  
🔗 [github.com/rwolfdev](https://github.com/rwolfdev)
