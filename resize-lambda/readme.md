Installation
============
`npm install`

Deployment
==========
Before running these steps ensure you are running the latest version of AWS CLI:

`sudo pip install --upgrade awscli`

## Creation of Lambda function and associated Role in AWS

`./createLambdaAndRole.sh`

(This should only need to be run once when setting up a new stage in AWS)

## Configure a Notification on the Bucket

Currently this must be created through the Console by following this tutorial: http://docs.aws.amazon.com/AmazonS3/latest/UG/SettingBucketNotifications.html#SettingBucketNotifications-enable-events

Once you have setup the event, put a file into the bucket, then review the event log within [CloudWatch Logs](https://eu-west-1.console.aws.amazon.com/cloudwatch/home?region=eu-west-1#logs:) to check the Lambda executed and see the result. 

## Update and test code

`./packageAndUpdate.sh`

This script updates the function code.


