#!/bin/bash

# Usage: createLambdaAndRole.sh [prod|code]

PWD=$(pwd)

ENV=${1:-"code"}

S3_POLICY_PATH=/tmp/Lambda-S3-Policy.json

cat >${S3_POLICY_PATH} << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": ["s3:GetObject", "s3:DeleteObject" ],
            "Resource": "arn:aws:s3:::com-gu-avatar-incoming-${ENV}/*"
        },
        {
            "Effect": "Allow",
            "Action": "s3:PutObject",
            "Resource":[
             "arn:aws:s3:::com-gu-avatar-raw-${ENV}/*",
             "arn:aws:s3:::com-gu-avatar-processed-${ENV}/*"
            ]
        }
    ]
}
EOF


aws iam create-role \
--role-name Lambda-Execution-Role \
--assume-role-policy-document file://${PWD}/Lambda-Execution-Role.json

aws iam put-role-policy \
--role-name Lambda-Execution-Role \
--policy-name Lambda-Basic-Execution-Policy \
--policy-document file://${PWD}/Lambda-Basic-Execution-Policy.json

aws iam put-role-policy \
--role-name Lambda-Execution-Role \
--policy-name Lambda-S3-Policy \
--policy-document file://${S3_POLICY_PATH}

zip CreateThumbnail.zip CreateThumbnail.js
zip -r CreateThumbnail.zip ./node_modules/

aws lambda create-function \
--region eu-west-1 \
--function-name CreateThumbnail-${ENV} \
--timeout 30 \
--memory-size 256 \
--zip-file fileb://${PWD}/CreateThumbnail.zip \
--role arn:aws:iam::082944406014:role/Lambda-Execution-Role \
--handler CreateThumbnail.handler \
--description "Resizes image and copies resulting file to an S3 Bucket" \
--runtime nodejs
