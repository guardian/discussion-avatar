Avatar Service
==============

**This service is a work in progress!**

## Architecture

http://blog.8thlight.com/uncle-bob/2012/08/13/the-clean-architecture.html

Entities are core Avatar models and logic. Adapters relate to IO of some
form - http or the store. The Dependency Rule requires that the entity 
layer is unaware of the adapters. I.e. **nothing** in the `entities` package 
calls code from, or uses models defined in, the `adapters` package.

## File uploads

{profile-id}.png

jpg, png, gif <= 1mb

Do not store multiple images per user.

raw -> pending -> (approved | rejected)

Resize to 60x60 but make bigger for moderators somehow?

## Consistency?

Don't worry about it. But probably do need a database of some kind to 
store avatar meta-data and manage workflow.

## Behaviour

* can you flag an approved avatar for moderation? Yes.
* can users upload multiple images for moderation? No, latest wins.

## Docs

http://localhost:8080/api-docs/v1

([Swagger](http://swagger.io/)-driven.)