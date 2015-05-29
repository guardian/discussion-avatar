// dependencies
var async = require('async');
var AWS = require('aws-sdk');
var gm = require('gm')
    .subClass({ imageMagick: true }); // Enable ImageMagick integration.
var util = require('util');

// constants
var MAX_WIDTH  = 60;
var MAX_HEIGHT = 60;

// get reference to S3 client 
var s3 = new AWS.S3();

exports.handler = function(event, context) {
    // Read options from the event.
    console.log("Reading options from event:\n", util.inspect(event, {depth: 5}));
    var srcBucket = event.Records[0].s3.bucket.name;
    // Object key may have spaces or unicode non-ASCII characters.
    var srcKey    =
        decodeURIComponent(event.Records[0].s3.object.key.replace(/\+/g, " "));
    var dstBucket = srcBucket + "-resized";
    var dstKey    = "resized-" + srcKey;

    // Sanity check: validate that source and destination are different buckets.
    if (srcBucket == dstBucket) {
        console.error("Destination bucket must not match source bucket.");
        return;
    }

    // Download the image from S3, transform, and upload to a different S3 bucket.
    async.waterfall([
            function download(next) {
                // Download the image from S3 into a buffer.
                s3.getObject({
                        Bucket: srcBucket,
                        Key: srcKey
                    },
                    next);
            },
            function tranform(response, next) {

                // obtain the size of an image
                gm(response.body)
                    .size(function (err, size) {
                        if (!err)
                            console.log(size.width > size.height ? 'wider' : 'taller than you');
                    });

                gm(response.Body).size(function(err, size) {
                    // Transform the image buffer in memory.
                    // Center, crop square, resize, strip metadata, convert to PNG
                    var smallestDimension = Math.min(size.width, size.height);
                    this.gravity('Center')
                        .extent(smallestDimension, smallestDimension)
                        .resize(MAX_WIDTH, MAX_HEIGHT)
                        .noProfile()
                        .toBuffer('PNG', function(err, buffer) {
                            if (err) {
                                next(err);
                            } else {
                                next(null, "image/png", buffer);
                            }
                        });
                });
            },
            function upload(contentType, data, next) {
                // Stream the transformed image to a different S3 bucket.
                s3.putObject({
                        Bucket: dstBucket,
                        Key: dstKey,
                        Body: data,
                        ContentType: contentType
                    },
                    next);
            }
        ], function (err) {
            if (err) {
                console.error(
                        'Unable to resize ' + srcBucket + '/' + srcKey +
                        ' and upload to ' + dstBucket + '/' + dstKey +
                        ' due to an error: ' + err
                );
            } else {
                console.log(
                        'Successfully resized ' + srcBucket + '/' + srcKey +
                        ' and uploaded to ' + dstBucket + '/' + dstKey
                );
            }

            context.done();
        }
    );
};
