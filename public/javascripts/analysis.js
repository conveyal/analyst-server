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
		  'click #showIso': 'updateMap',
		  'click #showPoints': 'updateMap',
		  'click .mode-selector' : 'updateMap'
		},

		regions: {
			analysisDetail: '#detail'
		},

		initialize: function(options){
			_.bindAll(this, 'createSurface', 'updateMap', 'onMapClick');
		},

		isochroneStyle: function(seconds) {
		    var style = {
		      color: '#333',
		      fillColor: '#333',
		      lineCap: 'round',
		      lineJoin: 'round',
		      weight: 3,
		      dashArray: '5, 5',
		      fillOpacity: '0.05'
		    };
		    if (seconds == 3600) {
		      style.weight = 1.5;
		    } else {
		      style.weight = 3;
		    }
		    return style;
		  },

		onShow : function() {

			var _this = this;

			this.pointsets = new A.models.PointSets(); 
			this.scenarios = new A.models.Scenarios(); 

			this.timeSlider = $('#timeSlider1').slider({
				formater: function(value) {
					$('#timeLimitValue').html(value + " mins");
					return value + " minutes";
				}
			}).on('slideStop', function(value) {

				_this.updateMap();
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

		},

		onClose : function() {
			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);

		  	if(A.map.marker && A.map.hasLayer(A.map.marker))
		  		A.map.removeLayer(A.map.marker);

		  	if(A.map.isochronesLayer  && A.map.hasLayer(A.map.isochronesLayer))
		  		A.map.removeLayer(A.map.isochronesLayer);
		  	

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

		  	this.comparisonType = this.$('.scenario-comparison').val();

 			var graphId1 = this.$('#scenario1').val();

			var _this = this;
			
			var surfaceUrl1 = '/api/surface?graphId=' + graphId1 + '&lat=' + A.map.marker.getLatLng().lat + '&lon=' + A.map.marker.getLatLng().lng + '&mode=' + this.mode1;
		    $.getJSON(surfaceUrl1, function(data) {

		  	  _this.surfaceId1 = data.id;
		  	  _this.updateMap();

		    });

		    if(this.comparisonType == 'compare') {

		    	var graphId2 = this.$('#scenario2').val();

		    	var surfaceUrl1 = '/api/surface?graphId=' + graphId1 + '&lat=' + A.map.marker.getLatLng().lat + '&lon=' + A.map.marker.getLatLng().lng + '&mode=' + this.mode1;
		    	$.getJSON(surfaceUrl1, function(data) {

			  	  _this.surfaceId2 = data.id;
			  	  _this.updateMap();

			    });

		    }
		},

		updateMap : function() {
			var _this = this;
	
			if(!this.$("#primaryIndicator").val() ||  !this.surfaceId1)
				return;	

			$('#results1').hide();
			$('#results2').hide();


			if(this.comparisonType == 'compare') { 

				if(!this.surfaceId1 || !this.surfaceId2)
					return;

				if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  			A.map.removeLayer(A.map.tileOverlay);
			  
				var timeLimit = this.timeSlider.getValue() * 60;

				A.map.tileOverlay = L.tileLayer('/tile/compareSpt?z={z}&x={x}&y={y}&pointSetId=' +  this.$("#primaryIndicator").val() + '&timeLimit=' + timeLimit + '&sptId1=' + this.sptId1  + '&sptId2=' + this.sptId2, {
		
					}).addTo(A.map);

				

				$.getJSON('/api/sptSummary?&pointSetId=' +  this.$("#primaryIndicator").val() + '&timeLimit=' + timeLimit + '&sptId=' + this.sptId1, function(data){

					var primaryIndicator = $("#primaryIndicator option:selected").text();

					var scenarioLabel = $("#scenario1 option:selected").text();

					var percent = (data.accessible / data.total) * 100;
					var percentOut = percent.toFixed(2) + "%";

					$('#primaryIndicatorLabel').html(primaryIndicator);
					$('#scenarioLabel1').html(scenarioLabel);
					$('#totalAccessible1').html(data.accessible);
					$('#percentAccessible1').html(percentOut);

					$('#results1').show();

				});

				$.getJSON('/api/sptSummary?&spatialId=' +  this.$("#primaryIndicator").val() + '&timeLimit=' + timeLimit + '&sptId=' + this.sptId2, function(data){

					var primaryIndicator = $("#primaryIndicator option:selected").text();

					var scenarioLabel = $("#scenario2 option:selected").text();

					var percent = (data.accessible / data.total) * 100;
					var percentOut = percent.toFixed(2) + "%";



					$('#primaryIndicatorLabel').html(primaryIndicator);
					$('#scenarioLabel2').html(scenarioLabel);
					$('#totalAccessible2').html(data.accessible);
					$('#percentAccessible2').html(percentOut);

					$('#results2').show();

				});

			}
			else {

				
				if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  			A.map.removeLayer(A.map.tileOverlay);
			  
				var timeLimit = this.timeSlider.getValue() * 60;

				var showIso =  $('#showIso').prop('checked');
				var showPoints =  $('#showPoints').prop('checked');

			  	
				A.map.tileOverlay = L.tileLayer('/tile/surface?z={z}&x={x}&y={y}&pointSetId=' +  this.$("#primaryIndicator").val() + '&timeLimit=' + timeLimit + '&showPoints=' + showPoints + '&showIso=' + false + '&surfaceId=' + this.surfaceId1, {
					
					}).addTo(A.map);

				if(showIso) {

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

				}

				
			}
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

