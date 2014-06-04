var Analyst = Analyst || {};

(function(A, $) {

A.analysis = {};

	A.analysis.AnalysisController = Marionette.Controller.extend({
	        
	    initialize: function(options){
	        
	        this.region = options.region;

	        this.project  = options.model;
	    },
	    
	    show: function(){

	    	var analysisLayout = new A.analysis.AnalysisLayout();
	    	this.region.show(analysisLayout);
	        
	    }
	});

	A.analysis.AnalysisLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('analysis', 'analysis-layout'),

		events: {
		  'change .analysis-type': 'selectAnalysisType'
		},

		regions: {
			analysisDetail: '#detail'
		},

		initialize: function(options){
			_.bindAll(this, 'loadSpt', 'updateMap')
		},

		onClose : function() {
			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  			A.map.removeLayer(A.map.tileOverlay);

		  		if(A.map.marker && A.map.hasLayer(A.map.marker))
		  			A.map.removeLayer(A.map.marker);
		  	},

		loadSpt : function() {
			var _this = this;
			var sptUrl = '/api/spt?lat=' + A.map.marker.getLatLng().lat + '&lon=' + A.map.marker.getLatLng().lng + '&mode=TRANSIT';
		    $.getJSON(sptUrl, function(data) {

		  	  _this.sptId = data.sptId;
		  	  _this.updateMap();
		    });
		},

		updateMap : function() {

			
		  	if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);
		  	
		  	var sptQuery = this.sptId;

		  	//sptQuery = 'all';

		  
			var timeLimit = 1800;
			var mapIndicatorId = "jobs_type";
		  	
			A.map.tileOverlay = L.tileLayer('/api/tile?z={z}&x={x}&y={y}&&indicatorId=' + mapIndicatorId + '&timeLimit=' + timeLimit + '&hiddenAttributes=&sptId=' + sptQuery  , {
				attribution: 'US Cenus LODES'
				}).addTo(A.map);


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

				A.map.marker = new L.marker(new L.latLng(-34.65072034337064,-58.416709899902344), {draggable:'true'});


				A.map.marker.on('dragend', function(event){
			    	_this.loadSpt();    	
			    });



			    A.map.addLayer(A.map.marker);

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

