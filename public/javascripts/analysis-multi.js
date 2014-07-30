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
	  	'click #queryCheckbox' : 'clickItem'
	  	
	  },

	  clickItem : function(evt) {

	 	var target = $(evt.target);
	  	
	  	var queryId = target.data("id")
	  		
	  	if(target.prop("checked"))
	  		this.trigger("queryShow", {queryId : queryId});
	  	else
	  		this.trigger("queryHide", {queryId : queryId});

	  },

	  deleteItem: function(evt) {
	  	this.model.destroy();
	  },

	  onRender: function () {
        // Get rid of that pesky wrapping-div.
        // Assumes 1 child element present in template.
        this.$el = this.$el.children();
        // Unwrap the element to prevent infinitely 
        // nesting elements during re-render.
        this.$el.unwrap();
        this.setElement(this.$el);
      }

	});


	A.analysis.QueryList = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('analysis', 'query-list'),
		itemView: A.analysis.QueryListItem,

		initialize : function() {
			this.queryOverlay = {};
			
		},

		onClose : function() {

			for(var id in this.queryOverlay){
				if(this.queryOverlay[id] && A.map.hasLayer(this.queryOverlay[id]))
					A.map.removeLayer(this.queryOverlay[id]);
			}

		},

		appendHtml: function(collectionView, itemView){
	    	collectionView.$("#queryList").append(itemView.el);
	    	this.listenTo(itemView, "queryShow", this.transitShow);	
	    	this.listenTo(itemView, "queryHide", this.transitHide);	
	 	},

	 	transitShow : function(data) {

	 		if(A.map.hasLayer(this.queryOverlay[data.queryId]))
	 			A.map.removeLayer(this.queryOverlay[data.queryId ]);

			this.queryOverlay[data.queryId] = L.tileLayer('/tile/query?z={z}&x={x}&y={y}&queryId=' + data.queryId).addTo(A.map);	
		},

		queryHide : function(data) {

			if(A.map.hasLayer(this.queryOverlay[data.queryId]))
				A.map.removeLayer(this.queryOverlay[data.queryId ]);
		}
	});


})(Analyst, jQuery);	

