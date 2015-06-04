#!/bin/bash

# Usage: packageAndUpdate.sh [prod|code]

PWD=$(pwd)
ENV=${1:-"code"}

zip CreateThumbnail.zip CreateThumbnail.js
zip -r CreateThumbnail.zip ./node_modules/

aws lambda update-function-code --function-name CreateThumbnail-${ENV} --zip-file fileb://${PWD}/CreateThumbnail.zip
