#!/bin/bash

# Usage: packageAndUpdate.sh [prod|code]

PWD=$(pwd)
ENV=${1:-"code"}

zip -r CreateThumbnail.zip .
aws lambda update-function-code --function-name CreateThumbnail-${ENV} --zip-file fileb://${PWD}/CreateThumbnail.zip