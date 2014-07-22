var Analyst = Analyst || {};

(function(A, $) {

A.analysis = {};

	A.analysis.AnalysisController = Marionette.Controller.extend({
	        
	    initialize: function(options){
	        
	        this.region = options.region;

	        this.project  = options.model;
	    },
	    
	    show: function(){

	    	var analysisLayout = new A.analysis.AnalysisLayout({model : this.project});
	    	this.region.show(analysisLayout);
	        
	    }
	});

	A.analysis.AnalysisLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('analysis', 'analysis-layout'),

		events: {
		  'change .analysis-type': 'selectAnalysisType',
		  'change #scenarioComparison': 'createSurface',
		  'change #scenario1': 'createSurface',
		  'change #scenario2': 'createSurface',
		  'change .primary-indicator': 'createSurface',
		  'change #chartType' : 'updateResults',
		  'click #showIso': 'updateMap',
		  'click #showPoints': 'updateMap',
		  'click #showTransit': 'updateMap',
		  'click .mode-selector' : 'updateMap'
		},

		regions: {
			analysisDetail: '#detail'
		},

		initialize: function(options){
			_.bindAll(this, 'createSurface', 'updateMap', 'onMapClick');

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

			this.pointsets = new A.models.PointSets(); 
			this.scenarios = new A.models.Scenarios(); 

			this.timeSlider = $('#timeSlider1').slider({
					formater: function(value) {
						return value + " minutes";
					}
				}).on('slideStop', function(evt) {

					$('#minTimeValue').html(evt.value[0] + "");
					$('#timeLimitValue').html(evt.value[1] + " mins");


				_this.updateResults(true)
				_this.updateMap();
			}).data('slider');

			this.walkSpeedSlider = $('#walkSpeedSlider').slider({
					formater: function(value) {
						$('#walkSpeedValue').html(value + " km/h");
						return value + " km/h";
					}
				}).on('slideStop', function(value) {

				_this.createSurface();
			}).data('slider');

			this.bikeSpeedSlider = $('#bikeSpeedSlider').slider({
					formater: function(value) {
						$('#bikeSpeedValue').html(value + " km/h");
						return value + " km/h";
					}
				}).on('slideStop', function(value) {

				_this.createSurface();
			}).data('slider');

			this.mode1 = 'TRANSIT';
			this.mode2 = 'TRANSIT';

			
			$('input[name=mode1]:radio').on('change', function(event) {
				_this.mode1 = $('input:radio[name=mode1]:checked').val();
				_this.createSurface();
		    });

		    $('input[name=mode2]:radio').on('change', function(event) {
				_this.mode2 = $('input:radio[name=mode2]:checked').val();
				_this.createSurface();
		    });

			this.pointsets.fetch({reset: true, data : {projectId: this.model.get("id")}, success: function(collection, response, options){

				_this.$("#primaryIndicator").empty();

				for(var i in _this.pointsets.models)
	    			_this.$("#primaryIndicator").append('<option value="' + _this.pointsets.models[i].get("id") + '">' + _this.pointsets.models[i].get("name") + '</option>');

			}});

			this.scenarios.fetch({reset: true, data : {projectId: this.model.get("id")}, success: function(collection, response, options){

				_this.$(".scenario-list").empty();

				for(var i in _this.scenarios.models) {
					if(_this.scenarios.models[i].get("id") == "default")
						_this.$(".scenario-list").append('<option selected value="' + _this.scenarios.models[i].get("id") + '">' + _this.scenarios.models[i].get("name") + '</option>');
					else
						_this.$(".scenario-list").append('<option value="' + _this.scenarios.models[i].get("id") + '">' + _this.scenarios.models[i].get("name") + '</option>');
						
				}
	    			
			}});

			this.$('#comparisonChart').hide();

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

		  	this.maxChartValue = 0;

		  	this.resetCharts();
		  		
		  	this.comparisonType = this.$('.scenario-comparison').val();

		  	if(this.comparisonType == 'compare')
		  		this.$('#comparisonChart').show();
		  	else	
		  		this.$('#comparisonChart').hide();

		  	var bikeSpeed = (this.bikeSpeedSlider.getValue() * 1000 / 60 / 60 );
		  	var walkSpeed = (this.walkSpeedSlider.getValue() * 1000 / 60 / 60 );

 			var graphId1 = this.$('#scenario1').val();

			var _this = this;
			
			var surfaceUrl1 = '/api/surface?graphId=' + graphId1 + '&lat=' + A.map.marker.getLatLng().lat + '&lon=' + A.map.marker.getLatLng().lng + '&mode=' + this.mode1 + '&bikeSpeed=' + bikeSpeed + '&walkSpeed=' + walkSpeed;
		    $.getJSON(surfaceUrl1, function(data) {

		  	  _this.surfaceId1 = data.id;
		  	  
		  	  if(!this.comparisonType || _this.surfaceId2) {
		  	 	_this.updateMap();
		  	 	_this.updateResults();
		  	  }
		  	 
		    });

		    if(this.comparisonType == 'compare') {

		    	var graphId2 = this.$('#scenario2').val();

		    	var surfaceUrl2 = '/api/surface?graphId=' + graphId2 + '&lat=' + A.map.marker.getLatLng().lat + '&lon=' + A.map.marker.getLatLng().lng + '&mode=' + this.mode2 + '&bikeSpeed=' + bikeSpeed + '&walkSpeed=' + walkSpeed;
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
					$.getJSON('/api/result?&pointSetId=' + this.$("#primaryIndicator").val() + '&surfaceId=' + this.surfaceId1, function(res) {

						_this.drawChart(res, 1, "#barChart1", 175);

					});	
				}

				if(this.surfaceId2) {
					$.getJSON('/api/result?&pointSetId=' + this.$("#primaryIndicator").val() + '&surfaceId=' + this.surfaceId2, function(res) {

						_this.drawChart(res, 2, "#barChart2", 175);

					});	
				}
			}	
			else {
				$.getJSON('/api/result?&pointSetId=' + this.$("#primaryIndicator").val() + '&surfaceId=' + this.surfaceId1, function(res) {

					_this.drawChart(res, 1, "#barChart1", 175);

				});	
			}
		},

		updateMap : function() {
			var _this = this;

			var showTransit =  $('#showTransit').prop('checked');

			this.comparisonType = this.$('.scenario-comparison').val();

			if(showTransit) {
				if(this.comparisonType == 'compare') { 

					var scenarioId = this.$('#scenario1').val();

					if(A.map.hasLayer(this.transitOverlays[scenarioId]))
			 			A.map.removeLayer(this.transitOverlays[scenarioId]);

					this.transitOverlays[scenarioId] = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&scenarioId=' + scenarioId).addTo(A.map);

					scenarioId = this.$('#scenario2').val();

					if(A.map.hasLayer(this.transitOverlays[scenarioId]))
			 			A.map.removeLayer(this.transitOverlays[scenarioId]);

					this.transitOverlays[scenarioId] = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&scenarioId=' + scenarioId).addTo(A.map);

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
	
			if(!this.$("#primaryIndicator").val() ||  !this.surfaceId1)
				return;	

			$('#results1').hide();
			$('#results2').hide();

			var minTime = this.timeSlider.getValue()[0] * 60;
			var timeLimit = this.timeSlider.getValue()[1] * 60;

			var showIso =  $('#showIso').prop('checked');
			var showPoints =  $('#showPoints').prop('checked');

			if(this.comparisonType == 'compare') { 

				if(!this.surfaceId1 || !this.surfaceId2)
					return;

				if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  			A.map.removeLayer(A.map.tileOverlay);

				A.map.tileOverlay = L.tileLayer('/tile/compare?z={z}&x={x}&y={y}&spatialId=' +  this.$("#primaryIndicator").val() + '&minTime=' + minTime + '&timeLimit=' + timeLimit + '&surfaceId1=' + this.surfaceId1  + '&surfaceId2=' + this.surfaceId2 + '&showIso=' + showIso + '&showPoints=' +  showPoints, {}
					).addTo(A.map);

			}
			else {
				
				if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  			A.map.removeLayer(A.map.tileOverlay);
			  				
				A.map.tileOverlay = L.tileLayer('/tile/surface?z={z}&x={x}&y={y}&pointSetId=' +  this.$("#primaryIndicator").val() + '&minTime=' + minTime + '&timeLimit=' + timeLimit + '&showPoints=' + showPoints + '&showIso=' + showIso + '&surfaceId=' + this.surfaceId1, {
					
					}).addTo(A.map);

				/*if(showIso) {

						$.getJSON('/api/isochrone?&cutoffs=' + timeLimit + '&surfaceId=' + this.surfaceId1, function(data){

						if(A.map.isochronesLayer  && A.map.hasLayer(A.map.isochronesLayer))
			  				A.map.removeLayer(A.map.isochronesLayer);


						A.map.isochronesLayer = L.geoJson(data.features, {
					      style: function(feature) {
					        return _this.isochroneStyle(feature.properties.Time);
					      }
					    })
						.addTo(A.map);
					});

				}
				else {

					if(A.map.isochronesLayer  && A.map.hasLayer(A.map.isochronesLayer))
		  				A.map.removeLayer(A.map.isochronesLayer);

				}*/

			}
		},

		drawChart : function(res, barChart, divSelector, chartHeight) {

			var _this = this;

			var color = new Array();
			var label = new Array();
			var value = new Array();
			var id = new Array();


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

			if(!this.analysisType || this.analysisType == 'single') {

				if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  			A.map.removeLayer(A.map.tileOverlay);

				var _this = this;

				var analysisSinglePointLayout = new A.analysis.AnalysisSinglePointLayout();

				this.analysisDetail.show(analysisSinglePointLayout);

				if(A.map.marker && A.map.hasLayer(A.map.marker))
		  			A.map.removeLayer(A.map.marker);

		  		A.map.on('click', this.onMapClick);

			}
			else if(this.analysisType == 'multi') {

				if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  			A.map.removeLayer(A.map.tileOverlay);

		  		if(A.map.marker && A.map.hasLayer(A.map.marker))
		  			A.map.removeLayer(A.map.marker);

		  		A.map.tileOverlay = L.tileLayer('http://{s}.tiles.mapbox.com/v3/conveyal.574uc8fr/{z}/{x}/{y}.png', {
				    attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://mapbox.com">Mapbox</a>',
				    maxZoom: 18
				}).addTo(Analyst.map);

				var analysisMultiPointLayout = new A.analysis.AnalysisMultiPointLayout();

				this.analysisDetail.show(analysisMultiPointLayout);
			}

		},

		onMapClick : function(evt) {

  			if(A.map.marker && A.map.hasLayer(A.map.marker))
  				A.map.removeLayer(A.map.marker);

  			A.map.marker = new L.marker(evt.latlng, {draggable:'true'});

  			A.map.marker.on('dragend', this.createSurface);

	    	A.map.addLayer(A.map.marker);

	    	this.createSurface();  

		},

		selectAnalysisType : function(evt) {

			this.analysisType = $('.analysis-type').val();

			this.render();

		},

		templateHelpers: {
	     	multiPoint : function () {
	     		if(this.analysisType == "multi") 
	     			return true;
	     		else
	     			return false;
	        },
	        singlePoint : function () {
	     		if(this.analysisType == "single") 
	     			return true;
	     		else
	     			return false;
	        }
      	}

	});

	A.analysis.AnalysisSinglePointLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('analysis', 'analysis-single-point'),

		events: {
		  'change .scenario-comparison': 'selectComparisonType'
		},

		onShow : function () {
			this.$('#scenario2-controls').hide();
		},

		selectComparisonType : function(evt) {

			this.comparisonType = $('.scenario-comparison').val();

			if(this.comparisonType == 'compare') {
				$('#scenario2-controls').show();
			}
			else {
				$('#scenario2-controls').hide();
			}

		}

	});

	A.analysis.AnalysisMultiPointLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('analysis', 'analysis-multi-point'),

		events: {
		  'change .scenario-comparison': 'selectComparisonType'
		},

		onRender : function () {
			$('#scenario2-controls').hide();
		},

		selectComparisonType : function(evt) {

			this.comparisonType = $('.scenario-comparison').val();

			if(this.comparisonType == 'compare') {
				$('#scenario2-controls').show();
			}
			else {
				$('#scenario2-controls').hide();
			}

		}

	});



})(Analyst, jQuery);	

