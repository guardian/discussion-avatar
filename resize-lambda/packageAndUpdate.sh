#!/bin/bash

# Usage: packageAndUpdate.sh [prod|code]

PWD=$(pwd)
ENV=${1:-"code"}

zip -r CreateThumbnail.zip .
aws lambda update-function-code --function-name CreateThumbnail-${ENV} --zip-file fileb://${PWD}/CreateThumbnail.zip
# Put file through as test
aws s3 cp 100x400_avatar.png s3://com-gu-avatar-incoming-${ENV}/100x400_avatar.png