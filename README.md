# Avatar Service

The Avatar Service comprises of an API and various related tasks,
which take the form of Lambda functions.

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
jetty:start
```
==========
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
