# Cluster interaction

This defines how the frontend interacts with the cluster and how it expects the cluster to respond.

## Making a request

The cluster will serialize a
[OneToManyRequest](https://github.com/conveyal/analyst-server/blob/queues/app/utils/OneToManyRequest.java) or a
[OneToManyProfileRequest](https://github.com/conveyal/analyst-server/blob/queues/app/utils/OneToManyProfileRequest.java)
to JSON; these are light wrappers around ProfileRequests and RoutingRequests, respectively, which contain a small amount
of additional information.

The important bits are the `destinationPointsetId`, which defines the pointset to used; if null, vector isochrones
should be generated. The pointset can be found in an S3 bucket (which is part of the cluster configuration) under the
name `<id>.json.gz`; it is, as the name would imply, a gzipped pointset. The `id` identifies this particular request,
and the `jobId` identifies the larger multipoint job this is a part of (which may be null). Together they uniquely
identify this request. `includeTimes` determines whether times should be included.

The `outputQueue` identifies the URL (NB not the name) of an SQS queue where a ResultComplete message (see below) should
be delivered, and the `outputLocation` is the name of an S3 bucket where the results should be saved. The ResultSet should
be saved as gzipped JSON, with the key 'jobId/id.json.gz' (if jobId is not null) or 'id.json.gz' if jobId is null.

These requests are serialized to JSON and gzipped and placed into SQS queues following the naming schemes described in the cluster spec.
Once  a worker receives a job, it is expected to process it, save the results to S3, and place a small gzipped JSON message in
the outputQueue that looks like this:

```{
  jobId: <jobId>,
  id: <id>
}```

The frontend will then retrieve the results from S3 and render them (for single point mode) or store them (multipoint mode).
