#!/bin/bash
cd /home/davidhlp/project/UltiCode-Public-Next/backend-spring
export SPRING_DATASOURCE_URL="mysql://ulticode:2%5BOGT%23ds%3E1h7xZM%3CO%5D7%3B2%5BF%26@localhost:23306/ulticode"
export JWT_SECRET="5GXMfun06YtfZSSV5h3M7yNA9fmuagbY5dITQyqSVDfcgebV-DqD9upy0zsSpPbKVKdRh4kllefbUFaTDuvpSA"
./mvnw spring-boot:run -Dmaven.test.skip=true
