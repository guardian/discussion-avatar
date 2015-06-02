Installation
============
`npm install`

Deployment
==========

## Creation of Lambda function and associated Role in AWS

`./createLambdaAndRole.sh`

(This should only need to be run once when setting up a new stage in AWS)

## Update and test code

`./packageAndUpdate.sh`

This script updates the function code and puts a file in the S3 Incoming bucket which will trigger the Lambda to run.

Review the event log within [CloudWatch Logs](https://eu-west-1.console.aws.amazon.com/cloudwatch/home?region=eu-west-1#logs:) after executing the script. 

Configure a Notification on the Bucket
==========
Currently this must be created through the Console by following this tutorial: http://docs.aws.amazon.com/AmazonS3/latest/UG/SettingBucketNotifications.html#SettingBucketNotifications-enable-events




