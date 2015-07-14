# Avatar Service

The Avatar Service comprises of an API and various related tasks,
which take the form of Lambda functions.

The API can be accessed at:

[***REMOVED***/v1]([***REMOVED***/v1)

## Running locally

### Configuring AWS Credentials
Use the named profile `gu-aws-discussion` i.e. in `~/.aws/credentials`

```
[gu-aws-discussion]
aws_access_key_id=[YOUR_AWS_ACCESS_KEY]
aws_secret_access_key=[YOUR_AWS_SECRET_ACCESS_KEY]
```

### Start the app
```
cd api/
sbt
container:start
```


