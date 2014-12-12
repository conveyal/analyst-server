var Analyst = Analyst || {};

(function(A, $) {

	A.analysis.AnalysisSinglePointLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('analysis', 'analysis-single-point'),

		events: {
		  'change #scenarioComparison': 'selectComparisonType',
		  'change #scenario1': 'createSurface',
		  'change #scenario2': 'createSurface',
		  'change #shapefile': 'createSurface',
			'change #shapefileColumn': 'createSurface',
		  'change #chartType' : 'updateResults',
			'change .which1 input' : 'updateEnvelope',
			'change .which2 input' : 'updateEnvelope',
			'change #shapefile' : 'updateAttributes',
		  'click #showIso': 'updateMap',
		  'click #showPoints': 'updateMap',
		  'click #showTransit': 'updateMap',
		  'click .mode-selector' : 'updateMap',
		},

		regions: {
			analysisDetail: '#detail'
		},

		initialize: function(options){
			_.bindAll(this, 'createSurface', 'updateMap', 'onMapClick', 'updateEnvelope', 'updateAttributes');

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

		/**
		 * Update the best case, worst case, etc. that the user can choose
		 * based on the modes. For example, for on-street modes, there is no
		 * best or worst case; for transit modes, there is currently no point estimate.
		 */
		updateAvailableEnvelopeParameters: function () {
			// we use this variable so that the map is not automatically redrawn when
			// we check checkboxes programatically
			this.envelopeParametersChangingProgramatically = true;

			// do it for the modes for both the baseline and the comparison
			for (var i = 1; i <= 2; i++) {
				var mode =  this['mode' + i];
				var inps = this.$('.which' + i);

				if (mode.includes('TRANSIT') || mode.includes('TRAINISH') || mode.includes('BUSISH') ||
							mode.includes('FERRY') || mode.includes('FUNICULAR') || mode.includes('GONDOLA') ||
							mode.includes('CABLE_CAR') || mode.includes('RAIL') || mode.includes('SUBWAY') ||
							mode.includes('TRAM') || mode.includes('BUS')) {
					// transit request, we're doing profile routing
					inps.find('[value="WORST_CASE"]').prop('disabled', false).parent().removeClass('hidden');
					inps.find('[value="BEST_CASE"]').prop('disabled', false).parent().removeClass('hidden');
					inps.find('[value="SPREAD"]').prop('disabled', true).parent().addClass('hidden');
					inps.find('[value="POINT_ESTIMATE"]').prop('disabled', true).parent().addClass('hidden');

					if (inps.find(':checked:disabled').length > 0 || inps.find(':checked').length == 0) {
						// we have disabled the currently selected envelope parameter, choose a reasonable default
						inps.find('input').prop('checked', false).parent().removeClass('active');
						inps.find('[value="WORST_CASE"]').prop('checked', true).parent().addClass('active');
					}
				} else {
					// non-transit request, we're doing vanilla routing with point estimates only
					inps.find('[value="WORST_CASE"]').prop('disabled', true).parent().addClass('hidden');
					inps.find('[value="BEST_CASE"]').prop('disabled', true).parent().addClass('hidden');
					inps.find('[value="SPREAD"]').prop('disabled', true).parent().addClass('hidden');

					// since there is only one option, we may as well go ahead and check it
					inps.find('[value="POINT_ESTIMATE"]')
						.prop('disabled', false)
						.prop('checked', true)
						.parent()
						.removeClass('hidden')
						.addClass('active');
					}
			 }

			this.envelopeParametersChangingProgramatically = false;
		},

		/**
		 * Event handler to update the envelope parameters
		 */
		updateEnvelope : function (e) {
			// prevent it from being run twice: once for uncheck and once for check
			if (e.target.checked && this.envelopeParametersChangingProgramatically !== true) {
				this.createSurface();
			}
		},

		onShow : function() {

			var _this = this;

			this.$('#scenario2-controls').hide();

			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);


			if(A.map.marker && A.map.hasLayer(A.map.marker))
	  			A.map.removeLayer(A.map.marker);

	  		A.map.on('click', this.onMapClick);

			this.shapefiles = new A.models.Shapefiles();
			this.scenarios = new A.models.Scenarios();

			this.timeSlider = this.$('#timeSlider1').slider({
					formater: function(value) {
						return value + " minutes";
					}
				}).on('slideStop', function(evt) {

					_this.$('#minTimeValue').html(evt.value[0] + "");
					_this.$('#timeLimitValue').html(evt.value[1] + " mins");

				_this.updateResults(true)
				_this.updateMap();
			}).data('slider');

			this.$('#minTimeValue').html("0");
			this.$('#timeLimitValue').html("60 mins");

			this.walkSpeedSlider = this.$('#walkSpeedSlider').slider({
					formater: function(value) {
						_this.$('#walkSpeedValue').html(value + " km/h");
						return value + " km/h";
					}
				}).on('slideStop', function(value) {

				_this.createSurface();
			}).data('slider');

			this.bikeSpeedSlider = this.$('#bikeSpeedSlider').slider({
					formater: function(value) {
						_this.$('#bikeSpeedValue').html(value + " km/h");
						return value + " km/h";
					}
				}).on('slideStop', function(value) {

				_this.createSurface();
			}).data('slider');

			this.mode1 = 'TRANSIT,WALK';
			this.mode2 = 'TRANSIT,WALK';

			this.updateAvailableEnvelopeParameters();

			this.$('input[name=mode1]:radio').on('change', function(event) {
				_this.mode1 = _this.$('input:radio[name=mode1]:checked').val();
				_this.updateAvailableEnvelopeParameters();
				_this.createSurface();
		    });

		    this.$('input[name=mode2]:radio').on('change', function(event) {
				_this.mode2 = _this.$('input:radio[name=mode2]:checked').val();
				_this.updateAvailableEnvelopeParameters();
				_this.createSurface();
		    });

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

		},

		/**
		* Update the attributes select to show the attributes of the current shapefile
		*/
		updateAttributes: function () {
			var shpId = this.$('#shapefile').val();
			var shp = this.shapefiles.get(shpId);
			var _this = this;

			this.$('#shapefileColumn').empty();

			shp.getNumericAttributes().forEach(function (attr) {
				var atName = A.models.Shapefile.attributeName(attr);

				$('<option>')
				.attr('value', attr.fieldName)
				.text(atName)
				.appendTo(_this.$('#shapefileColumn'));
			});
		},

		onClose : function() {
			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);

		  	if(A.map.marker && A.map.hasLayer(A.map.marker))
		  		A.map.removeLayer(A.map.marker);

		  	if(A.map.isochronesLayer  && A.map.hasLayer(A.map.isochronesLayer))
		  		A.map.removeLayer(A.map.isochronesLayer);


			for(var id in this.transitOverlays){
				if(this.transitOverlays[id] && A.map.hasLayer(this.transitOverlays[id]))
					A.map.removeLayer(this.transitOverlays[id]);
			}

		  	A.map.off('click', this.onMapClick);

		  	A.map.marker = false;

		 },

		createSurface : function() {

			if(!A.map.marker)
				return;

			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);

		  	this.surfaceId1 = false;
		  	this.surfaceId2 = false;

		  	this.barChart1 = false;
		  	this.barChart2 = false;

		  	this.scenario1Data = false;
		  	this.scenario2Data = false;

		  	this.maxChartValue = 0;

		  	this.resetCharts();

		  	if(this.comparisonType == 'compare') {
		  		this.$('#comparisonChart').show();
		  		this.$('#compareLegend').show();
		  		this.$('#legend').hide();
		  	}
		  	else {
		  		this.$('#comparisonChart').hide();
		  		this.$('#compareLegend').hide();
		  		this.$('#legend').show();
		  	}


		  	var bikeSpeed = (this.bikeSpeedSlider.getValue() * 1000 / 60 / 60 );
		  	var walkSpeed = (this.walkSpeedSlider.getValue() * 1000 / 60 / 60 );

 			var graphId1 = this.$('#scenario1').val();
			var which1 = this.$('input[name="which1"]:checked').val();

			var _this = this;

			var surfaceUrl1 = '/api/surface?graphId=' + graphId1 + '&lat=' + A.map.marker.getLatLng().lat + '&lon=' +
				A.map.marker.getLatLng().lng + '&mode=' + this.mode1 + '&bikeSpeed=' + bikeSpeed + '&walkSpeed=' + walkSpeed +
				'&which=' + which1;

		    $.getJSON(surfaceUrl1, function(data) {

		  	  _this.surfaceId1 = data.id;

		  	  if(!this.comparisonType || _this.surfaceId2) {
		  	 	_this.updateMap();
		  	 	_this.updateResults();
		  	  }

		    });

		    if(this.comparisonType == 'compare') {

		    	var graphId2 = this.$('#scenario2').val();
					var which2 = this.$('input[name="which2"]:checked').val();

		    	var surfaceUrl2 = '/api/surface?graphId=' + graphId2 + '&lat=' + A.map.marker.getLatLng().lat + '&lon=' +
						A.map.marker.getLatLng().lng + '&mode=' + this.mode2 + '&bikeSpeed=' + bikeSpeed +
						'&walkSpeed=' + walkSpeed + '&which=' + which2;
		    	$.getJSON(surfaceUrl2, function(data) {

			  	  _this.surfaceId2 = data.id;

			  	  if(_this.surfaceId1) {
			  	  	_this.updateMap();
			  	  	_this.updateResults();
			  	  }

			    });

		    }
		},

		updateResults : function (timeUpdateOnly) {

			var _this = this;

			this.comparisonType = this.$('.scenario-comparison').val();

			if(this.comparisonType == 'compare') {

				if(this.surfaceId1) {
					var resUrl = '/api/result?shapefileId=' + this.$("#shapefile").val() + '&attributeName=' + this.$('#shapefileColumn').val() +
						'&surfaceId=' + this.surfaceId1 +	'&which=' + this.$('input[name="which1"]:checked').val();
					$.getJSON(resUrl, function(res) {

						_this.scenario1Data = res;
						_this.drawChart(res, 1, "#barChart1", 175);

					});
				}

				if(this.surfaceId2) {
					var resUrl = '/api/result?shapefileId=' + this.$("#shapefile").val() + '&attributeName=' + this.$('#shapefileColumn').val() +
					  '&surfaceId=' + this.surfaceId2 + '&which=' + this.$('input[name="which2"]:checked').val();
					$.getJSON(resUrl, function(res) {

						_this.scenario2Data = res;
						_this.drawChart(res, 2, "#barChart2", 175);

					});
				}
			}
			else {
				var resUrl = '/api/result?shapefileId=' + this.$("#shapefile").val() + '&attributeName=' + this.$('#shapefileColumn').val() +
					'&surfaceId=' + this.surfaceId1 +	'&which=' + this.$('input[name="which1"]:checked').val();
				$.getJSON(resUrl, function(res) {

					_this.drawChart(res, 1, "#barChart1", 175);

				});
			}
		},

		updateMap : function() {
			var _this = this;

			var showTransit =  this.$('#showTransit').prop('checked');

			this.comparisonType = this.$('.scenario-comparison').val();

			if(showTransit) {
				if(this.comparisonType == 'compare') {

					var scenarioId1 = this.$('#scenario1').val();

					if(A.map.hasLayer(this.transitOverlays[scenarioId1]))
			 			A.map.removeLayer(this.transitOverlays[scenarioId1]);

					this.transitOverlays[scenarioId1] = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&scenarioId=' + scenarioId1).addTo(A.map);

					var scenarioId2 = this.$('#scenario2').val();

					var compareKey = scenarioId1 + "_ " + scenarioId2;

					if(A.map.hasLayer(this.transitOverlays[compareKey]))
			 			A.map.removeLayer(this.transitOverlays[compareKey]);

					this.transitOverlays[compareKey] = L.tileLayer('/tile/transitComparison?z={z}&x={x}&y={y}&scenarioId1=' + scenarioId1 + '&scenarioId2=' + scenarioId2).addTo(A.map);

				}
				else {

					var scenarioId = this.$('#scenario1').val();

					if(A.map.hasLayer(this.transitOverlays[scenarioId]))
			 			A.map.removeLayer(this.transitOverlays[scenarioId]);

					this.transitOverlays[scenarioId] = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&scenarioId=' + scenarioId).addTo(A.map);

				}
			}
			else {

				for(var id in this.transitOverlays){
					if(this.transitOverlays[id] && A.map.hasLayer(this.transitOverlays[id]))
						A.map.removeLayer(this.transitOverlays[id]);
				}

			}

			if(!this.surfaceId1 && (this.surfaceId2 || this.comparisonType != 'compare'))
				return;

			$('#results1').hide();
			$('#results2').hide();

			var minTime = this.timeSlider.getValue()[0] * 60;
			var timeLimit = this.timeSlider.getValue()[1] * 60;

			var showIso =  this.$('#showIso').prop('checked');
			var showPoints = this.$('#showPoints').prop('checked');

			if(this.comparisonType == 'compare') {

				if(!this.surfaceId1 || !this.surfaceId2)
					return;

				if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  			A.map.removeLayer(A.map.tileOverlay);

				A.map.tileOverlay = L.tileLayer('/tile/surfaceComparison?z={z}&x={x}&y={y}&shapefileId=' + this.$('#shapefile').val() + '&attributeName=' + this.$('#shapefileColumn').val() +
					'&minTime=' + minTime + '&timeLimit=' + timeLimit + '&surfaceId1=' + this.surfaceId1  + '&surfaceId2=' + this.surfaceId2 + '&showIso=' + showIso + '&showPoints=' +  showPoints, {}
					).addTo(A.map);

			}
			else {

				if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  			A.map.removeLayer(A.map.tileOverlay);

				A.map.tileOverlay = L.tileLayer('/tile/surface?z={z}&x={x}&y={y}&shapefileId=' + this.$('#shapefile').val() + '&attributeName=' + this.$('#shapefileColumn').val() +
					'&minTime=' + minTime + '&timeLimit=' + timeLimit + '&showPoints=' + showPoints + '&showIso=' + showIso + '&surfaceId=' + this.surfaceId1, {

					}).addTo(A.map);

			}
		},

		drawChart : function(res, barChart, divSelector, chartHeight) {

			var _this = this;

			var color = new Array();
			var label = new Array();
			var value = new Array();
			var id = new Array();


			$()

			_.each(res.data, function(val,key) {

				id.push(key);
				value.push(val.sums);
				label.push(res.properties.schema[key].label);
				color.push(res.properties.schema[key].style.color);

			});

			var transformedData = new Array();

			var minute = 1;
			var item = {};

			var maxVals = {};

			for(i in id) {
				item[id[i]] = 0;
			}

			for(var v in value[0]) {

				maxVals[v] = 0;

				if(minute % 1 == 0) {
					item['min'] = Math.ceil(minute / 1);

					for(i in id) {
						item[id[i]] = item[id[i]] +  parseInt(value[i][v]);
						maxVals[v] = maxVals[v] + parseInt(value[i][v]);
					}

					transformedData.push(item);

					item = {};

					for(i in id) {
						item[id[i]] = 0;
					}
				}
				else {
					for(i in id) {
						item[id[i]] = item[id[i]] +  parseInt(value[i][v]);
					}
				}

				minute++;
			}

			for(var v in maxVals) {
				if(maxVals[v] > this.maxChartValue)
					this.maxChartValue = maxVals[v];
			}

			var minuteDimension;

			if(barChart == 1) {
				this.barChart1 = dc.barChart(divSelector);
				this.cfData1 = crossfilter(transformedData);
				minuteDimension = this.cfData1.dimension(function(d) {
					return d.min;
				});
				barChart = this.barChart1;
			}
			else if(barChart == 2) {
				this.barChart2 = dc.barChart(divSelector);
				this.cfData2 = crossfilter(transformedData);
				minuteDimension = this.cfData2.dimension(function(d) {
					return d.min;
				});
				barChart = this.barChart2;
			}


			barChart
                .width(400)
                .height(chartHeight)
                .margins({top: 10, right: 20, bottom: 10, left: 40})
                .elasticY(false)
                .y(d3.scale.linear().domain([0, this.maxChartValue]))
                .dimension(minuteDimension)
                .ordinalColors(color)
                .xAxisLabel("Minutes")
                .yAxisLabel("# " + res.properties.label)
                .transitionDuration(0);


            for(i in id) {
            	var group = minuteDimension.group().reduceSum(function(d){return d[id[i]]});

            	if(i == 0)
            		barChart.group(group, label[i])
            	else
            		barChart.stack(group, label[i])
            }

            barChart.x(d3.scale.linear().domain([0, 120]))
                .renderHorizontalGridLines(true)
                .centerBar(true)
                .brushOn(false)
                .legend(dc.legend().x(250).y(10))
                .xAxis().ticks(5).tickFormat(d3.format("d"));

            this.scaleBarCharts();

         	dc.renderAll();

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

		resetCharts : function() {

			if(this.cfData1) {
				this.cfData1.remove();
			}

			if(this.cfData2) {
				this.cfData2.remove();
			}

			dc.renderAll();

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

	  		A.map.marker.on('dragend', this.createSurface);

		    	A.map.addLayer(A.map.marker);

	    		this.createSurface();

		},

      	selectComparisonType : function(evt) {

			this.comparisonType = this.$('#scenarioComparison').val();

			if(this.comparisonType == 'compare') {
				$('#scenario2-controls').show();
			}
			else {
				$('#scenario2-controls').hide();
			}

			this.createSurface();
		}
	});

})(Analyst, jQuery);
