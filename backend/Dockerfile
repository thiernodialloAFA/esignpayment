FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install Tesseract OCR + French/English language data + font support
RUN apk add --no-cache \
    tesseract-ocr \
    tesseract-ocr-data-fra \
    tesseract-ocr-data-eng \
    fontconfig \
    ttf-dejavu

COPY --from=builder /build/target/*.jar app.jar
RUN mkdir -p uploads/documents uploads/kyc uploads/contracts
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
