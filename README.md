# UrutiBot

An AI-powered chatbot assistant built with Spring Boot and LangChain4j, designed to provide intelligent conversation capabilities and appointment management for UrutiHub Ltd.

## 🚀 Features

- **AI-Powered Chatbot**: Built with LangChain4j and Claude Sonnet 4 for intelligent conversations
- **Appointment Management**: Complete CRUD operations for appointment scheduling and management
- **Memory Persistence**: Conversation history stored in MongoDB for context-aware interactions
- **Email Notifications**: Automated email notifications for appointment confirmations and updates
- **RESTful API**: Comprehensive REST API with Swagger documentation
- **Company Knowledge Base**: Integrated with UrutiHub company information for accurate responses

## 🛠️ Technology Stack

- **Backend**: Spring Boot 3.5.4
- **AI/ML**: LangChain4j 1.0.0-beta1 with Anthropic Claude
- **Database**: MongoDB
- **Documentation**: Swagger/OpenAPI
- **Email**: Spring Mail with Thymeleaf templates
- **Language**: Java 21
- **Build Tool**: Maven

## 📋 Prerequisites

- Java 21 or higher
- Maven 3.6+
- MongoDB instance
- Anthropic API key

## 🔧 Environment Setup

1. **Clone the repository**

   ```bash
   git clone https://github.com/aimelive/urutibot
   cd urutibot
   ```

2. **Set up environment variables**
   Create a `.env` file or set the following environment variables:

   ```bash
   # Database
   DATABASE_URL=mongodb://localhost:27017/urutibot

   # Anthropic API
   ANTHROPIC_API_KEY=your_anthropic_api_key_here

   # Email Configuration
   APP_EMAIL_USERNAME=your_email@gmail.com
   APP_EMAIL_PASSWORD=your_app_password

   # Cors (URLs with comma-separated)
   CORS_ALLOWED_ORIGINS=...
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8080`

---

Once the application is running, you can access the Swagger UI at:
`http://localhost:8080/swagger-ui.html`

### Chatbot Endpoints

#### Send Message

```http
POST /api/chatbot
Content-Type: application/json

{
  "memoryId": "user@example.com",
  "message": "Hello, I need help with a software project"
}
```

## 🏗️ Project Structure

```
src/
├── main/
│   ├── java/com/aimelive/urutibot/
│   │   ├── config/           # Configuration classes
│   │   ├── controller/        # REST API controllers
│   │   ├── dto/              # Data Transfer Objects
│   │   ├── exception/         # Exception handling
│   │   ├── model/            # Entity models
│   │   ├── repository/       # Data access layer
│   │   ├── service/          # Business logic
│   │   └── UrutiBotApplication.java
│   └── resources/
│       ├── application.properties
│       ├── urutihub.txt      # Company information
│       └── templates/        # Email templates
```

## 🐳 Docker

### Image

- Official image name used below: `aimelive/urutibot`

### Required environment variables

- `DATABASE_URL` (MongoDB connection string)
- `ANTHROPIC_API_KEY`
- `APP_EMAIL_USERNAME`, `APP_EMAIL_PASSWORD`
- `CORS_ALLOWED_ORIGINS` (comma separated)
- Optional: `APP_ABOUT_COMPANY_FILE` (defaults to `file:/app/urutihub.txt` baked into the image)

### Quick run (pull latest and run)

```bash
docker run -d --name urutibot -p 8080:8080 \
  -e DATABASE_URL='mongodb+srv://<user>:<pass>@<cluster>/<db>?retryWrites=true&w=majority' \
  -e ANTHROPIC_API_KEY='<your_anthropic_key>' \
  -e APP_EMAIL_USERNAME='<smtp_user>' \
  -e APP_EMAIL_PASSWORD='<smtp_pass>' \
  -e CORS_ALLOWED_ORIGINS='https://your-frontend.example' \
  aimelive/urutibot:latest
```

### Build locally (single-arch)

```bash
docker build -t aimelive/urutibot:local .
```

### Build multi-arch and push (amd64 + arm64)

```bash
docker login -u aimelive

# one-time setup
docker buildx create --name uruti-builder --use || docker buildx use uruti-builder
docker buildx inspect --bootstrap

# build & push
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t aimelive/urutibot:0.0.1 \
  -t aimelive/urutibot:latest \
  --push \
  .
```

### Run with docker-compose

Create a `.env` file in the project root with your secrets, then:

```bash
docker compose up -d --build
```

Notes

- The image includes `/app/urutihub.txt` and defaults `APP_ABOUT_COMPANY_FILE=file:/app/urutihub.txt`.
- To override the file, mount your own and keep the env:
  ```bash
  docker run -d -p 8080:8080 \
    -v /absolute/path/urutihub.txt:/app/urutihub.txt:ro \
    -e APP_ABOUT_COMPANY_FILE='file:/app/urutihub.txt' \
    aimelive/urutibot:latest
  ```

## 📞 Support

For support and questions, contact:

- Email: info@urutihub.com
- Website: www.urutihub.com

---

**UrutiHub Ltd** - Empowering businesses through innovative technology solutions.
