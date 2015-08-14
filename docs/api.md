# API documentation

Analyst Server exposes an API that allows analyses created in Analyst Server to be visualized in other applications,
for example public-facing web apps.

## Getting started

For the time being, the Analyst Server API only allows one to perform analysis on scenarios that have already been
created, so a first step is to create scenarios and upload shapefiles to Analyst Server through the default user
interface and ensure that analysis can be performed and that results are satisfactory.

## Authentication

The APIs described here (with the exception of the tile APIs) require authentication using OAuth2. Your account has
API key(s) and secret(s) which you can use to connect applications to it. Authenticating via OAuth is a two-stage process:

1. Make a POST request to /oauth/token with an HTTP Basic Authorization header, using your key and secret as the username and
   password (which are concatenated with a : and then base64-encoded, and then placed in a header `Authorization: Basic <base64-encoded-value>`).
   Put in the body `grant_type=client_credentials`, and send it with content type application/x-www-form-urlencoded.

   If you're using a browser to do cross-origin requests, and thus cannot use an Authorization header, you can also put
   the key and secret in the POST body, as parameters `key` and `secret`, along with the `grant_type`, in standard form-encoded
   format. Note that it's recommended to retrieve tokens on the server side, because otherwise you have to expose your API
   key to the client.

2. The response from that is a JSON object like so:

    {
        "access_token": "many-random-letters-and-numbers",
        "token_type": "Bearer",
        "expires_in": 3600
    }

  It can be used by adding an `Authorization: Bearer <access_token>` header to all requests described herein. It is good for one hour.
  If you're doing cross-origin requests, you can instead include it as a query parameter, i.e. `?accessToken=...`. (Include it in the URL,
    even with POST requests).

The recommended way to use this would be to keep your API keys on your server and make a server-side request to get client
credentials, which could then be used in JavaScript directly.

## Single-point analysis

Currently the API only supports performing single point analysis. To perform a single point analysis, you POST a JSON
object to `/api/single`. The JSON you post looks like so:

```
{
  "destinationPointsetId": null,
  "graphId": "78953923453b6da1a585cc58621eebc7",
  "profile": true,
  "options": {
    "fromLat": 42.35296235855687,
    "fromLon": -71.06094360351562,
    "toLat": 42.35296235855687,
    "toLon": -71.06094360351562,
    "date": "2015-05-26",
    "fromTime": 25200,
    "toTime": 32400,
    "accessModes": "WALK",
    "egressModes": "WALK",
    "walkSpeed": 1.3333333333333333,
    "bikeSpeed": 4.1,
    "carSpeed": 20,
    "streetTime": 90,
    "maxWalkTime": 20,
    "maxBikeTime": 45,
    "maxCarTime": 45,
    "minBikeTime": 10,
    "minCarTime": 10,
    "suboptimalMinutes": 5,
    "analyst": true,
    "bikeSafe": 1,
    "bikeSlope": 1,
    "bikeTime": 1,
    "scenario": {
      "id": 0,
      "description": "No description",
      "modifications": [
        {
          "type": "remove-trip",
          "agencyId": "1",
          "routeId": ["Red"],
          "routeType": [1],
          "tripId": null
        }
      ]
    }
  }
}

```

`destinationPointsetId` is the ID of the shapefile for which to calculate connectivity (frequently called
"accessibility" in the literature). The IDs of shapefiles can be found by accessing `/api/shapefile`, which will give a
JSON document detailing all of the scenarios. If it is set to null, isochrones will be generated and returned as GeoJSON,
but accessibility will not be calculated.

