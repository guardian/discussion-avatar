Avatar Service
==============

GET /profile
GET /profile/{id}
GET /profile/{id}/avatar
PUT /profile/{id}/avatar

GET /avatar
GET /avatar/pending
GET /avatar/approved
GET /avatar/rejected


How are we going to store profile information without a db? Avatar
filename?

Yes, {profile-id}.png?

Resizing? A lambda perhaps, or just resize on upload?


If S3 only then what about consistency?! Can't guarantee this.


## File uploads

{profile-id}.png

jpg, png, gif <= 1mb

Do not store multiple images per user.

raw -> pending -> (approved | rejected)

Resize to 60x60 but make bigger for moderators somehow?

## Consistency?

Don't worry about it. But might be worth using a database? To store
original filename?


## Behaviour

q. can you flag an approved avatar for moderation?
q. can users upload multiple images for moderation? No, latest wins...
