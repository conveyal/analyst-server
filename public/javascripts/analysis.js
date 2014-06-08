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
		  'change .primary-indicator': 'loadSpt',
		  'click #showIso': 'updateMap',
		  'click #showPoints': 'updateMap',
		  'click .mode-selector' : 'updateMap'
		},

		regions: {
			analysisDetail: '#detail'
		},

		initialize: function(options){
			_.bindAll(this, 'loadSpt', 'updateMap');
		},

		onShow : function() {

			var _this = this;

			this.pointsets = new A.models.PointSets(); 

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
				_this.loadSpt();
		    });

		    $('input[name=mode2]:radio').on('change', function(event) {
				_this.mode2 = $('input:radio[name=mode2]:checked').val();
				_this.loadSpt();
		    });

			this.pointsets.fetch({reset: true, data : {projectId: this.model.get("id")}, success: function(collection, response, options){

				_this.$("#primaryIndicator").empty();

				for(var i in _this.pointsets.models)
	    			_this.$("#primaryIndicator").append('<option value="' + _this.pointsets.models[i].get("id") + '">' + _this.pointsets.models[i].get("name") + '</option>');

			}});

		},

		onClose : function() {
			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);

		  	if(A.map.marker && A.map.hasLayer(A.map.marker))
		  		A.map.removeLayer(A.map.marker);
		 },

		loadSpt : function() {

			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);

			var _this = this;
			var sptUrl = '/api/spt?graphId=default&lat=' + A.map.marker.getLatLng().lat + '&spatialId=' + this.$("#primaryIndicator").val() + '&lon=' + A.map.marker.getLatLng().lng + '&mode=' + this.mode1;
		    $.getJSON(sptUrl, function(data) {

		  	  _this.sptId = data.sptId;
		  	  _this.updateMap();
		    });
		},

		updateMap : function() {
	
			if(!this.$("#primaryIndicator").val() ||  !this.sptId)
				return;	

			if(A.map.tileOverlay && A.map.hasLayer(A.map.tileOverlay))
		  		A.map.removeLayer(A.map.tileOverlay);

		  	var sptQuery = this.sptId;
		  
			var timeLimit = this.timeSlider.getValue() * 60;

			var showIso =  $('#showIso').prop('checked');
			var showPoints =  $('#showPoints').prop('checked');

		  	
			A.map.tileOverlay = L.tileLayer('/tile/spt?z={z}&x={x}&y={y}&&spatialId=' +  this.$("#primaryIndicator").val() + '&timeLimit=' + timeLimit + '&showPoints=' + showPoints + '&showIso=' + showIso + '&sptId=' + this.sptId, {
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

		  		A.map.on('click', function(evt) {

		  			if(A.map.marker && A.map.hasLayer(A.map.marker))
		  				A.map.removeLayer(A.map.marker);

		  			A.map.marker = new L.marker(evt.latlng, {draggable:'true'});

		  			A.map.marker.on('dragend', function(event){
			    		_this.loadSpt();    	
			    	});

			    	A.map.addLayer(A.map.marker);

			    	_this.loadSpt();  

		  		});
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

