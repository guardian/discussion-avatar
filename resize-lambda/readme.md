Installation
============
`npm install`

Deployment
==========
Push the updated code:
`./packageAndUpdate.sh` 

Manually Invoke
======

## Configuaration

Edit `input.js` and set the object key and S3 bucket name 

## Invocation
For testing you can manually invoke the function:
`aws lambda invoke --invocation-type Event --function-name CreateThumbnail-code --region eu-west-1 --payload file:///path/to/input.js outputfile.txt`

Review the event log within [CloudWatch Logs](https://eu-west-1.console.aws.amazon.com/cloudwatch/home?region=eu-west-1#logs:) 

Configure a Notification on the Bucket
==========
Currently this must be created through the Console by following this tutorial: http://docs.aws.amazon.com/AmazonS3/latest/UG/SettingBucketNotifications.html#SettingBucketNotifications-enable-events




