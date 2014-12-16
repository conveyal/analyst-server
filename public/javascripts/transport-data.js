var Analyst = Analyst || {};

(function(A, $) {

A.transportData = {};

	A.transportData.DataLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-layout'),

		regions: {
		  scenarioData : "#main"
		},

		initialize : function(options) {

			var _this = this;

			this.scenarios  = new A.models.Scenarios();

		},

		onShow : function() {

			this.scenarioLayout = new A.transportData.ScenarioLayout({collection: this.scenarios});

			this.listenTo(this.scenarioLayout, "scenarioCreate", this.createScenario);

			this.scenarioData.show(this.scenarioLayout);

			this.scenarios.fetch({reset: true, data : {projectId: A.app.selectedProject}});

			var _this = this;

			A.app.instance.vent.on("setSelectedProject", function() {
				_this.scenarios.fetch({reset: true, data : {projectId: A.app.selectedProject}});
			});

		},

		cancelNewScenario : function() {

			this.scenarioData.close();

			this.onShow();
		},


		createScenario : function(evt) {

			var _this = this;

			var scenarioCreateLayout = new A.transportData.ScenarioCreateView({projectId: A.app.selectedProject});

			this.listenTo(scenarioCreateLayout, "scenarioCreate:save", function() {
					_this.scenarios.fetch({reset: true, data : {projectId: A.app.selectedProject}});
					this.onShow();
				});

			this.listenTo(scenarioCreateLayout, "scenarioCreate:cancel", this.cancelNewScenario)	;

			this.scenarioData.show(scenarioCreateLayout);

		},


	});


	A.transportData.ScenarioCreateView = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-scenario-create-form'),

		ui: {
			name: 			'#name',
			description:  	'#description'
		},

		regions: {
		  shapefileSelect 	: "#shapefileSelect"
		},

		events: {
		  'click #scenarioSave': 'saveScenarioCreate',
		  'click #scenarioCancel': 'cancelScenarioCreate',
		  'change #scenarioType' : 'scenarioTypeChange'
		},

		initialize : function(options) {


			this.projectId = options.projectId;
		},

		onShow : function() {

			var _this = this;

			this.scenarios = new A.models.Scenarios();

			this.scenarios.fetch({reset: true, data : {projectId: this.projectId}, success: function(collection, response, options){

				_this.$("#augmentScenarioId").empty();

				for(var i in _this.scenarios.models) {
					_this.$("#augmentScenarioId").append('<option value="' + _this.scenarios.models[i].get("id") + '">' + _this.scenarios.models[i].get("name") + '</option>');
				}

			}});

			this.$("#augmentScenarioId").hide();
		},

		scenarioTypeChange : function(evt) {

			if(this.$('#scenarioType').val() === "augment")
				this.$("#augmentScenarioId").show();
			else
				this.$("#augmentScenarioId").hide();
		},

		cancelScenarioCreate : function(evt) {

			this.trigger("scenarioCreate:cancel");

		},

		saveScenarioCreate : function(evt) {

			evt.preventDefault();
			var _this = this;
		    var values = {};

		    if(event){ event.preventDefault(); }

		    _.each(this.$('form').serializeArray(), function(input){
		      values[ input.name ] = input.value;
		    })

		    values.projectId = this.projectId;
		    values.scenarioType = this.$('#scenarioType').val();

		    if(values.scenarioType === "augment")
			values.augmentScenarioId = this.$('#augmentScenarioId').val();

		    var scenario = new A.models.Scenario();

		    scenario.save(values, { iframe: true,
		                              files: this.$('form :file'),
		                              data: values,
		                              success: function(){
		                      				_this.trigger("scenarioCreate:save");
		                              }});
		}

	});


	A.transportData.ScenarioLayout = Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-scenario-layout'),

		events:  {
			'click #createScenario' : 'createScenario'
		},

		regions: {
		  main 	: "#main"
		},

		onShow : function() {

			var scenarioListLayout = new A.transportData.ScenarioListView({collection: this.collection});

			this.main.show(scenarioListLayout);

		},

		createScenario : function(evt) {
			this.trigger("scenarioCreate");
		}

	});

	A.transportData.ScenarioListItem = Backbone.Marionette.ItemView.extend({

	  template: Handlebars.getTemplate('data', 'data-scenario-list-item'),

	  events: {

	  	'click #deleteItem' : 'deleteItem',
	  	'click #scenarioCheckbox' : 'clickItem'

	  },

	  initialize : function() {

		_.bindAll(this, 'refreshModel');

		this.refreshModel();
	  },

	  refreshModel : function() {
		if(this.model.get('status') != "BUILT") {
			this.model.fetch();
			this.render();
			setTimeout(this.refreshModel, 5000);
		}
		else
			this.render();
	  },


	  clickItem : function(evt) {

	 	var target = $(evt.target);

	  	var scenarioId = target.data("id")

	  	if(target.prop("checked"))
	  		this.trigger("transitShow", {scenarioId : scenarioId});
	  	else
	  		this.trigger("transitHide", {scenarioId : scenarioId});

	  },

	  deleteItem: function(evt) {
	  	this.model.destroy();
	  },

	  templateHelpers: {
			built : function () {
				if(this.model.get('status') === "BUILT")
					return true;
				else
					return false;
			  }
		},

	  onRender: function () {
        // Get rid of that pesky wrapping-div.
        // Assumes 1 child element present in template.
        this.$el = this.$el.children();
        // Unwrap the element to prevent infinitely
        // nesting elements during re-render.
        this.$el.unwrap();
        this.setElement(this.$el);

				var _this = this;
				
				this.$el.find("#scenarioName").editable({
					type        : 'text',
					name        : "name",
					mode				: "inline",
					value       : this.model.get("name"),
					pk          : this.model.get('id'),
					url         : '',
					success     : function(response, newValue) {
						_this.model.set("name", newValue);
						_this.model.save("name", newValue);
					}
				}).on("hidden", function(e, reason) {
					_this.render();
				});
      }

	});

	A.transportData.ScenarioEmptyList = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('data', 'data-scenario-empty-list')
	});

	A.transportData.ScenarioListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('data', 'data-scenario-list'),
		itemView: A.transportData.ScenarioListItem,
		emptyView: A.transportData.ScenarioEmptyList,

		initialize : function() {
			this.transitOverlays = {};

		},

		onClose : function() {

			for(var id in this.transitOverlays){
				if(this.transitOverlays[id] && A.map.hasLayer(this.transitOverlays[id]))
					A.map.removeLayer(this.transitOverlays[id]);
			}

		},

		appendHtml: function(collectionView, itemView){
	    	collectionView.$("#scenarioList").append(itemView.el);
	    	this.listenTo(itemView, "transitShow", this.transitShow);
	    	this.listenTo(itemView, "transitHide", this.transitHide);
	 	},

	 	transitShow : function(data) {

	 		if(A.map.hasLayer(this.transitOverlays[data.scenarioId]))
	 			A.map.removeLayer(this.transitOverlays[data.scenarioId ]);

			this.transitOverlays[data.scenarioId] = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&scenarioId=' + data.scenarioId).addTo(A.map);
		},

		transitHide : function(data) {

			if(A.map.hasLayer(this.transitOverlays[data.scenarioId]))
				A.map.removeLayer(this.transitOverlays[data.scenarioId ]);

		}

	});


})(Analyst, jQuery);
