var Analyst = Analyst || {};

(function(A, $) {

	A.analysis.AnalysisSinglePointLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('analysis', 'analysis-single-point'),

		events: {
		  'change #scenarioComparison': 'selectComparisonType',
		  'change #scenario1': 'updateResults',
		  'change #scenario2': 'updateResults',
		  'change #shapefile': 'updateResults',
			'cahnge .timesel': 'updateResults',
			'change #shapefileColumn1': 'updateCharts',
			'change #shapefileColumn2': 'updateCharts',
		  'change #chartType' : 'updateResults',
			'change .which input' : 'updateEnvelope',
			'change #shapefile' : 'updateAttributes',
			'change .isochrone' : 'updateIsochrone',
		  'click #showIso': 'updateMap',
		  'click #showPoints': 'updateMap',
		  'click #showTransit': 'updateMap',
		  'change .mode-selector' : 'updateResults',
			'change .which-selector': 'updateResults',
			'click #showSettings' : 'showSettings',
			'click #downloadGis' : 'downloadGis',
			'click #downloadCsv' : 'downloadCsv',
			'change #useMaxFare' : 'selectMaxFare',
			'blur #maxFare1': 'updateResults',
			'blur #maxFare2': 'updateResults'
		},

		regions: {
			analysisDetail: '#detail'
		},

		initialize: function(options){
			_.bindAll(this, 'updateResults', 'updateMap', 'onMapClick', 'updateAttributes', 'updateCharts', 'selectMaxFare');

			this.transitOverlays = {};
		},

		isochroneStyle: function(seconds) {
		    var style = {
		      color: '#333',
		      fillColor: '#333',
		      lineCap: 'round',
		      lineJoin: 'round',
		      weight: 1.5,
		      dashArray: '5, 5',
		      fillOpacity: '0.05'
		    };
		    if (seconds == 3600) {
		      style.weight = 1.5;
		    } else {
		      style.weight = 1.5;
		    }
		    return style;
		},

		onShow : function() {

			var _this = this;

			this.$('#date').datetimepicker({ format: 'l', locale: moment.locale() })
				.on('dp.hide', this.updateResults);
			this.$('#fromTime').datetimepicker({ format: 'LT', locale: moment.locale() })
				.on('dp.hide', this.updateResults);
			this.$('#toTime').datetimepicker({ format: 'LT', locale: moment.locale() })
				.on('dp.hide', this.updateResults);

			// pick a reasonable default date
			$.get('api/project/' + A.app.selectedProject + '/exemplarDay')
			.done(function (data) {
				var $d = _this.$('#date');

				var sp = data.split('-');
				// months are off by one in javascript
				var date = new Date(sp[0], sp[1] - 1, sp[2]);

				_this.$('#date').data('DateTimePicker').date(date);
			});

			// set default times
			this.$('#fromTime').data('DateTimePicker').date(new Date(2014, 11, 15, 7, 0, 0));
			this.$('#toTime')  .data('DateTimePicker').date(new Date(2014, 11, 15, 9, 0, 0));

			this.$('.scenario2-controls').hide()
			this.$('.fare-controls').hide()

			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);


			if(A.map.marker && A.map.hasLayer(A.map.marker))
	  			A.map.removeLayer(A.map.marker);

	  		A.map.on('click', this.onMapClick);

			this.shapefiles = new A.models.Shapefiles();
			this.scenarios = new A.models.Scenarios();

			this.timeSlider = this.$('#timeSlider1').slider({
					formater: function(value) {
						return window.Messages("analysis.n-minutes", value);
					}
				}).on('slideStop', function(evt) {

					_this.$('#timeRangeValue').html(window.Messages("analysis.travel-time-range", evt.value[0], evt.value[1]));
  				_this.updateMap();
			}).data('slider');

			_this.$('#timeRangeValue').html(window.Messages("analysis.travel-time-range", 0, 60));

			this.walkSpeedSlider = this.$('#walkSpeedSlider').slider({
					formater: function(value) {
						_this.$('#walkSpeedValue').html(window.Messages("analysis.average-walk-speed", value));
						return window.Messages("analysis.km-per-hour", value)
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.walkTimeSlider = this.$('#walkTimeSlider').slider({
					formater: function(value) {
						_this.$('#walkTimeValue').html(window.Messages("analysis.walk-time", value));
						return window.Messages("analysis.n-minutes", value);
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.bikeSpeedSlider = this.$('#bikeSpeedSlider').slider({
					formater: function(value) {
						_this.$('#bikeSpeedValue').html(window.Messages("analysis.average-bike-speed", value));
						return window.Messages("analysis.km-per-hour", value)
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.bikeTimeSlider = this.$('#bikeTimeSlider').slider({
					formater: function(value) {
						_this.$('#bikeTimeValue').html(window.Messages("analysis.bike-time", value));
						return window.Messages("analysis.n-minutes", value);
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.maxTransfersSlider = this.$('#maxTransfersSlider').slider({
					formater: function(value) {
						_this.$('#maxTransfersValue').html(window.Messages("analysis.max-transfers", value));
						return window.Messages("analysis.max-transfers", value);
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.reachabilityThresholdSlider = this.$('#reachabilityThresholdSlider').slider({
					formater: function(value) {
						var pct = Math.round(value * 100);
						_this.$('#reachabilityThresholdValue').html(window.Messages("analysis.reachability-threshold", pct));
						return pct + '%';
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.iterationSlider = this.$('#iterationSlider').slider({
					formater: function (value) {
						_this.$('#iterationValue').html(window.Messages("analysis.monte-carlo-draws", value));
						return value
					} 
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.ltsSlider1 = this.$('#ltsSlider1').slider({
					formater: function (value) {
						_this.$('#ltsValue1').html(window.Messages("analysis.bike-lts-scenario-1", value));
						return value
					} 
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.ltsSlider2 = this.$('#ltsSlider2').slider({
					formater: function (value) {
						_this.$('#ltsValue2').html(window.Messages("analysis.bike-lts-scenario-2", value));
						return value
					} 
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.mode = 'TRANSIT,WALK';

			this.shapefiles.fetch({reset: true, data : {projectId: A.app.selectedProject}})
				.done(function () {
				_this.$("#primaryIndicator").empty();

				_this.shapefiles.each(function (shp) {
					$('<option>')
						.attr('value', shp.id)
						.text(shp.get('name'))
						.appendTo(this.$('#shapefile'));
				});

				_this.updateAttributes();
			});

			this.scenarios.fetch({reset: true, data : {projectId: A.app.selectedProject}, success: function(collection, response, options){

				_this.$(".scenario-list").empty();

				for(var i in _this.scenarios.models) {
					if(_this.scenarios.models[i].get("id") == "default")
						_this.$(".scenario-list").append('<option selected value="' + _this.scenarios.models[i].get("id") + '">' + _this.scenarios.models[i].get("name") + '</option>');
					else
						_this.$(".scenario-list").append('<option value="' + _this.scenarios.models[i].get("id") + '">' + _this.scenarios.models[i].get("name") + '</option>');

				}

			}});

			this.$('#comparisonChart').hide();
			this.$('#compareLegend').hide();
			this.$('#isoLegend').hide();

			this.$('#queryProcessing').hide();
			this.$('#initializingCluster').hide();
			this.$('#requestFailed').hide();
			this.$('#insufficientQuota').hide();
			this.$('#showSettings').hide();
			this.$('#queryResults').hide();

		},

		/**
		* Update the attributes select to show the attributes of the current shapefile
		*/
		updateAttributes: function () {
			var shpId = this.$('#shapefile').val();
			var shp = this.shapefiles.get(shpId);
			var _this = this;

			this.$('#shapefileColumn1').empty();
			this.$('#shapefileColumn2').empty();

			$('<option>')
				.text(window.Messages('analysis.same-field'))
				.appendTo(this.$('#shapefileColumn2'))

			shp.getNumericAttributes().forEach(function (attr) {
				var atName = A.models.Shapefile.attributeName(attr);

				if(!attr.hide) {
					$('<option>')
					.attr('value', attr.fieldName)
					.text(atName)
					.appendTo(_this.$('#shapefileColumn1'));

					$('<option>')
					.attr('value', attr.fieldName)
					.text(atName)
					.appendTo(_this.$('#shapefileColumn2'));
				}
			});
		},

		/** show/hide shapefile selector based on isochrone selection */
		updateIsochrone: function () {
			var isochrone = this.$('input[name="isochrone"]:checked').val() == 'true';

			if (isochrone)
				this.$('#shapefile-group').hide();
			else
				this.$('#shapefile-group').show();

			this.updateResults();
		},

		onClose : function() {
			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);

		  	if(A.map.marker && A.map.hasLayer(A.map.marker))
		  		A.map.removeLayer(A.map.marker);

		  	if(A.map.isochronesLayer  && A.map.hasLayer(A.map.isochronesLayer))
		  		A.map.removeLayer(A.map.isochronesLayer);

				if (A.map.utfOverlay && A.map.hasLayer(A.map.utfOverlay))
					A.map.removeLayer(A.map.utfOverlay);

				if (A.map.valueReadout) {
					A.map.removeControl(A.map.valueReadout);
					A.map.valueReadout = false;
				}

			for(var id in this.transitOverlays){
				if(this.transitOverlays[id] && A.map.hasLayer(this.transitOverlays[id]))
					A.map.removeLayer(this.transitOverlays[id]);
			}

		  	A.map.off('click', this.onMapClick);

		  	A.map.marker = false;

		 },

		updateResults : function() {
			if(!A.map.marker)
				return;

			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);

		  	this.barChart1 = false;
		  	this.barChart2 = false;

		  	this.scenario1Data = false;
		  	this.scenario2Data = false;

				if (this.$('input[name="isochrone"]:checked').val() === 'true') {
					this.$('#chart, #chartLegend').hide();

					if(this.comparisonType == 'compare') {
						this.$('#compareLegend').hide();
						this.$('#singleLegend').hide();
						this.$('#isoSingleLegend').hide();
						this.$('#isoComparisonLegend').show();
					}
					else {
						this.$('#comparisonChart').hide();
						this.$('#compareLegend').hide();
						this.$('#singleLegend').hide();
						this.$('#isoSingleLegend').show();
						this.$('#isoComparisonLegend').hide();
					}
				}

				else {
					this.$('#chart, #chartLegend').show();

			  	if(this.comparisonType == 'compare') {
			  		this.$('#comparisonChart').show();
			  		this.$('#compareLegend').show();
			  		this.$('#singleLegend').hide();
						this.$('#isoSingleLegend').hide();
						this.$('#isoComparisonLegend').hide();
			  	}
			  	else {
			  		this.$('#comparisonChart').hide();
			  		this.$('#compareLegend').hide();
						this.$('#singleLegend').show();
						this.$('#isoSingleLegend').hide();
						this.$('#isoComparisonLegend').hide();
					}
				}

		  	var bikeSpeed = (this.bikeSpeedSlider.getValue() * 1000 / 60 / 60 );
		  	var walkSpeed = (this.walkSpeedSlider.getValue() * 1000 / 60 / 60 );
			var walkTime = this.walkTimeSlider.getValue();
			var bikeTime = this.bikeTimeSlider.getValue();
			var reachabilityThreshold = this.reachabilityThresholdSlider.getValue();
			var iterations = this.iterationSlider.getValue()
			var maxRides = this.maxTransfersSlider.getValue() + 1 // One more ride than transfer, but transfers are more understandable

 			this.scenario1 = this.scenarios.get(this.$('#scenario1').val());
			this.scenario2 = this.scenarios.get(this.$('#scenario2').val());

			var _this = this;

			this.$('#querySettings').hide();
			this.$('#showSettings').show();
			this.$('#queryResults').hide();
			this.$('#requestFailed').hide();
			this.$('#insufficientQuota').hide();
			
			// don't flip to processing if the cluster is still initializing
			if (!this.initializingCluster) {
				this.$('#queryProcessing').show();
				this.$('#initializingCluster').hide();
			}

			this.initializingCluster = false;

			this.mode = this.$('input[name="mode"]:checked').val();

			var date = this.$('#date').data('DateTimePicker').date().format('YYYY-MM-DD');
			var fromTime = A.util.makeTime(this.$('#fromTime').data('DateTimePicker').date());

			var dateTime = '&date=' + date + '&fromTime=' + fromTime;

			if (A.util.isTransit(this.mode))
				dateTime += '&toTime=' + A.util.makeTime(this.$('#toTime').data('DateTimePicker').date());

			var params1 = 'graphId=' + this.graphId1 + '&lat=' + A.map.marker.getLatLng().lat + '&lon=' +
				A.map.marker.getLatLng().lng + '&mode=' + this.mode + '&bikeSpeed=' + bikeSpeed + '&walkSpeed=' + walkSpeed +
	 			dateTime + '&shapefile=' + this.$('#shapefile').val() +
				'&profile=' + this.$('input.profile:checked').val();

			// TODO probably not the best place for a bunch of defaults
			var isochrone = this.$('input[name="isochrone"]:checked').val() == 'true';
			var profile = this.$('input.profile:checked').val() == "true";
			var params = {
				// if we're requesting only isochrones, don't pass in a pointset
				destinationPointsetId: isochrone ? null : this.$('#shapefile').val(),
				graphId: this.scenario1.get('bundleId')
			};

			var mods1 = []

			if (this.scenario1.get('modifications'))
				mods1 = mods1.concat(this.scenario1.get('modifications'));

			if (window.modifications1)
				mods1 = mods1.concat(window.modifications1);

			if (!isochrone && !params.destinationPointsetId)
				// this can happen when the shapefiles have not yet loaded
				return;

			// The type field clarifies that this is an analyst request
			// rather than a request for a single origin point for a static site.
			params.type = "analyst";

			var maxFare1 = this.$('#useMaxFare').prop('checked') ? parseInt(this.$('#maxFare1').val()) : undefined
			var maxFare2 = this.$('#useMaxFare').prop('checked') ? parseInt(this.$('#maxFare2').val()) : undefined


			// We always send a profile request, but if we're not using transit the window will be treated as zero-width.
			params.profileRequest = {
				fromLat:  A.map.marker.getLatLng().lat,
				fromLon: A.map.marker.getLatLng().lng,
				toLat:  A.map.marker.getLatLng().lat,
				toLon: A.map.marker.getLatLng().lng,
				date: date,
				fromTime:  A.util.makeTime(this.$('#fromTime').data('DateTimePicker').date()),
				toTime: A.util.makeTime(this.$('#toTime').data('DateTimePicker').date()),
				accessModes: A.util.removeTransit(this.mode),
				directModes: A.util.removeTransit(this.mode),
				egressModes: 'WALK',
				transitModes: this.mode,
				walkSpeed: walkSpeed,
				bikeSpeed: bikeSpeed,
				carSpeed: 20,
				streetTime: 90,
				maxWalkTime: walkTime,
				maxBikeTime: bikeTime,
				maxFare: maxFare1,
				maxCarTime: 45,
				minBikeTime: 10,
				minCarTime: 10,
				suboptimalMinutes: 5,
				reachabilityThreshold: reachabilityThreshold,
				analyst: true,
				bikeSafe: 1,
				bikeSlope: 1,
				bikeTime: 1,
				maxRides: maxRides,
				bikeTrafficStress: this.ltsSlider1.getValue(),
				// use monte carlo at all times; it also produces true best/worst case numbers.
				boardingAssumption: 'RANDOM',
				monteCarloDraws: iterations,
				scenario: {
				    id: this.scenario1.get('id'),
					modifications: mods1
				}
			}

			// called when we get a 503 back from the cluster, which indicates that workers are starting
			var unavailableCallback = function () {
				_this.$('#queryProcessing').hide();
				_this.$('#requestFailed').hide();
				_this.$('#insufficientQuota').hide();
				_this.$('#initializingCluster').show();
				// note this so that we don't show processing query when we call it again and get a 503 back
				_this.initializingCluster = true;

				// call back in 15 seconds
				// TODO updateResults gets called twice on comparison queries
				setTimeout(_this.updateResults, 15000, _this);
			};

		    var p1 = $.ajax({
					url: '/api/single',
					data: JSON.stringify(params),
					contentType: 'application/json',
					method: 'post'
				});

		    if (this.comparisonType == 'compare') {
					// ok to be destructive - we've already stringified the request
					params.graphId = this.scenario2.get('bundleId');

					var mods2 = []

					if (this.scenario2.get('modifications'))
						mods2 = mods2.concat(this.scenario2.get('modifications'));

					if (window.modifications2)
						mods2 = mods2.concat(window.modifications2);

					params.profileRequest.scenario = { modifications : mods2, id: this.scenario2.get('id') };
					params.profileRequest.maxFare = maxFare2;
					params.profileRequest.bikeTrafficStress = this.ltsSlider2.getValue()

					var p2 = $.ajax({
						url: '/api/single',
						data: JSON.stringify(params),
						contentType: 'application/json',
						method: 'post'
					});

					// make sure they both get set at once, UI should never get out of sync
					$.when(p1, p2).done(function (d1, d2) {
						_this.scenario1Data = d1[0];
						_this.scenario2Data = d2[0];

						_this.updateMap();
						_this.updateCharts();

						// update the progress bar for how many origin credits have been used
						A.app.user.fetch();
					})
					.fail(function (a, b) {
						if (a.status == 503 || b.status == 503) {
							unavailableCallback();
						} else {
							_this.$('#queryProcessing').hide();
							_this.$('#insufficientQuota').hide();
							_this.$('#requestFailed').hide();
							_this.$('#initializingCluster').hide();
							if (a.status == 403 && a.responseText == 'INSUFFICIENT_QUOTA'|| b.status == 403 && b.responseText == 'INSUFFICIENT_QUOTA') {
							  _this.$('#insufficientQuota').show();
								// refetch user to ensure that quota readout is up to date
								A.app.user.fetch();
							} else {
								_this.$('#requestFailed').show();
							}
						}
					});

		    }
				else {
					// make sure they both get set at once, UI should never get out of sync
					$.when(p1).then(function (d1) {
						_this.scenario1Data = d1;
						_this.scenario2Data = false;

						_this.updateMap();
						_this.updateCharts();

						// update the progress bar for how many origin credits have been used
						A.app.user.fetch();
					})
					.fail(function (err) {
						if (err.status == 503) {
							unavailableCallback();
						} else {
							_this.$('#queryProcessing').hide();
							_this.$('#insufficientQuota').hide();
							_this.$('#requestFailed').hide();
							_this.$('#initializingCluster').hide();
							if (err.status == 403 && err.responseText == 'INSUFFICIENT_QUOTA') {
								_this.$('#insufficientQuota').show();
								// refetch user to ensure that quota readout is up to date
								A.app.user.fetch();
							} else {
								_this.$('#requestFailed').show();
							}
						}
					});
				}
		},

		/**
		 * Draw the charts
		 */
		updateCharts: function () {
			this.$('#queryProcessing').hide();
			this.$('#initializingCluster').hide();
			this.$('#requestFailed').hide();
			this.$('#insufficientQuota').hide();
			this.$('#queryResults').show();

			if (this.scenario1Data.isochrones === undefined) {
				// draw accessibility plots only if there is accessibility
				var categoryId = this.shapefiles.get(this.$("#shapefile").val()).get('categoryId');
				var attributeId1 = this.$('#shapefileColumn1').val();
				var attributeId2 = this.$('#shapefileColumn2').val();

                // item zero is (same field)
				if (this.$('#shapefileColumn2').get(0).selectedIndex === 0) attributeId2 = attributeId1

				if (this.scenario2Data) {
					this.drawChart(categoryId + '.' + attributeId1, this.scenario1Data, categoryId + '.' + attributeId2, this.scenario2Data);
					this.$('#downloadCsv').hide();
				} else {
					this.drawChart(categoryId + '.' + attributeId1, this.scenario1Data);
					this.$('#downloadCsv').show();
				}
			}
		},

		updateMap : function() {
			var _this = this;

			var showTransit =  this.$('#showTransit').prop('checked');

			this.comparisonType = this.$('.scenario-comparison').val();

			if(!(this.scenario1Data && (this.scenario2Data || this.comparisonType != 'compare')))
				return;

			$('#results1').hide();
			$('#results2').hide();

			var timeLimit = this.getTimeLimit();

			var showIso =  this.$('#showIso').prop('checked');
			var showPoints = false;//this.$('#showPoints').prop('checked');
			var which = this.$('input[name="which"]:checked').val();

			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
				A.map.removeLayer(A.map.tileOverlay);

			var lcWhich;

			switch (which) {
				case 'WORST_CASE':
					lcWhich = 'worstCase';
					break;
				case 'AVERAGE':
				case 'POINT_ESTIMATE':
					lcWhich = 'pointEstimate';
					break;
				case 'BEST_CASE':
					lcWhich = 'bestCase';
					break;
				case 'SPREAD':
					lcWhich = 'spread';
					break;
			}

			// show vector isochrones
			if (this.scenario1Data.isochrones !== undefined) {
				// find the appropriate isochrones
				var iso1 = _.find(this.scenario1Data.isochrones[lcWhich].features, function (iso) {
					return iso.properties.time == timeLimit;
				});

				var geom1 = L.GeoJSON.geometryToLayer(iso1.geometry);

				geom1.setStyle({
					opacity: 0.75,
					color:'#e5b234',
					weight: 1,
					fillColor: '#e5b234',
					fillOpacity: 0.15
				});

				if (this.comparisonType == 'compare') {
					var iso2 = _.find(this.scenario2Data.isochrones[lcWhich].features, function (iso) {
						return iso.properties.time == timeLimit;
					});

					var geom2 = L.GeoJSON.geometryToLayer(iso2.geometry);

					geom2.setStyle({
						opacity: 0.75,
						weight: 1,
						color: '#00c',
						fillColor: '#00c',
						fillOpacity: 0.15
					});

					// make them into a feature collection
					// put the second isochrone on the bottom, because it is usually larger (increased service)
					A.map.tileOverlay = L.featureGroup([geom1, geom2]).addTo(A.map);
				}
				else {
					A.map.tileOverlay = geom1.addTo(A.map);
				}
			}
			else {
				if(this.comparisonType == 'compare') {

					if(!this.scenario1Data || !this.scenario2Data)
						return;

					var tileUrl = '/tile/single/' + this.scenario1Data.key + '/' + this.scenario2Data.key + '/{z}/{x}/{y}.png' +
						'?showIso=' + showIso +
						'&showPoints=' +  showPoints + '&timeLimit=' + timeLimit +
						'&which=' + which;

				  var utfUrl = '/tile/single/' + this.scenario1Data.key + '/' + this.scenario2Data.key + '/{z}/{x}/{y}.json' +
						'?showIso=' + showIso +
						'&showPoints=' +  showPoints + '&timeLimit=' + timeLimit +
						'&which=' + which;

					A.map.tileOverlay = L.tileLayer(tileUrl)
						.addTo(A.map);

					if(A.map.utfOverlay && A.map.hasLayer(A.map.utfOverlay))
							A.map.removeLayer(A.map.utfOverlay);

					if (A.map.valueReadout) {
						A.map.removeControl(A.map.valueReadout);
						A.map.valueReadout = false;
					}

					// readout control: see http://leafletjs.com/examples/choropleth.html
					A.map.valueReadout = L.control({
						position: 'bottomleft'
					});
					A.map.valueReadout.onAdd = function (map) {
						this._div = L.DomUtil.create('div', 'valueReadout');
						this.update();
						return this._div;
					}
					A.map.valueReadout.update = function(val) {
						if (!val) {
							this._div.innerHTML = '-';
							return;
						}

						val = Math.round(val / 60);

						this._div.innerHTML = window.Messages('analysis.change-in-time', val);
					}
					A.map.valueReadout.addTo(A.map);

					A.map.utfOverlay = new L.UtfGrid(utfUrl, {
						resolution: 4,
						useJsonP: false
					})
					.on('mouseover', function (e) {
						A.map.valueReadout.update(e.data);
					})
					.on('mouseout', function (e) {
						A.map.valueReadout.update();
					});

					A.map.addLayer(A.map.utfOverlay);

				}
				else {

					if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
			  			A.map.removeLayer(A.map.tileOverlay);

					if (A.map.utfOverlay && A.map.hasLayer(A.map.utfOverlay))
						A.map.removeLayer(A.map.utfOverlay);

					if (A.map.valueReadout) {
						A.map.removeControl(A.map.valueReadout);
						A.map.valueReadout = false;
					}

					A.map.tileOverlay = L.tileLayer('/tile/single/' + this.scenario1Data.key + '/{z}/{x}/{y}.png?showIso=' + showIso +
						'&showPoints=' +  showPoints + '&timeLimit=' + timeLimit + '&which=' + which, {})
						.addTo(A.map);

				}
			}

			if(showTransit) {
				if(this.comparisonType == 'compare') {

					var scenarioId1 = this.$('#scenario1').val();

					if(A.map.hasLayer(this.transitOverlays[scenarioId1]))
						A.map.removeLayer(this.transitOverlays[scenarioId1]);

					// manual setting of z-indices to get layer ordering right: http://stackoverflow.com/questions/12848812
					this.transitOverlays[scenarioId1] = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&scenarioId=' + scenarioId1)
						.setZIndex(7)
						.addTo(A.map);

					var scenarioId2 = this.$('#scenario2').val();

					var compareKey = scenarioId1 + "_ " + scenarioId2;

					if(A.map.hasLayer(this.transitOverlays[compareKey]))
						A.map.removeLayer(this.transitOverlays[compareKey]);

					this.transitOverlays[compareKey] = L.tileLayer('/tile/transitComparison?z={z}&x={x}&y={y}&scenarioId1=' + scenarioId1 + '&scenarioId2=' + scenarioId2)
						.setZIndex(8).addTo(A.map);

				}
				else {

					var scenarioId = this.$('#scenario1').val();

					if(A.map.hasLayer(this.transitOverlays[scenarioId]))
						A.map.removeLayer(this.transitOverlays[scenarioId]);

					this.transitOverlays[scenarioId] = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&scenarioId=' + scenarioId).setZIndex(7).addTo(A.map);

				}
			}
			else {

				for(var id in this.transitOverlays){
					if(this.transitOverlays[id] && A.map.hasLayer(this.transitOverlays[id]))
						A.map.removeLayer(this.transitOverlays[id]);
				}

			}
		},

		/** get the current position of the time limit slider */
		getTimeLimit: function () {
			return this.timeSlider.getValue()[1] * 60;
		},

		downloadGis : function(evt) {
			var which = this.$('input[name="which"]:checked').val();


			if (this.scenario2Data)
				window.location.href = '/gis/singleComparison?key1=' + this.scenario1Data.key + '&key2=' + this.scenario2Data.key + '&which=' + which;
			else
				window.location.href = '/gis/single?key=' + this.scenario1Data.key + '&which=' + which;

		},

		downloadCsv : function(evt) {
			var which = this.$('input[name="which"]:checked').val();

			if (this.scenario2Data)
				// comparisons not supported
				evt.preventDefault();
			else
					window.location.href = '/csv/single?key=' + this.scenario1Data.key + '&which=' + which;

		},

		showSettings : function(evt) {

			this.$('#showSettings').hide();
			this.$('#querySettings').show();
		},

		drawChart : function(attribute1, result1, attribute2, result2) {
			// ensure we don't make a mess.
			this.$('#chart').empty();
			this.$('#chartLegend').empty();

			// pivot the data into an object array for MetricsGraphics and make a cumulative distribution
			var plotData = this.getPlotData(result1, attribute1);
			var max = plotData[119].bestCase !== undefined ? plotData[119].bestCase : plotData[119].pointEstimate;

			// this is how you make a multi-line plot with metricsgraphics
			if (result2) {
				plotData = [plotData, this.getPlotData(result2, attribute2)];
				max = Math.max(max, plotData[1][119].bestCase !== undefined ? plotData[1][119].bestCase : plotData[1][119].pointEstimate);
			}

			var fmt = d3.format();

			let label1 = result1.properties.schema[attribute1].label
			let label2 = attribute2 ? result2.properties.schema[attribute2].label : null

			MG.data_graphic({
				title: window.Messages('analysis.accessibility-to', label2 && label1 !== label2 ? label1 + '/' + label2 : label1),
				width: 400,
				height: 225,
				data: plotData,
				max_y: max,
				target: '#chart',
				area: false,
				y_accessor: 'pointEstimate',
				x_accessor: 'minute',
				x_label: window.Messages('analysis.minutes'),
				max_x: 120,
				bottom: 40,
				show_confidence_band: ['worstCase', 'bestCase'],
				mouseover: function (d, i) {
					$('#chart svg .mg-active-datapoint')
						.text(window.Messages('analysis.graph-mouseover', d.minute, fmt(d.worstCase), fmt(d.pointEstimate), fmt(d.bestCase)));
				},
				show_rollover_text: false,
				legend: [window.Messages('analysis.scenario-1'), window.Messages('analysis.scenario-2')],
				legend_target: '#chartLegend'
			});
		},

		/** get data for metricsgraphics from a result query */
		getPlotData: function(result, attribute) {
		  var plotData = [];
		  var histograms = result.data[attribute];

		  // make cumulative distributions
		  var cWorst = 0,
		    cEst = 0,
		    cBest = 0;

		  for (var i = 0; i < 120; i++) {
		    plotData[i] = {};

		    if (histograms.worstCase !== undefined)
		      cWorst = plotData[i].worstCase = cWorst + (histograms.worstCase.sums[i] !== undefined ? histograms.worstCase.sums[
		        i] : 0);

		    if (histograms.pointEstimate !== undefined)
		      cEst = plotData[i].pointEstimate = cEst + (histograms.pointEstimate.sums[i] !== undefined ? histograms.pointEstimate
		        .sums[i] : 0)

		    if (histograms.bestCase !== undefined)
		      cBest = plotData[i].bestCase = cBest + (histograms.bestCase.sums[i] !== undefined ? histograms.bestCase.sums[i] :
		        0)

				// the 0th entry in the marginal array is marginal accessibility from 0-1 minutes, or cumulative
				// accessibility within 1 minute
		    plotData[i].minute = i + 1;
		  }

			return plotData;
		},

		/*updateSummary : function() {

			this.$("#resultSummary").append("<tr><td></td></tr>");


		},*/

		scaleBarCharts : function() {

			if(this.barChart1)
				this.barChart1.y(d3.scale.linear().domain([0, this.maxChartValue]));

			if(this.barChart2)
				this.barChart2.y(d3.scale.linear().domain([0, this.maxChartValue]));
		},

		onRender : function() {

			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
	  			A.map.removeLayer(A.map.tileOverlay);

			var _this = this;

			if(A.map.marker && A.map.hasLayer(A.map.marker))
	  			A.map.removeLayer(A.map.marker);

	  		A.map.on('click', this.onMapClick);

		},

		onMapClick : function(evt) {

	  		if(A.map.marker && A.map.hasLayer(A.map.marker))
	  			return;

	  		A.map.marker = new L.marker(evt.latlng, {draggable:'true'});

	  		A.map.marker.on('dragend', this.updateResults);

		    	A.map.addLayer(A.map.marker);

	    		this.updateResults();

		},

      	selectComparisonType : function(evt) {

			this.comparisonType = this.$('#scenarioComparison').val();

			if(this.comparisonType == 'compare') {
				$('.scenario2-controls').show();
			}
			else {
				$('.scenario2-controls').hide();
			}

			this.updateResults();
		},

		/** toggle max fare on or off */
		selectMaxFare: function (e) {
			this.useMaxFare = this.$('#useMaxFare').prop('checked')

			if (this.useMaxFare) $('.fare-controls').show()
			else $('.fare-controls').hide()

			this.updateResults()
		}
	});

})(Analyst, jQuery);
