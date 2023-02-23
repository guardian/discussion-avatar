# Avatar Service

The Avatar Service comprises of an API and various related tasks,
which take the form of Lambda functions.

https://avatar.code.dev-theguardian.com/v1
https://avatar.theguardian.com/v1

## Architecture

Metadata about avatars is stored in Dynamo. Image files are stored in
S3.

The Avatar API is used for uploading avatars and updating their status
(for moderation purposes).

Some lambdas are used to resize avatars on initial upload. These live
in the Discussion Platform repo.

There are 4 S3 buckets used:

* incoming (for newly uploaded avatars)
* raw (to preserve original files)
* processed (for resized files)
* origin (for approved files - this bucket is public)

The exact flow is:

    upload (api)
    -> + incoming

    resize (lambda)
    -> + raw
    -> + processed
    -> - incoming

    approved (api - moderation)
    -> + origin
    -> old avatar files and records deleted

(+/- indicate add/remove from bucket.)

If the avatar is instead rejected by the moderatorsn, the image is not
moved to origin and any previous avatar files are left in place.

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
run
```

Note that the app will run on port 8080 by default (configurable by setting the PORT environment variable - not 8900 as is implied by the output when you start the service).

## Deploying the app

The AWS infrastructure that this application runs on is defined (using cloudformation) in 
[discussion-avatar](https://github.com/guardian/discussion-platform/tree/main/discussion-avatar) sub directory of the
discussion-platform repository. To build the application TeamCity constructs the following working directory:
```
Github                                    TeamCity
------                                    --------
discussion-avatar/api/                 => .
discussion-platform/discussion-avatar/ => ./platform
```
and then builds the project. In particular, this is why in `build.sbt` the RiffRaff and cloudformation files are
assumed to be in the `platform` sub directory of the project. From inspection, if a branch of this repository is built 
in TeamCity, the main branch of the discussion-platform will be used; the converse is also true. The freshness 
of the main branch depends on the configured value for the _checking changes interval_ property.

===========
### License
```
Copyright 2015 Guardian News & Media Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
