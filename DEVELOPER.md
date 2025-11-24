# Developer Guide

This guide provides detailed instructions on how to build, test, run, package, and deploy the `k-rest-proxy` project.

## Dev Box Prerequisites

To successfully run and develop this project, your development environment (Dev Box) needs the following:

### Hardware
-   **RAM**: Minimum 8GB (16GB recommended) to run the full stack (Kafka, Zookeeper, Schema Registry, Application).
-   **Disk Space**: At least 10GB free space for Docker images and logs.

### Software
-   **OS**: Linux, macOS, or Windows (with WSL2).
-   **Java 21**: The project uses Java 21.
-   **Maven 3.9+**: For building the project.
-   **Docker & Docker Compose**: For containerization and running dependencies.
-   **Git**: For version control.

## Project Structure

-   `src/main/java`: Application source code.
-   `src/test/java`: Unit and integration tests.
-   `Dockerfile`: Docker image definition.
-   `docker-compose.yml`: Orchestration for local development and running the app with dependencies.

## Build

To build the application and generate the JAR file, run:

```bash
mvn clean package
```

This will create the executable JAR in the `target/` directory.

## Test

The project uses **Testcontainers** for integration testing, which requires Docker to be running.

### Run All Tests

```bash
mvn test
```

### Run Integration Tests

```bash
mvn verify
```

### Run Performance Tests

The project includes Gatling performance tests in `src/gatling/java`. You can run them using Maven.

**Prerequisites:** The application and Kafka environment must be running (e.g., via `docker-compose` or locally) and accessible.

```bash
mvn gatling:test
```

**Configuration:**
You can configure the test parameters using system properties (`-Dproperty=value`):

| Property | Description | Default |
| :--- | :--- | :--- |
| `baseUrl` | Base URL of the application | `https://localhost:8443` |
| `apiKey` | API Key for authentication | `default-api-key` |
| `topic` | Kafka topic to query | `test-topic` |
| `execId` | Execution ID for filtering | `test-exec-id` |
| `startTime` | Start of time window (ISO 8601) | 1 hour ago |
| `endTime` | End of time window (ISO 8601) | Now |

**Example:**

```bash
mvn gatling:test \
    -DbaseUrl=https://localhost:8443 \
    -DapiKey=my-secret-key \
    -Dtopic=my-data-topic
```

## Run Locally

### 1. Start Dependencies

Start Kafka, Zookeeper, and Schema Registry using Docker Compose:

```bash
docker-compose up -d zookeeper kafka schema-registry
```

Wait for the services to be healthy.

### 2. Run Application

You can run the application using Maven or the built JAR.

**Using Maven:**

```bash
mvn spring-boot:run
```

**Using JAR:**

```bash
java -jar target/k-rest-proxy-0.0.1-SNAPSHOT.jar
```

The application will start on port **8443** (HTTPS).

## Run with Docker

You can run the entire stack, including the application, using Docker Compose.

### 1. Build Docker Image

```bash
docker build -t k-rest-proxy .
```

### 2. Start All Services

```bash
docker-compose up -d
```

This will start:
-   Zookeeper
-   Kafka
-   Schema Registry
-   k-rest-proxy (accessible at `https://localhost:8443`)

## API Usage

The application exposes a REST API to fetch Kafka messages.

### Fetch Messages

**Endpoint:** `GET /api/v1/messages/{topic}`

**Parameters:**
-   `startTime`: Start timestamp (ISO 8601 format, e.g., `2023-10-27T10:00:00Z`).
-   `endTime`: End timestamp (ISO 8601 format, e.g., `2023-10-27T10:05:00Z`).

**Authentication:**
Requires an API Key header: `X-API-KEY`.

**Example Request:**

```bash
curl -k -H "X-API-KEY: your-api-key" \
     "https://localhost:8443/api/v1/messages/my-topic?startTime=2023-10-27T10:00:00Z&endTime=2023-10-27T10:05:00Z"
```

### Filter Messages by Execution ID

**Endpoint:** `GET /api/v1/messages/{topic}/filter`

**Parameters:**
-   `startTime`: Start timestamp.
-   `endTime`: End timestamp.
-   `execId`: Execution ID to filter by.

**Example Request:**

```bash
curl -k -H "X-API-KEY: your-api-key" \
     "https://localhost:8443/api/v1/messages/my-topic/filter?startTime=2023-10-27T10:00:00Z&endTime=2023-10-27T10:05:00Z&execId=12345"
```

*(Note: `-k` is used to skip SSL validation for self-signed certificates in development)*

## Deploy

### Docker Deployment

The application is designed to be deployed as a Docker container.

1.  **Build the Image**: Use the `Dockerfile` to build the image.
2.  **Push to Registry**: Tag and push the image to your container registry (e.g., Docker Hub, ECR).
    ```bash
    docker tag k-rest-proxy:latest myregistry/k-rest-proxy:v1.0.0
    docker push myregistry/k-rest-proxy:v1.0.0
    ```
3.  **Run**: Deploy using your orchestration tool (Kubernetes, ECS, Docker Swarm) ensuring the following environment variables are set:
    -   `SPRING_KAFKA_BOOTSTRAP_SERVERS`: Kafka broker address.
    -   `SPRING_KAFKA_PROPERTIES_SCHEMA_REGISTRY_URL`: Schema Registry URL.
    -   `SERVER_SSL_KEY_STORE`: Path to the keystore file (default: `/app/keystore.p12`).

### Environment Variables

| Variable | Description | Default |
| :--- | :--- | :--- |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `kafka:29092` |
| `SPRING_KAFKA_PROPERTIES_SCHEMA_REGISTRY_URL` | Schema Registry URL | `http://schema-registry:8081` |
| `SERVER_SSL_KEY_STORE` | Path to SSL Keystore | `file:/app/keystore.p12` |
