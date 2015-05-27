Installation
============
`npm install`

Configuaration
==============
Edit `input.js` and set the object key and S3 bucket name 

Deployment
==========
Create a .zip archive of the directory
`zip -r CreateThumbnail.zip .`

Push the updated code:
`aws lambda update-function-code --function-name CreateThumbnail --zip-file fileb:///path/to/CreateThumbnail.zip 

Invoke
======
For testing you can manually invoke the function:
`aws lambda invoke --invocation-type Event --function-name CreateThumbnail --region eu-west-1 --payload file:///path/to/input.js outputfile.txt 
Review the event log within [CloudWatch Logs](https://eu-west-1.console.aws.amazon.com/cloudwatch/home?region=eu-west-1#logs:)


