#!/bin/bash
docker start postgres-pruebas
set -a
source .env
set +a
./mvnw spring-boot:run