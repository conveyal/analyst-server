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

	  	'click #deleteScenario' : 'deleteScenario',
	  	'click #toggleLayer' : 'toggleLayer',
			'click #zoomToExtent' : 'zoomToExtent'

	  },

	  initialize : function() {

			_.bindAll(this, 'refreshModel');

			this.refreshModel();

			this.transitOverlay = false;
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

		toggleLayer : function(evt) {

	 	  var target = $(evt.target);

	  	var scenarioId = this.model.id;

			if(this.transitOverlay) {
				if(A.map.hasLayer(this.transitOverlay))
					A.map.removeLayer(this.transitOverlay);

					this.transitOverlay = false;

					this.$("#zoomToExtent").addClass("disabled");

					this.$("#toggleLayerIcon").removeClass("glyphicon-eye-open");
					this.$("#toggleLayerIcon").addClass("glyphicon-eye-close");
			}
			else {
				this.transitOverlay = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&scenarioId=' + scenarioId	).addTo(A.map);

				this.$("#zoomToExtent").removeClass("disabled");

				this.$("#toggleLayerIcon").addClass("glyphicon-eye-open");
				this.$("#toggleLayerIcon").removeClass("glyphicon-eye-close");

			}

		},

		onClose : function(evt) {
			if(this.transitOverlay && A.map.hasLayer(this.transitOverlay))
				A.map.removeLayer(this.transitOverlay);
		},

		zoomToExtent : function(evt) {

			// prevent bootstrap toggle state
			evt.stopImmediatePropagation();

			var bounds = L.latLngBounds([L.latLng(this.model.get("bounds").north, this.model.get("bounds").east), L.latLng(this.model.get("bounds").south, this.model.get("bounds").west)])
			A.map.fitBounds(bounds);
		},

	  deleteScenario: function(evt) {
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

				this.$el.find("#scenarioDescription").editable({
					type        : 'textarea',
					name        : "description",
					mode				: "inline",
					value       : this.model.get("description"),
					pk          : this.model.get('id'),
					url         : '',
					success     : function(response, newValue) {
						_this.model.set("description", newValue);
						_this.model.save("description", newValue);
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

		appendHtml: function(collectionView, itemView){
	    	collectionView.$("#scenarioList").append(itemView.el);

	 	}

	});




})(Analyst, jQuery);
