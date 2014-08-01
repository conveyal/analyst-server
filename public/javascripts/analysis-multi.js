var Analyst = Analyst || {};

(function(A, $) {

	A.analysis.AnalysisMultiPointLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('analysis', 'analysis-multi-point'),

		events: {
		  'change .scenario-comparison': 'selectComparisonType',
		  'click #createQuery' : 'createQuery',
		  'click #cancelQuery' : 'cancelQuery',
		  'click #newQuery' : 'newQuery' 
		},

		regions: {
		  main 	: "#main"
		},

		initialize: function(options){
			_.bindAll(this, 'selectComparisonType', 'createQuery', 'cancelQuery');

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

		},

		onShow : function() {

			var _this = this;

			this.pointsets = new A.models.PointSets(); 
			this.scenarios = new A.models.Scenarios(); 
			this.queries = new A.models.Queries(); 

			this.pointsets.fetch({reset: true, data : {projectId: this.model.get("id")}, success: function(collection, response, options) {

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

			this.queries.fetch({reset: true, data : {projectId: this.model.get("id")}, success: function(collection, response, options){
	    			
			}});

			this.mode = "TRANSIT";

			this.$('input[name=mode1]:radio').on('change', function(event) {
				_this.mode = _this.$('input:radio[name=mode1]:checked').val();
		    });

		    this.$("#createQueryForm").hide();

		    var queryListLayout = new A.analysis.QueryList({collection: this.queries});			

			this.main.show(queryListLayout);
		},

		createQuery : function(evt) {

			var data = {name: this.$("#name").val(), 
						mode: this.mode, 
						pointSetId: this.$("#primaryIndicator").val(), 
						scenarioId: this.$('#scenario1').val(),
						projectId: this.model.get("id")};

			var query = new A.models.Query(data);
			query.save();

			this.$("#createQueryForm").hide();
		},

		cancelQuery : function(evt) {
			this.$("#createQueryForm").hide();
		},

		newQuery : function(evt) {
			this.$("#createQueryForm").show();
		}
 
	});

	A.analysis.QueryListItem = Backbone.Marionette.ItemView.extend({

	  template: Handlebars.getTemplate('analysis', 'query-list-item'),

	  events: { 

	  	'click #deleteItem' : 'deleteItem',
	  	'click #queryCheckbox' : 'clickItem',
	  	'click #groupCheckbox' : 'groupBy',
	  	'click #normalizeCheckbox' : 'normalizeBy',
	  	'change #normalizeBy' : 'refreshMap',
	  	'change #groupBy' : 'groupBy',
	  	'click #exportShape' : 'exportShape'
	  	
	  },

	  modelEvents: {
	  	'change': 'fieldsChanged'
	  },

	  initialize : function() {
	  	var _this = this;
	  	this.updateInterval = setInterval(function() {
        	if(_this.model.get("completePoints") < _this.model.get("totalPoints"))
        		_this.model.fetch();
		}, 1000);
	  },

	  onClose : function() {
	  	clearInterval(this.updateInterval);

	  	if(this.queryOverlay && A.map.hasLayer(this.queryOverlay))
			A.map.removeLayer(this.queryOverlay);
		
	  },

	  fieldsChanged: function() {
	  	this.render();
	  },

	  serializeData: function() {

	  	var data  = this.model.toJSON();

	  	if(this.isStarting())
	  		data['starting'] = true;
	  	else if(this.isComplete())
	  		data['complete'] = true;

	  	return data;

	  },

	  isStarting : function() {
	  	return this.model.get("totalPoints") == -1;
	  },

	  isComplete : function() {
	  	return this.model.get("totalPoints") == this.model.get("completePoints");
	  },

	  clickItem : function(evt) {
	  	
	  	this.refreshMap();
	  	
	  },

	  exportShape : function(evt) {

	  	var timeLimit = this.timeSlider.getValue() * 60;

	  	var url = '/gis/query?z={z}&x={x}&y={y}&queryId=' + this.model.id + '&timeLimit=' + timeLimit;

 		if(this.groupById)
 			url = url + "&groupBy=" + this.groupById;

 		if(this.normalizeById)
 			url = url + "&normalizeBy=" + this.normalizeById;

 		window.open(url);

	  },

	  groupBy : function(evt) {

	  	this.refreshMap();
	  },

	  normalizeBy : function(evt) {
	  		
	  	this.refreshMap();
	  },

	  deleteItem: function(evt) {
	  	this.model.destroy();
	  },

	  refreshMap : function() {
	  	var target = this.$("#queryCheckbox");

	  	if(this.$("#groupCheckbox").prop('checked')) {
	  		this.$("#groupBy").prop("disabled", false);
	  		this.groupById = this.$("#groupBy").val();	
	  	}
	  	else  {
	  		this.groupById = false;
	  		this.$("#groupBy").prop("disabled", true);
	  	}

	  	if(this.$("#normalizeCheckbox").prop('checked')) {
	  		this.$("#normalizeBy").prop("disabled", false);
	  		this.normalizeById = this.$("#normalizeBy").val();
	  	}
	  	else {
	  		this.$("#normalizeBy").prop("disabled", true);
	  		this.normalizeById = false;

	  	}

	  	if(target.prop("checked")) {
	  		if(A.map.hasLayer(this.queryOverlay))
	 			A.map.removeLayer(this.queryOverlay);

	 		var timeLimit = this.timeSlider.getValue() * 60;

	 		var url = '/tile/query?z={z}&x={x}&y={y}&queryId=' + this.model.id + '&timeLimit=' + timeLimit;

	 		if(this.groupById)
	 			url = url + "&groupBy=" + this.groupById;

	 		if(this.normalizeById)
	 			url = url + "&normalizeBy=" + this.normalizeById;

			this.queryOverlay = L.tileLayer(url).addTo(A.map);
	  	}	
	  	else {
	  		if(A.map.hasLayer(this.queryOverlay))
				A.map.removeLayer(this.queryOverlay);

	  	}
	  },

	  onRender: function () {

	  	var _this = this;

        // Get rid of that pesky wrapping-div.
        // Assumes 1 child element present in template.
        this.$el = this.$el.children();
        // Unwrap the element to prevent infinitely 
        // nesting elements during re-render.
        this.$el.unwrap();
        this.setElement(this.$el);

        if(this.isComplete()) {

        	this.pointsets = new A.models.PointSets(); 

			this.pointsets.fetch({reset: true, data : {projectId: this.model.get("projectId")}, success: function(collection, response, options) {

				_this.$("#normalizeBy").empty();
				_this.$("#groupBy").empty();

				for(var i in _this.pointsets.models) {
					_this.$("#normalizeBy").append('<option value="' + _this.pointsets.models[i].get("id") + '">' + _this.pointsets.models[i].get("name") + '</option>');
					_this.$("#groupBy").append('<option value="' + _this.pointsets.models[i].get("id") + '">' + _this.pointsets.models[i].get("name") + '</option>');
				}
	    			

			}});

			this.$("#normalizeBy").prop("disabled", true);
			this.$("#groupBy").prop("disabled", true);

			this.$("#settings").show();
        }
        else
        	this.$("#settings").hide();

        this.timeSlider = this.$('#timeSlider').slider({
			formater: function(value) {
				return value + " minutes";
			}
		}).on('slideStop', function(evt) {
			_this.$('#timeLimitValue').html(evt.value + " mins");
			_this.refreshMap();
		}).data('slider');

      }

	});


	A.analysis.QueryList = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('analysis', 'query-list'),
		itemView: A.analysis.QueryListItem,

		initialize : function() {
			this.queryOverlay = {};
			
		},

		onShow : function() {
			
		},

		appendHtml: function(collectionView, itemView){
	    	collectionView.$("#queryList").append(itemView.el);
	 	}

	});


})(Analyst, jQuery);	

