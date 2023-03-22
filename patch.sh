#!/bin/bash

# https://github.com/apache/camel-quarkus/issues/4620
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X 'PATCH' \
  $REGISTRY_URL'/admin/v1/platform-release/io.quarkus.platform%3Aquarkus-camel-bom-quarkus-platform-descriptor/2.9/2.9.2.Final/extension/org.apache.camel.quarkus/camel-quarkus-tagsoup/2.9.0' \
  -H 'TOKEN: '$REGISTRY_TOKEN \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'metadata=%7B%22built-with-quarkus-core%22%3A%222.9.0.Final%22%2C%22guide%22%3A%22https%3A%2F%2Fcamel.apache.org%2Fcamel-quarkus%2Flatest%2Freference%2Fextensions%2Ftagsoup.html%22%2C%22categories%22%3A%5B%22integration%22%5D%2C%22status%22%3A%5B%22deprecated%22%5D%2C%22extension-dependencies%22%3A%5B%22org.apache.camel.quarkus%3Acamel-quarkus-core%22%2C%22io.quarkus%3Aquarkus-core%22%2C%22io.quarkus%3Aquarkus-arc%22%2C%22io.quarkus%3Aquarkus-jaxp%22%2C%22org.apache.camel.quarkus%3Acamel-quarkus-support-xalan%22%5D%7D'
  )
if (( $HTTP_STATUS != 202 )); then
  echo "Failed to patch extension org.apache.camel.quarkus/camel-quarkus-tagsoup/2.9.0: " $HTTP_STATUS
  exit 1
fi

# https://github.com/apache/camel-quarkus/issues/4621
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X 'PATCH' \
  $REGISTRY_URL'/admin/v1/platform-release/io.quarkus.platform%3Aquarkus-camel-bom-quarkus-platform-descriptor/2.7/2.7.7.Final/extension/org.apache.camel.quarkus/camel-quarkus-ipfs/2.7.1' \
  -H 'TOKEN: '$REGISTRY_TOKEN \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'metadata=%7B%22built-with-quarkus-core%22%3A%222.7.0.Final%22%2C%22guide%22%3A%22https%3A%2F%2Fcamel.apache.org%2Fcamel-quarkus%2Flatest%2Freference%2Fextensions%2Fipfs.html%22%2C%22categories%22%3A%5B%22integration%22%5D%2C%22status%22%3A%5B%22deprecated%22%5D%2C%22extension-dependencies%22%3A%5B%22org.apache.camel.quarkus%3Acamel-quarkus-core%22%2C%22io.quarkus%3Aquarkus-core%22%2C%22io.quarkus%3Aquarkus-arc%22%5D%7D%2C%22artifact%22%3A%22org.apache.camel.quarkus%3Acamel-quarkus-ipfs%3A%3Ajar%3A2.7.1%22%2C%22origins%22%3A%5B%22io.quarkus.platform%3Aquarkus-camel-bom-quarkus-platform-descriptor%3Aquarkus-bom-quarkus-platform-descriptor%3A2.7.7.Final%3Ajson%3A2.7.7.Final%22%5D%7D'
)
if (( $HTTP_STATUS != 202 )); then
  echo "Failed to patch extension org.apache.camel.quarkus/camel-quarkus-ipfs/2.7.1: " $HTTP_STATUS
  exit 1
fi

# https://github.com/apache/camel-quarkus/issues/4622
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X 'PATCH' \
  $REGISTRY_URL'/admin/v1/platform-release/io.quarkus.platform%3Aquarkus-camel-bom-quarkus-platform-descriptor/2.7/2.7.7.Final/extension/org.apache.camel.quarkus/camel-quarkus-weka/2.7.1' \
  -H 'TOKEN: '$REGISTRY_TOKEN \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'metadata=%7B%22categories%22%3A%5B%22integration%22%5D%2C%22built-with-quarkus-core%22%3A%222.7.0.Final%22%2C%22status%22%3A%5B%22deprecated%22%5D%2C%22unlisted%22%3Atrue%2C%22guide%22%3A%22https%3A%2F%2Fcamel.apache.org%2Fcamel-quarkus%2Flatest%2Freference%2Fextensions%2Fweka.html%22%2C%22extension-dependencies%22%3A%5B%22org.apache.camel.quarkus%3Acamel-quarkus-core%22%2C%22io.quarkus%3Aquarkus-core%22%2C%22io.quarkus%3Aquarkus-arc%22%5D%7D'
)
if (( $HTTP_STATUS != 202 )); then
  echo "Failed to patch extension org.apache.camel.quarkus/camel-quarkus-weka/2.7.1: " $HTTP_STATUS
  exit 1
fi