The `graphId` is the ID of the scenario to use, which is the `id` attribute in `/api/scenario`. `profile` indicates that
the request should use profile routing, which calculates not the travel time and connectivity at a particular time but
rather the guaranteed, expected and possible connectivity over a time window (see [this
post](http://conveyal.com/blog/2015/05/04/variation-in-accessibility-measures/) for more details on these types of
connectivity).

The `options` field is a JSONified OTP [profile request](https://github.com/opentripplanner/OpenTripPlanner/blob/master/src/main/java/org/opentripplanner/profile/ProfileRequest.java).
`fromLat` and `fromLon` are the origins of the search. `toLat` and `toLon` are not used but must be specified; the simplest
thing to do is to just set them the same as `fromLat` and `fromLon`. `date` is the date of the search, YYYY-MM-DD.

`fromTime` and `toTime` specify the time window of the search, in seconds since local midnight. (Techinically, seconds
since local noon minus 12 hours, but this is midnight except on days when daylight savings time is activated or
deactivated. I'd recommend   not doing analysis on those days anyhow.) So 25200 is 7 AM (7 hours * 60 minutes/hour * 60
seconds/minute = 25200) and 32400 is 9 AM.

`scenario` specifies modifications on top of the graph that should be made prior to performing this request. A scenario
has a numeric ID and textual description (which are immaterial for this discussion) and a list of modifications. A modification
has a `type` parameter and then a number of additional parameters depending on the type specifying how it should be applied.
The various types of modifications are described below.

The remainder of the parameters are documented in the [OTP API
documentation](http://dev.opentripplanner.org/javadoc/master/org/opentripplanner/profile/ProfileRequest.html). They
currently do not have defaults, but the values specified in the examples above are reasonable.

Drive- and bike-to-transit searches are not currently supported in profile mode, but this is planned in the future.
They will be specified by changing the `accessModes` (the modes used access transit stops at the start of the trip)
and the `egressModes` (the modes used to leave transit at the end of the trip).

You will need to send the `Content-Type: application/json` header along with your request.

### Response

The responses are also JSON files which look like so:

```
{
  "properties": {
    "id": "boston",
    "label": "boston",
    "description": "",
    "schema": {
      "boston.housing10": {
        "label": "housing10",
        "style": {}
      },
      "boston.pop10": {
        "label": "Population",
        "style": {}
      }
    }
  },
  "key": "14cc35fa-7135-4753-8e6d-d1922d2fba57",
  "data": {
    "boston.housing10": {
      "worstCase": {
        "sums": [36, 109, 151, 250, 415, 512, 585, 578, 659, 694, 734, 857, 848, 901, 1266, 1794, 2155, 2345, 2451,
          2425, 2674, 3120, 3458, 3177, 3243, 3393, 3183, 3703, 4195, 4044, 3845, 4668, 6221, 7032, 7259, 7525, 8577,
          8789, 7633, 6890, 6358, 6607, 7361, 7802, 7802, 7787, 7621, 6991, 6908, 7212, 7773, 9154, 10082, 10228,
          10474, 10435, 10189, 10054, 9899, 11123, 12016, 11920, 12036, 12779, 12993, 12681, 12213, 12179, 12299,
          12724, 13073, 12690, 12186, 12392, 12234, 11514, 11137, 10701, 10034, 9453, 8954, 8502, 7777, 7101, 6863,
          7203, 7751, 7536, 7079, 7228, 6965, 6537, 6579, 7118, 7387, 7083, 6938, 6188, 5617, 5721, 5582, 5873, 5851,
          5345, 4476, 4456, 4635, 4606, 4567, 4275, 4169, 4087, 4224, 4156, 3512, 3072, 2749, 2595, 2502, 2631
        ],
        "counts": [1, 1, 3, 4, 7, 9, 11, 13, 15, 16, 16, 18, 20, 25, 33, 38, 47, 58, 63, 66, 70, 76, 85, 92, 96, 93, 85,
          89, 97, 96, 99, 112, 134, 148, 161, 168, 177, 186, 198, 211, 217, 220, 237, 232, 215, 220, 222, 199, 185,
          189, 201, 226, 242, 259, 272, 281, 295, 308, 327, 352, 375, 383, 380, 400, 405, 403, 398, 399, 409, 417,
          416, 417, 434, 456, 450, 431, 424, 425, 424, 399, 362, 336, 311, 297, 285, 278, 282, 290, 292, 296, 292,
          291, 300, 316, 328, 308, 289, 274, 265, 275, 276, 272, 264, 240, 214, 203, 208, 218, 212, 199, 197, 197,
          207, 204, 188, 171, 158, 150, 144, 147
        ]
      },
      "pointEstimate": {
        "sums": [36, 109, 151, 250, 416, 512, 589, 594, 682, 748, 814, 957, 1176, 1413, 1865, 2466, 2791, 2800, 3097,
          3522, 3729, 4084, 4109, 4606, 5218, 4921, 4873, 5997, 7425, 7574, 8034, 9036, 10026, 10078, 9305, 8829,
          8656, 8494, 8267, 8738, 8808, 8483, 9370, 10652, 10427, 10629, 10765, 10699, 10766, 11929, 13315, 13147,
          12028, 12273, 12918, 13294, 14220, 14978, 15305, 15821, 15601, 15333, 15499, 15723, 15929, 16300, 15794,
          14231, 13417, 12621, 12061, 11709, 11564, 11155, 11208, 10885, 10799, 10569, 9750, 8940, 8676, 8725, 8083,
          6737, 5723, 5632, 5969, 6195, 6382, 6884, 6476, 5778, 5255, 5279, 5196, 5347, 4971, 4738, 4845, 5022, 5210,
          5309, 5498, 5538, 5377, 5515, 5216, 4915, 5085, 4779, 4200, 3997, 4431, 3978, 3831, 3649, 3422, 3364, 3049,
          3013
        ],
        "counts": [1, 1, 3, 4, 7, 9, 12, 13, 16, 17, 20, 24, 29, 34, 40, 53, 67, 71, 81, 96, 100, 106, 111, 118, 126,
          131, 132, 145, 166, 176, 193, 226, 258, 276, 272, 257, 250, 245, 246, 265, 268, 264, 269, 285, 290, 297,
          299, 300, 322, 362, 394, 406, 409, 425, 439, 444, 442, 445, 449, 473, 496, 510, 523, 544, 566, 572, 565,
          548, 536, 518, 499, 496, 494, 488, 483, 477, 474, 478, 466, 446, 416, 372, 326, 289, 257, 257, 272, 284,
          293, 313, 314, 299, 286, 286, 277, 270, 266, 250, 246, 253, 247, 239, 241, 235, 224, 216, 209, 202, 204,
          203, 197, 199, 213, 209, 196, 194, 192, 177, 154, 142
        ]
      },
      "bestCase": {
        "sums": [36, 109, 151, 250, 417, 525, 654, 762, 1000, 1313, 1696, 2263, 2974, 3524, 3882, 4143, 4510, 4727,
          5182, 5720, 6288, 7720, 8791, 9153, 9372, 9840, 9629, 8930, 9181, 9958, 10078, 10433, 11423, 12467, 12645,
          12467, 12842, 12035, 11566, 11913, 11487, 11624, 12076, 13160, 14812, 15849, 15883, 16408, 17681, 18373,
          19908, 20868, 21314, 21514, 21096, 21454, 21960, 21188, 18574, 16216, 15741, 16320, 15935, 14691, 13295,
          11611, 10774, 10850, 10677, 9963, 9231, 8454, 8096, 7894, 7472, 7324, 7219, 7256, 7647, 7476, 6378, 5851,
          5743, 5175, 5034, 5301, 5419, 5240, 4712, 4316, 4159, 4089, 4343, 3865, 3778, 3594, 3337, 3414, 3184, 2585,
          2343, 2236, 2081, 2000, 1844, 1949, 2126, 2495, 2517, 2264, 1942, 1776, 1519, 1313, 1229, 1085, 1150, 1096,
          1013, 839
        ],
        "counts": [1, 1, 3, 4, 7, 9, 14, 19, 28, 37, 41, 51, 69, 88, 101, 111, 130, 141, 144, 157, 178, 212, 252, 268,
          275, 277, 274, 275, 295, 318, 322, 324, 334, 344, 344, 329, 328, 345, 355, 350, 348, 370, 399, 432, 468,
          498, 509, 537, 569, 587, 637, 680, 716, 762, 781, 800, 823, 801, 755, 726, 715, 708, 672, 617, 575, 528,
          491, 485, 486, 457, 435, 421, 397, 386, 390, 380, 344, 304, 286, 277, 264, 263, 262, 252, 244, 233, 230,
          231, 227, 217, 214, 201, 194, 188, 189, 176, 164, 164, 154, 134, 123, 118, 112, 107, 102, 105, 103, 103, 97,
          93, 83, 74, 66, 63, 63, 54, 52, 44, 32, 28
        ]
      }
    },
    "boston.pop10": {
      "worstCase": {
        "sums": [107, 317, 411, 638, 1041, 1277, 1453, 1409, 1627, 1705, 1781, 2098, 2080, 2259, 3167, 4437, 5350, 5903,
          6322, 6261, 6771, 8203, 9492, 8665, 8715, 9339, 9132, 9665, 10477, 10434, 10505, 12157, 14933, 15816, 15844,
          16104, 17256, 17394, 15793, 14765, 13492, 13655, 14836, 15682, 16803, 16941, 15730, 13734, 13140, 13961,
          15770, 18634, 20582, 21659, 22761, 23815, 24958, 24171, 23322, 25582, 27548, 27714, 28706, 30622, 31169,
          30914, 29862, 29136, 28874, 29789, 30730, 29776, 28671, 29552, 28874, 26896, 25999, 25108, 24144, 22430,
          20236, 18727, 17189, 15770, 15213, 16224, 17266, 17139, 16458, 17039, 16815, 16047, 15884, 16976, 17957,
          17283, 16753, 15224, 14162, 14688, 14010, 14200, 14262, 13131, 11401, 11045, 11147, 11111, 11085, 10322,
          9949, 9694, 10066, 9733, 8310, 7502, 6875, 6546, 6319, 6568
        ],
        "counts": [1, 1, 3, 4, 7, 9, 11, 13, 15, 16, 16, 18, 20, 25, 33, 38, 47, 58, 63, 66, 70, 76, 85, 92, 96, 93, 85,
          89, 97, 96, 99, 112, 134, 148, 161, 168, 177, 186, 198, 211, 217, 220, 237, 232, 215, 220, 222, 199, 185,
          189, 201, 226, 242, 259, 272, 281, 295, 308, 327, 352, 375, 383, 380, 400, 405, 403, 398, 399, 409, 417,
          416, 417, 434, 456, 450, 431, 424, 425, 424, 399, 362, 336, 311, 297, 285, 278, 282, 290, 292, 296, 292,
          291, 300, 316, 328, 308, 289, 274, 265, 275, 276, 272, 264, 240, 214, 203, 208, 218, 212, 199, 197, 197,
          207, 204, 188, 171, 158, 150, 144, 147
        ]
      },
      "pointEstimate": {
        "sums": [107, 317, 411, 638, 1042, 1278, 1463, 1448, 1685, 1825, 1960, 2335, 2854, 3437, 4573, 6166, 7112, 7339,
          8169, 9142, 9556, 10730, 10937, 11502, 12736, 12465, 13145, 15151, 17477, 17665, 18829, 21133, 22607, 21912,
          19629, 18587, 18038, 17196, 16726, 17966, 18223, 17632, 18921, 21658, 21829, 23243, 24563, 23814, 24022,
          26377, 28971, 29117, 27678, 28952, 30804, 31911, 33876, 35509, 36233, 37625, 37019, 35954, 36227, 36529,
          36909, 37722, 36431, 33496, 31720, 29860, 28286, 26899, 26343, 25790, 26198, 25602, 25231, 24591, 22749,
          20992, 20715, 20811, 19317, 16417, 14318, 14135, 15178, 15378, 15388, 17006, 16719, 14720, 12982, 12843,
          12885, 13174, 11986, 11500, 11842, 12097, 12337, 12470, 12578, 12668, 12806, 13474, 12675, 11891, 12076,
          11381, 10560, 10164, 10870, 9716, 9275, 8771, 8010, 7872, 7179, 7058
        ],
        "counts": [1, 1, 3, 4, 7, 9, 12, 13, 16, 17, 20, 24, 29, 34, 40, 53, 67, 71, 81, 96, 100, 106, 111, 118, 126,
          131, 132, 145, 166, 176, 193, 226, 258, 276, 272, 257, 250, 245, 246, 265, 268, 264, 269, 285, 290, 297,
          299, 300, 322, 362, 394, 406, 409, 425, 439, 444, 442, 445, 449, 473, 496, 510, 523, 544, 566, 572, 565,
          548, 536, 518, 499, 496, 494, 488, 483, 477, 474, 478, 466, 446, 416, 372, 326, 289, 257, 257, 272, 284,
          293, 313, 314, 299, 286, 286, 277, 270, 266, 250, 246, 253, 247, 239, 241, 235, 224, 216, 209, 202, 204,
          203, 197, 199, 213, 209, 196, 194, 192, 177, 154, 142
        ]
      },
      "bestCase": {
        "sums": [107, 317, 411, 639, 1046, 1309, 1628, 1859, 2415, 3203, 4224, 5692, 7508, 9045, 10285, 10911, 11578,
          12112, 13147, 14299, 15290, 18457, 20978, 21657, 22199, 23429, 22681, 19985, 20103, 22044, 22626, 22801,
          24784, 25820, 25263, 24930, 25693, 25349, 25436, 25811, 24966, 25469, 27490, 31187, 33975, 35560, 36350,
          37592, 40780, 43083, 46930, 48975, 50195, 51538, 51518, 51836, 52060, 49697, 44206, 39003, 37666, 38579,
          37760, 35267, 31779, 27490, 25404, 25118, 24588, 22960, 21458, 19899, 19272, 19368, 18821, 18625, 18467,
          18063, 17773, 17382, 15427, 13899, 13655, 12727, 12090, 12583, 13238, 13114, 11558, 10375, 9932, 9758,
          10127, 8945, 8788, 8197, 7494, 7677, 7228, 6024, 5405, 5208, 4766, 4506, 4239, 4508, 4820, 5593, 5575, 4984,
          4289, 4001, 3516, 3086, 3039, 2729, 2834, 2637, 2287, 1846
        ],
        "counts": [1, 1, 3, 4, 7, 9, 14, 19, 28, 37, 41, 51, 69, 88, 101, 111, 130, 141, 144, 157, 178, 212, 252, 268,
          275, 277, 274, 275, 295, 318, 322, 324, 334, 344, 344, 329, 328, 345, 355, 350, 348, 370, 399, 432, 468,
          498, 509, 537, 569, 587, 637, 680, 716, 762, 781, 800, 823, 801, 755, 726, 715, 708, 672, 617, 575, 528,
          491, 485, 486, 457, 435, 421, 397, 386, 390, 380, 344, 304, 286, 277, 264, 263, 262, 252, 244, 233, 230,
          231, 227, 217, 214, 201, 194, 188, 189, 176, 164, 164, 154, 134, 123, 118, 112, 107, 102, 105, 103, 103, 97,
          93, 83, 74, 66, 63, 63, 54, 52, 44, 32, 28
        ]
      }
    }
  }
}
```

The `properties` section describes the shapefile that the request was performed against. The `id` attribute here is not
the ID of the shapefile but rather a key into the attributes. `label` is the human-readable label of the shapefile, and
`description` is the longer form description, as entered in the Analyst Server UI. The `properties` section details the
attributes of the shapefile, including their names in the original file (the keys of the schema array), which are used
to key the connectivity results later in the response. Also included are the human readable labels from the analyst UI.

The `key` is a string uniquely identifying this request, which can be used to retrieve tiles.

The `data` section contains the connectivity values. For each attribute in the shapefile, it contains `worstCase`,
`pointEstimate`, and `bestCase` sections, representing the guaranteed, expected/average and possible connectivity
values, respectively. Each of these contains sums and counts, which are arrays where each entry represents a minute. The
`i`th entry in counts is the number of features that are reachable in between `i` and `i + 1` minutes. The sums are
similar, but the features are multiplied by their attribute values, which is generally more meaningful. In this example,
that means you are looking at connectivity to population rather than to Census blocks (which are for the purposes of
this   analysis arbitrary).

Both sums and counts are not strict cumulative opportunity measures. They use a logistic rolloff function with a fairly
sharp slope so that features very near the time bound are not arbitrarily included or excluded based on a few second
difference in travel time. This smooths the "echo" of large blocks through space and prevents a hard edge where a large
office building (say) goes just out of range. The weighting function used is:

1 / (1 + exp( -2 / 60 * (time - cutoff)))

where time is the time required to reach the feature (in seconds), and cutoff is the cutoff value, which ranges from 1
minute to 120 minutes (expressed in seconds of course) and is defined by the array index, as described above. The
default value of -2 / 60 is specified in Histogram.java and yields a rolloff of approximately 5 minutes. For
computational efficiency, weights less than 0.001 or greater than 0.999 are assumed to be 0 and 1, respectively, to
reduce the number of exponentiations required.

If you did not specify a destination pointset, isochrones are instead returned.
For each of the types of connectivity, there is a GeoJSON document (e.g. GeoJSON root is `isochrones.worstCase`) with isochrones spaced five minutes apart. Each GeoJSON
feature has one property, `cutoffSec`, which is the time associated with that isochrone (for example, a 45 minute isochrone
  is 2700 seconds).

### Tiles

It is also possible to receive tiles which show the travel times to each feature in the shapefile, or which compare the
travel times to each feature in the shapefile over two different scenarios. In order to retrieve tiles, you must first
perform a single point request as described above. If you're doing a comparison, you must perform that analysis for both
of the things you want to compare. You can then request tiles using the following URLs:

For a single request:

    /tile/single/<key>/<z>/<x>/<y>.png?which=<which>&timeLimit=<timeLimit>&showPoints=<showPoints>&showIso=<showIso>

The `key` is the key attribute in the response from the single-point query performed above. The time limit is how large
the represented isochrone should be, in seconds; for instance, if set to 3600, the tiles will show a smooth gradient
from 0 minutes to 60 minutes, with points beyond 60 minutes shown as unreachable. This time limit is not subject to the
logistic cutoff described above. `showPoints` determines whether Halton points for the destinations should be shown, a
lá [this map made for the Regional Plan Association](http://fragile-success.rpa.org/maps/jobs.html). `showIso`
determines whether thematic mapping of time (isochrones) should be shown. `x`, `y`, and `z` are the tile coordinates in
Web Mercator space.

The `which` attribute describes which type of connectivity you wish to measure: `BEST_CASE` (possible), `AVERAGE` (expected),
or `WORST_CASE` (guaranteed).

Comparison requests are very similar:

    /tile/single/<key1>/<key2>/<z>/<x>/<y>.png?which=<which>&timeLimit=<timeLimit>&showPoints=<showPoints>&showIso=<showIso>

Note that the the `key` attribute has been replaced by `key1` and `key2`; the query specified by `key2` is subtracted from
the query specified by `key1` and the difference is displayed. Yellow represents no change, with the opacity indicating
the travel time relative to the time limit; blue represents are that could be reached in less than the time limit before
but now can be reached faster, with the opacity indicating the ratio; and purple indicates new service, with the opacity
representing the travel time relative to the time limit.

## Types of modifications

There are a number of types of modifications that can be made on top of a graph to specify arbitrary scenarios that can
be quickly tested without requiring a graph rebuild.

### Removing trips

This modification has the same effect as the former `bannedRoutes` parameter---it removes trips or routes.

```
{
  "type": "remove-trip",
  "agencyId": "AGENCY ID",
  "routeId": ["Route ID 1", "Route ID 2"],
  "tripId": ["Trip ID 1", "Trip ID 2"],
  "routeType": [1]
}
```

The `agencyId` parameter specifies the agency ID of trips to remove,
and must be specified. `routeId`, `routeType`, and `tripId` are lists
of IDs which are used to select routes and trips. They are combined
with a logical `and`, meaning that a trip must be referenced both by
trip ID and route ID and route type to be removed. If either trip ID, route type or route ID is
left `null`, it is treated as a wildcard matching all trip IDs, route types, or route
IDs.

### Adjusting frequencies

This modification allows adjusting the headway of frequency-based trips (it will have no effect on scheduled trips).

```
{
  "type": "adjust-headway",
  "agencyId": "AGENCY ID",
  "routeId": ["Route ID 1", "Route ID 2"],
  "tripId": ["Trip ID 1", "Trip ID 2"],
  "headway": 600
}
```

Parameters are the same as for removing trips, with the addition of the `headway` parameter, which is the new headway
in seconds. As before, setting `routeId`, `routeType`, or `tripId` to `null` is a wildcard.

### Adjusting dwell times

```
{
  "type": "adjust-dwell-time",
  "agencyId": "AGENCY ID",
  "routeId": ["Route ID 1", "Route ID 2"],
  "tripId": ["Trip ID 1", "Trip ID 2"],
  "stopId": ["stop ID 1", "stop ID 2"],
  "dwellTime": 30
}
```

Again parameters are the same with the addition of the `stopId` parameter which matches stops along a trip, with the same
semantics as the other (if left null all stops on all matched trips are updated).

### Skipping stops

```
{
  "type": "skip-stop",
  "agencyId": "AGENCY ID",
  "routeId": ["Route ID 1", "Route ID 2"],
  "tripId": ["Trip ID 1", "Trip ID 2"],
  "stopId": ["stop ID 1", "stop ID 2"]
}
```

This causes stops to be skipped. Parameters are the same as adjusting dwell times, but of course without specifying a dwell
time. Skipped stops are no longer served by the matched trips, and and dwell time at a skipped stop is removed from the schedule.
If stops are skipped at the start of a trip, the start of the trip is simply removed; the remaining times are not shifted.

## Multipoint Analysis

It is also possible to perform multipoint analysis, wherein single point analysis is run as a batch over many origins and
the results are summarized. For example, one might run a query showing the job accessibility change for every Census
block in a city. To start a multipoint job, you POST a JSON object to `/api/query`:

```
{
  "name": "query name",
  "shapefileId": <pointset ID>,
  "projectId": <project ID>,
  "bundleId": <bundle ID>,
  "profileRequest": {
    "date": "2015-05-26",
    "fromTime": 25200,
    "toTime": 32400,
    "accessModes": "WALK",
    "egressModes": "WALK",
    "walkSpeed": 1.3333333333333333,
    "bikeSpeed": 4.1,
    "carSpeed": 20,
    "streetTime": 90,
    "maxWalkTime": 20,
    "maxBikeTime": 45,
    "maxCarTime": 45,
    "minBikeTime": 10,
    "minCarTime": 10,
    "suboptimalMinutes": 5,
    "analyst": true,
    "bikeSafe": 1,
    "bikeSlope": 1,
    "bikeTime": 1,
    "scenario": {
      "id": 0,
      "description": "No description",
      "modifications": [
      {
        "type": "remove-trip",
        "agencyId": "1",
        "routeId": ["Red"],
        "tripId": null
      }
      ]
    }
  }
}
```

This contains a basic definition of the parameters for the query. The name is simply a human-readable string referring
to what this query is. The `shapefileId` defines the pointset ID that should be used for this query; currently, the same
pointset is used as both origins and destinations (this [will be changed
soon](https://github.com/conveyal/analyst-server/issues/84)). Accessibility is calculated to all of the attributes of the
pointset, as in single point mode; there is no need to specify an attribute. Project ID is an analyst server project ID
(see `/api/project`); it is not strictly necessary, but allows the output of queries to be viewed in the Analyst UI,
helpful for debugging.

The response looks like this:

```
{
  "id": "20dfd9b05cac968a6eb6a195fdaeb01e",
  "projectId": "14e4274a080d86ef0993deea2c6af986",
  "name": "query name",
  "mode": null,
  "shapefileId": "14e4274a080d86ef0993deea2c6af986_f41d86e7f826fb644e6b0a27d58897a5",
  "scenarioId": null,
  "status": null,
  "totalPoints": 142,
  "completePoints": 0,
  "fromTime": 0,
  "toTime": 0,
  "date": null,
  "graphId": "78953923453b6da1a585cc58621eebc7",
  "profileRequest": {
    "fromLat": 0,
    "fromLon": 0,
    "toLat": 0,
    "toLon": 0,
    "fromTime": 25200,
    "toTime": 32400,
    "walkSpeed": 1.3333334,
    "bikeSpeed": 4.1,
    "carSpeed": 20,
    "streetTime": 90,
    "maxWalkTime": 20,
    "maxBikeTime": 45,
    "maxCarTime": 45,
    "minBikeTime": 10,
    "minCarTime": 10,
    "date": [
      2015,
      5,
      26
    ],
    "orderBy": null,
    "limit": 0,
    "accessModes": {
      "qModes": [
        {
          "mode": "WALK",
          "qualifiers": []
        }
      ]
    },
    "egressModes": {
      "qModes": [
        {
          "mode": "WALK",
          "qualifiers": []
        }
      ]
    },
    "directModes": null,
    "transitModes": null,
    "analyst": true,
    "bikeSafe": 1,
    "bikeSlope": 1,
    "bikeTime": 1,
    "suboptimalMinutes": 5,
    "scenario": {
      "id": 0,
      "description": "No description",
      "modifications": [
        {
          "type": "remove-trip",
          "warnings": [],
          "agencyId": "1",
          "routeId": [
            "Red"
          ],
          "tripId": null
        }
      ]
    }
  },
  "routingRequest": null,
  "shapefileName": "tract",
  "transit": null,
  "percent": 0
}
```

For the most part it just gives your parameters back to you. There are many null fields, which are used when using
predefined scenarios (it is possible to define scenarios in Analyst Server and then leave the `profileRequest` field
blank on the initial request, with values filled in from the scenario). It also has fields `completePoints` and
`totalPoints`; initially both are zero. Once the pointset has been read by the server, `totalPoints` will be the number
of origins in the pointset, and `completePoints` will be the number of points that have been completed. This data can be
refreshed by requesting

      /api/query/<id>

with `id` being the `id` attribute of the original response.

### Tiles

Query result tiles are available at the following URL.

    /tile/query/<id>/<z>/<x>/<y>.png?timeLimit=<timeLimit>&attributeName=<attribute name>&which=<accessibility type>

`id` is the `id` attribute from the original response. `timeLimit` is the limit in seconds for which to retrieve
accessibility. `attributeName` is the name of the attribute to use for accessibility in the shapefile. `which` is one of
`WORST_CASE`, `AVERAGE`, or `BEST_CASE`, indicating what type of accessibility you wish to calculate.

If you wish to compare multiple queries, you can do that like so:

    /tile/query/<id>/<otherId>/<z>/<x>/<y>.png?timeLimit=<timeLimit>&attributeName=<attribute name>&which=<accessibility type>

The query with ID `otherId` will be subtracted from the query with id `id` and then the results will be rendered.

### Legends

The tiles don't have a clearly defined color scheme, as the single point tiles do. Rather, they are classified using
a [Natural Breaks classifier](http://en.wikipedia.org/wiki/Jenks_natural_breaks_optimization). Thus they need a legend
so that they can be interpreted. This legend is available from

    /api/query/<id>/bins?timeLimit=<time limit>&attributeName=<attr name>&which=<accessibility type>

or, for a multipoint query,

    /api/query/<id>/<id2>/bins?timeLimit=<time limit>&attributeName=<attr name>&which=<accessibility type>

The response looks like this:

```
[
  {
    "lower": -1.0999999999999999e-07,
    "upper": 116004102,
    "lowerPercent": -6.230556574296034e-16,
    "upperPercent": 0.7227701203614078,
    "hexColor": "#ffffff"
  },
  {
    "lower": 116004102,
    "upper": 331289814,
    "lowerPercent": 0.7227701203614078,
    "upperPercent": 2.0641199286150105,
    "hexColor": "#ccccff"
  },
  {
    "lower": 331289814,
    "upper": 650310327,
    "lowerPercent": 2.0641199286150105,
    "upperPercent": 4.051795283222454,
    "hexColor": "#9999ff"
  },
  {
    "lower": 650310327,
    "upper": 1116192323,
    "lowerPercent": 4.051795283222454,
    "upperPercent": 6.954499416246413,
    "hexColor": "#6666ff"
  },
  {
    "lower": 1116192323,
    "upper": 1781270588,
    "lowerPercent": 6.954499416246413,
    "upperPercent": 11.098307172663564,
    "hexColor": "#3333ff"
  },
  {
    "lower": 1781270588,
    "upper": 2933247564,
    "lowerPercent": 11.098307172663564,
    "upperPercent": 18.27576489391803,
    "hexColor": "#0000ff"
  }
]
```

It is an array of objects, each one representing one class on the map. It has the lower and upper bounds of each class
represented as an absolute number (`lower` and `upper`) as well as as a percentage of the sum of that attribute over
all features in the pointset (be aware that this number is sensitive to how large the pointset is geographically; for instance,
  if a New York analysis run happened to also include Philadelphia, the percentage accessible would be artificially deflated.)
Finally, they include the hex color of that bin on the map tiles.
