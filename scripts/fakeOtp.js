#!/usr/bin/iojs

// a fake OTP cluster worker that just spits back semi-random results
// Usage: iojs fakeOtp.js <queue url>
// Where queue URL is the URL of the queue from which you would like it to pull.
// (yes it only does one queue)

var AWS = require('aws-sdk');
var zlib = require('zlib');
var request = require('request');

AWS.config.region = 'us-east-1'

var s3 = new AWS.S3();
var sqs = new AWS.SQS();

var queueUrl = process.argv[process.argv.length - 1];

var pointSets = new Map();

var poll;

/** Get a point set from S3. */
function getPointSet(id, callback) {
  if (pointSets.has(id)) {
    console.log('returning existing pointset ' + id);
    return pointSets.get(id);
  } else {
    console.log('retrieving analyst-dev-pointsets/' + id + '.json.gz');
    s3.getObject({
        Bucket: 'analyst-dev-pointsets',
        Key: id + '.json.gz'
      },
      function(err, data) {
        if (err) {
          console.log(err);
          return;
        }

        console.log('retrieved pointset');

        console.log(data.Body);

        zlib.gunzip(data.Body, function(err, psraw) {
          console.log('extracted pointset ' + id);
          console.log(psraw);
          pointSets.set(id, JSON.parse(psraw));
          poll();
        });
      });

    return null;
  }
}

function avg(array) {
  return array.reduce(function(a, b) {
    return a + b;
  }) / array.length;
}

function max(array) {
  return array.reduce(function(a, b) {
    return Math.max(a, b);
  });
}

var writeTimes = [];

/** Get some messages from SQS */
poll = function() {
  console.log('polling');
  sqs.receiveMessage({
      QueueUrl: queueUrl,
      MaxNumberOfMessages: 10,
      WaitTimeSeconds: 5
    },
    function(err, data) {
      if (err) {
        console.log(err);
        return;
      }

      if (data.Messages === undefined) {
        // no messages this time, poll again.
        // using long polls so this only happens every 5s when the queue is empty
        poll();
        return;
      }

      // loop over all messages and make fake results
      data.Messages.every(function(message) {
        console.log('processing message');

        var body = JSON.parse(message.Body);

        console.log(body);

        var ps = getPointSet(body.destinationPointsetId);

        // we'll get this message the next time it comes around.
        if (ps === null) {
          console.log('pointset not yet retrieved, returning to queue');
          return false;
        }

        // make up times if requested
        var times = null;
        if (body.includeTimes) {
          console.log('including times')
          times = ps.features.map(function(pf) {
            var distManhattan =
              Math.abs(body.options.fromLat - pf.geometry.coordinates[1]) +
              Math.abs(body.options.fromLon - pf.geometry.coordinates[0]);

            // suppose you travel at a constant 20kmph north-south, and 20kmph east-west at the
            // equator, with your speed east-west declining with the cosine of your latitude . . .
            return Math.floor(distManhattan * 3600 * 110 / 20);
          });
        }

        // make up histograms
        var histograms = {};

        var counts = [];
        var factor = Math.random();
        for (var i = 0; i < 120; i++) {
          counts.push(Math.floor(factor * 1000 * i));
        }

        for (var attr in ps.features[0].properties.structured) {
          histograms[attr] = {
            counts: counts,
            sums: counts
          };
        }

        // make a resultset
        var rs = {
          times: times, // will be undefined if times were not requested
          histograms: histograms

        }

        // and an envelope
        var re = {
          pointEstimate: rs,
          avgCase: rs,
          bestCase: rs,
          worstCase: rs,
          id: body.id,
          destinationPointsetId: body.destinationPointsetId
        };

        // compress it.
        re = JSON.stringify(re);
        var comp = zlib.gzipSync(re);

        console.log("sending " + (re.length / 1000) + " kb uncompressed, " + (comp.length / 1000) +
          " kb compressed");

        var s3start =  (new Date().getTime());

        // Direct proxy
        if (body.directOutputUrl) {
          console.log("sending via direct proxy");
          request({
              method: 'POST',
              uri: body.directOutputUrl,
              body: comp,
              headers: {
                'Content-Type': 'application/gzip'
              }
            },
            function(err, data) {
              if (err) {
                console.log('send err' + err);
              }
          });
        }

        // write to S3/SQS
        if (body.outputLocation && body.outputQueue) {
          console.log("saving to s3/sqs");

          s3.putObject({
            Bucket: body.outputLocation,
            Key: body.jobId ? body.jobId + "/" + body.id + ".json.gz" : body.id + ".json.gz",
            Body: comp
          },
          function (err, data) {
            // Load to SQS after S3 has done
            // Most S3 regions have read-after-write consistency so this is fine
            // US Standard has only eventual consistency, so it's possible that whatever is receiving the queue
            // would get the message before the item has propagated, but then it will just return the message to the queue
            // and try again in 30 seconds.
            if (err) {
              console.log("s3 upload err:" + err);
            } else {
              sqs.sendMessage({
                QueueUrl: body.outputQueue,
                MessageBody: JSON.stringify({
                  jobId: body.jobId,
                  id: body.id
                })
              }, function (err, data) {
                if (err) console.log('failed to send to sqs:' + err);
              });
            }
          });
        }

        // lazy lazy: should not be deleting until everything is done
        sqs.deleteMessage({
          QueueUrl: queueUrl,
          ReceiptHandle: message.ReceiptHandle
        },
        function (err, data) {
          if (err) console.log(err);
        });

        return true;
      });

      // play it again, sam
      poll();
    });
};

// start everything running
poll();
