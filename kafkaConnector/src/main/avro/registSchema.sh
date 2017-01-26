#!/bin/bash
curl -X POST -i -H "Content-Type: application/vnd.schemaregistry.v1+json" \
	--data @NaiveVariant.avsc http://localhost:8081/subjects/Variant/versions
##    --data '{"schema": "{\"type\": \"string\"}"}' http://localhost:8081/subjects/Variant/versions
