# Avatar Service

The Avatar Service comprises of an API and various related tasks,
which take the form of Lambda functions.

## Running locally

### Configuring AWS Credentials
Use the named profile `discussion` i.e. in `~/.aws/credentials`

```
[discussion]
aws_access_key_id=[YOUR_AWS_ACCESS_KEY]
aws_secret_access_key=[YOUR_AWS_SECRET_ACCESS_KEY]
```

### Start the app
```
cd api/
sbt
container:start
```
===========
```
              _.---._    /\\
           ./'       "--`\//
         ./              o \
        /./\  )______   \__ \
       ./  / /\ \   | \ \  \ \
 VK       / /  \ \  | |\ \  \7
           "     "    "  "
```

