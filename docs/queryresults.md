# Query Result Format

The query results are stored in a simple binary flat-file format optimized for rapid display. This document describes the
format. The files are stored gzipped; the gzip protocol is described elsewhere. This documents the format within the
gzip container.

The file is encoded using the Java standard library DataOutput and DataInput classes. When this document says there is a
UTF value, it is accessed using {read|write}UTF (), and an int value is accessed using {read|write}Int (). Each file
contains the results for a single envelope parameter (BEST_CASE etc.), query, and attribute, thus grouping the data we
need to render the result without including any extra data. This way we loop over only what we need.

Header:

UTF: literal text `QUERYRESULT`, identifying the file type.
UTF: query ID
UTF: variable name (as returned by the cluster, format `categoryId.variable`)
UTF: envelope parameter (e.g. `AVERAGE`, `POINT_ESTIMATE`, etc.)

Each entry:
UTF: feature ID
int: number of minutes in histogram
repeated int: counts (one int for each minute, length determined by above)
repeated int: sums (one int for each minute, length again determined by above)
