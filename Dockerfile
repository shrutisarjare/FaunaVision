# FaunaVision — one container that serves the React build and the Spring Boot API.
#
#   docker build -t faunavision .
#   docker run -p 8080:8080 -e JWT_SECRET=... faunavision
#
# Models (~176 MB) and the H2 database are not in git; the models are fetched once here.

# ---------- 1. React build ----------
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# A developer's .env must never leak into the image (see .dockerignore), and an explicitly
# empty value beats any .env that does get in. Empty means: call the API on our own origin.
ARG VITE_API_URL=""
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ---------- 2. Spring Boot build ----------
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /src
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
# Whatever the SPA built becomes part of the jar's static resources.
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -B -q -DskipTests package

# ---------- 3. Models ----------
# A throwaway stage so curl never reaches the runtime image.
FROM alpine:3.20 AS models
RUN apk add --no-cache curl
ARG CLIP=https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main
RUN mkdir -p /models/clip
WORKDIR /models
RUN curl -fsSL "$CLIP/onnx/vision_model_quantized.onnx" -o clip/vision_model.onnx \
 && curl -fsSL "$CLIP/onnx/text_model_quantized.onnx"   -o clip/text_model.onnx \
 && curl -fsSL "$CLIP/vocab.json"                       -o clip/vocab.json \
 && curl -fsSL "$CLIP/merges.txt"                       -o clip/merges.txt \
 && curl -fsSL "https://github.com/onnx/models/raw/main/validated/vision/classification/mobilenet/model/mobilenetv2-12.onnx" -o mobilenetv2-12.onnx \
 && curl -fsSL "https://raw.githubusercontent.com/onnx/models/main/validated/vision/classification/synset.txt" -o imagenet-synset.txt \
 && curl -fsSL "https://huggingface.co/audiomagic/yamnet-onnx/resolve/main/yamnet.onnx" -o yamnet.onnx \
 && ls -l clip

# ---------- 4. Runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=models /models ./models
COPY --from=api /src/target/species-api.jar app.jar

# Written on first start; mount a volume here to survive redeploys (see render.yaml).
RUN mkdir -p /app/data

ENV PORT=8080
EXPOSE 8080

# The process settles around 340 MB with CLIP loaded, so a 512 MB instance needs the
# heap capped well below the default; the ONNX session is allocated off-heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "app.jar"]
