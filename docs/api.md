# API documentation

Analyst Server exposes an API that allows analyses created in Analyst Server to be visualized in other applications,
for example public-facing web apps.

## Getting started

For the time being, the Analyst Server API only allows one to perform analysis on scenarios that have already been
created, so a first step is to create scenarios and upload shapefiles to Analyst Server through the default user
interface and ensure that analysis can be performed and that results are satisfactory.

## Single-point analysis

Currently the API only supports performing single point analysis. To perform a single point analysis, you POST a JSON
object to `/api/single`. The JSON you post looks like so:

```
{
  "destinationPointsetId": "60b294f2e9b12e4814f584cced50efa9_729af269afd764aa742bec4efe5486d3",
  "graphId": "2a4b112aa5fb1b258f2c7fc6120e6497",
  "profile": "true",
  "options": {
    "fromLat": 42.3641249807027,
    "fromLon": -71.06145858764648,
    "toLat": 42.3641249807027,
    "toLon": -71.06145858764648,
    "date": "2015-05-12",
    "accessModes": "WALK",
    "egressModes": "WALK",
    "fromTime": 25200,
    "toTime": 32400,
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
    "bannedRoutes": "2_Red"
  }
}
```

`destinationPointsetId` is the ID of the shapefile for which to calculate connectivity (frequently called
"accessibility" in the literature). The IDs of shapefiles can be found by accessing `/api/shapefile`, which will give a
JSON document detailing all of the scenarios. The `graphId` is the ID of the scenario to use, which is the `id`
attribute in `/api/scenario`. `profile` indicates that the request should use profile routing, which calculates not the
travel time and connectivity at a particular time but rather the guaranteed, expected and possible connectivity over a
time window (see [this post](http://conveyal.com/blog/2015/05/04/variation-in-accessibility-measures/) for more details
on these types of connectivity).

The `options` field is a JSONified OTP [profile request](https://github.com/opentripplanner/OpenTripPlanner/blob/master/src/main/java/org/opentripplanner/profile/ProfileRequest.java).
`fromLat` and `fromLon` are the origins of the search. `toLat` and `toLon` are not used but must be specified; the simplest
thing to do is to just set them the same as `fromLat` and `fromLon`. `date` is the date of the search, YYYY-MM-DD.

`fromTime` and `toTime` specify the time window of the search, in seconds since local midnight. (Techinically, seconds
since local noon minus 12 hours, but this is midnight except on days when daylight savings time is activated or
deactivated. I'd recommend   not doing analysis on those days anyhow.) So 25200 is 7 AM (7 hours * 60 minutes/hour * 60
seconds/minute = 25200) and 32400 is 9 AM.

`bannedRoutes` specifies routes that should not be used by this request. It is formatted as `agencyid_routeid` using the
entities from the GTFS feeds. Be aware that this is currently affected by [OTP issue 1755](https://github.com/opentripplanner/OpenTripPlanner/issues/1755),
which means that all routes take on the ID of the first agency in their feed.

The remainder of the parameters are documented in the [OTP API
documentation](http://dev.opentripplanner.org/javadoc/master/org/opentripplanner/profile/ProfileRequest.html). They
currently do not have defaults, but the values specified in the examples above are reasonable.

Bicycle and bike-to-transit searches are not currently supported in profile mode, but this is planned in the future.
They will be specified by changing the `accessModes` (the modes used access transit stops at the start of the trip)
and the `egressModes` (the modes used to leave transit at the end of the trip).

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