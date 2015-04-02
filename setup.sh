#!/bin/bash

TABLE_NAME=${1:-Avatars}
STAGE=${2:-DEV}

aws dynamodb create-table \
    --table-name ${TABLE_NAME}-${STAGE} \
    --attribute-definitions \
    AttributeName=AvatarId,AttributeType=S \
    AttributeName=Status,AttributeType=S \
    AttributeName=UserId,AttributeType=N \
    --key-schema \
    AttributeName=AvatarId,KeyType=HASH \
    --global-secondary-indexes '
          [
            {
              "IndexName": "Status-AvatarId-index",
              "KeySchema": [
                {
                  "AttributeName": "Status",
                  "KeyType": "HASH"
                }
              ],
              "Projection": {
                "ProjectionType": "ALL"
              },
              "ProvisionedThroughput": {
                "ReadCapacityUnits": 1,
                "WriteCapacityUnits": 1
              }
            },
            {
              "IndexName": "UserId-AvatarId-index",
              "KeySchema": [
                {
                  "AttributeName": "UserId",
                  "KeyType": "HASH"
                }
              ],
              "Projection": {
                "ProjectionType": "ALL"
              },
              "ProvisionedThroughput": {
                "ReadCapacityUnits": 1,
                "WriteCapacityUnits": 1
              }
            }
          ]' \
    --provisioned-throughput ReadCapacityUnits=1,WriteCapacityUnits=1