var Analyst = Analyst || {};

(function(A, $) {

A.data = {};

	A.data.DataLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-layout'),

		regions: {
		  spatialData 	: "#spatial",
		  scenarioData 	: "#scenario"
		},

		initialize : function(options) {

			var _this = this;

			this.pointsets = new A.models.PointSets(); 

			this.scenarios  = new A.models.Scenarios(); 

			

		},

		onShow : function() {

			this.pointSetLayout = new A.data.PointSetLayout({collection: this.pointsets});

			this.listenTo(this.pointSetLayout, "pointsetCreate", this.createPointset);		
			
			this.spatialData.show(this.pointSetLayout);

			this.scenarioLayout = new A.data.ScenarioLayout({collection: this.scenarios});

			this.listenTo(this.scenarioLayout, "scenarioCreate", this.createScenario);		
			
			this.scenarioData.show(this.scenarioLayout);

			this.scenarios.fetch({reset: true, data : {projectId: this.model.get("id")}});			

		},

		createPointset : function(evt) {

			var pointSetCreateLayout = new A.data.PointSetCreateView();		

			this.listenTo(pointSetCreateLayout, "pointsetCreate:save", this.saveNewPointset);

			this.listenTo(pointSetCreateLayout, "pointsetCreate:cancel", this.cancelNewPointset);	

			this.spatialData.show(pointSetCreateLayout);
			this.scenarioData.close();

		},

		saveNewPointset: function(data) {

			data.projectId = this.model.get("id");
			this.pointsets.create(data, {wait: true});
			this.spatialData.close();

			this.onShow();
		},

		cancelNewPointset : function() {

			this.spatialData.close();

			this.onShow();
		},

		createScenario : function(evt) {

			var _this = this;

			var scenarioCreateLayout = new A.data.ScenarioCreateView({projectId: this.model.get("id")});		

			this.listenTo(scenarioCreateLayout, "scenarioCreate:save", function() {
					_this.scenarios.fetch({reset: true, data : {projectId: _this.model.get("id")}});
					this.onShow();
				});

			this.listenTo(scenarioCreateLayout, "scenarioCreate:cancel", this.onShow());	

			this.spatialData.close();
			this.scenarioData.show(scenarioCreateLayout);

		},


	});

	A.data.PointSetLayout = Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-pointset-layout'),

		events:  {
			'click #createPointset' : 'createPointset'
		},

		regions: {
		  main 	: "#main"
		},

		onShow : function() {

			var pointSetListLayout = new A.data.PointSetListView({collection: this.collection});			

			this.main.show(pointSetListLayout);

		},

		createPointset : function(evt) {
			this.trigger("pointsetCreate");
		}

		

	});

	A.data.PointSetCreateView = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-pointset-create-form'),

		ui: {
			name: 			'#name',
			description:  	'#description'
		},

		regions: {
		  shapefileSelect 	: "#shapefileSelect"
		},

		events: {
		  'click #pointsetSave': 'savePointSetCreate',
		  'click #pointsetCancel': 'cancelPointSetCreate'
		},


		onShow : function() {

			var _this = this;
			this.shapefileListView = new A.data.ShapefileListView();			

			this.shapefileSelect.show(this.shapefileListView);

			//this.colorVal = '#0fb0b0';

			//this.colorPicker = this.$('#pointsetColorPicker').colorpicker({color:this.colorVal}).on('changeColor', function(ev){
			// _this.colorVal = ev.color.toHex();
			//});

		},

		cancelPointSetCreate : function(evt) {

			this.trigger("pointsetCreate:cancel");

		},

		savePointSetCreate : function(evt) {

			evt.preventDefault();
			var data = Backbone.Syphon.serialize(this);
			//data.color = this.colorVal;

			if(data.name && data.shapeFileId)
				this.trigger("pointsetCreate:save", data);

		}

	});

	A.data.PointSetAttributeCreateView = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-pointset-attribute-create-form'),

		onShow : function() {

			alert("test");
		},

		cancelPointSetAttributeCreate : function(evt) {

			this.trigger("pointSetAttributeCreate:cancel");

		},

		savePointSetAttributeCreate : function(evt) {

			this.trigger("pointSetAttributeCreate:save");

		},

	});

	A.data.PointSetListItem = Backbone.Marionette.Layout.extend({

	  template: Handlebars.getTemplate('data', 'data-pointset-list-item'),

	  events: { 

	  	'click #deleteItem' : 'deleteItem',
	  	'click #pointsetCheckbox' : 'clickItem',
	  	'click .pointsetAttributeCheckbox' : 'clickItem',
	  	'click #addAttribute' : 'addAttribute',
	  	'click #deleteAttribute' : 'deleteAttribute',
	  },

	  regions : {
	  		createAttribute : "#createAttribute"
	  },

	  modelEvents : {
	  	"change" : "render"
	  },

	  initialize : function() {

	  	_.bindAll(this, "addAttribute");
	  },

	  clickItem : function(evt) {

	  	
	  	var target = $($(evt.target).closest(".pointset-group"));
	  	
	  	var pointSetId = $(target.find("#pointsetCheckbox")).data("id")
	  	var attributeCheckboxes = target.find(".pointsetAttributeCheckbox");

	  	var checkedAttributes = new Array();
	  	attributeCheckboxes.each( function(item) {
	  		if($(this).prop("checked")) {
	  			checkedAttributes.push($(this).data("id"));
	  		}	
	  	});
	  		
	  	if($(target.find("#pointsetCheckbox")).prop("checked"))
	  		this.trigger("pointSetShow", {pointSetId : pointSetId, checkedAttributes: checkedAttributes});
	  	else
	  		this.trigger("pointSetHide", {pointSetId : pointSetId});

	  },

	  addAttribute : function(evt) {

	  	var pointSetAttributeCreateLayout = new A.data.PointSetAttributeCreateView();		

		this.listenTo(pointSetAttributeCreateLayout, "pointSetAttributeCreate:save", this.saveNewAttribute);

		this.listenTo(pointSetAttributeCreateLayout, "pointSetAttributeCreate:cancel", this.cancelNewPointset);	

		this.createAttribute.show(pointSetAttributeCreateLayout);

		this.$("#addAttribute").hide();
		  	
	  },

	  saveNewAttribute : function(data) {
	  	this.addAttribute(data.name, data.description, data.color, data.fieldName);


	  },

	  cancelNewAttribute : function() {
	  	this.createAttribute.close();
	  	this.$("#addAttribute").show();
	  },

	  deleteAttribute : function(evt) {

	  	var attributeId = $(evt.target).data("id");

	  	this.model.deleteAttribute(attributeId);
	  	this.model.save();

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

	A.data.PointSetListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('data', 'data-pointset-list'),
		itemView: A.data.PointSetListItem,

		initialize : function() {
			this.pointSetOverlays = {};
			
			this.collection.on("reset", function() {
				$("#loadingPointsets").show();
			});
		},

		onShow : function() {
			this.collection.fetch({reset: true, data : {projectId: A.app.selectedProject.get("id")}, success :function() {	
				$("#loadingPointsets").hide();
			}});

		},

		onClose : function() {

			for(var id in this.pointSetOverlays){
				if(this.pointSetOverlays[id] && A.map.hasLayer(this.pointSetOverlays[id]))
					A.map.removeLayer(this.pointSetOverlays[id]);
			}

		},

		appendHtml: function(collectionView, itemView){
	    	collectionView.$("#pointsetList").append(itemView.el);
	    	this.listenTo(itemView, "pointSetShow", this.pointSetShow);	
	    	this.listenTo(itemView, "pointSetHide", this.pointSetHide);	
	 	 },

	 	pointSetShow : function(data) {

	 		if(A.map.hasLayer(this.pointSetOverlays[data.pointSetId]))
	 			A.map.removeLayer(this.pointSetOverlays[data.pointSetId ]);

			var selectedAttributes = data.checkedAttributes.join();
			this.pointSetOverlays[data.pointSetId] = L.tileLayer('/tile/spatial?z={z}&x={x}&y={y}&&spatialId=' + data.pointSetId + '&selectedAttributes=' + selectedAttributes).addTo(A.map);	
		},

		pointSetHide : function(data) {

			if(A.map.hasLayer(this.pointSetOverlays[data.pointSetId]))
				A.map.removeLayer(this.pointSetOverlays[data.pointSetId ]);
	
		}


	});

	A.data.ScenarioCreateView = Backbone.Marionette.Layout.extend({

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
		  'click #scenarioCancel': 'cancelScenarioCreate'
		},

		initialize : function(options) {

			this.projectId = options.projectId;
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

		    var scenario = new A.models.Scenario();

		    scenario.save(values, { iframe: true,
		                              files: this.$('form :file'),
		                              data: values,
		                              success: function(){
		                      				_this.trigger("scenarioCreate:save");
		                              }});
		}

	});


	A.data.ScenarioLayout = Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-scenario-layout'),

		events:  {
			'click #createScenario' : 'createScenario'
		},

		regions: {
		  main 	: "#main"
		},

		onShow : function() {

			var scenarioListLayout = new A.data.ScenarioListView({collection: this.collection});			

			this.main.show(scenarioListLayout);

		},

		createScenario : function(evt) {
			this.trigger("scenarioCreate");
		}

	});

	A.data.ScenarioListItem = Backbone.Marionette.ItemView.extend({

	  template: Handlebars.getTemplate('data', 'data-scenario-list-item'),

	  events: { 

	  	'click #deleteItem' : 'deleteItem'
	  	
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

	A.data.ScenarioListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('data', 'data-scenario-list'),
		itemView: A.data.ScenarioListItem,

		initialize : function() {

			
		},

		onClose : function() {

		},

		appendHtml: function(collectionView, itemView){
	    	collectionView.$("#scenarioList").append(itemView.el);
	 	},

	});




	A.data.ShapefileListItem = Backbone.Marionette.ItemView.extend({

	  template: Handlebars.getTemplate('data', 'data-shapefile-select-list-item'),

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

	A.data.ShapefileListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('data', 'data-shapefile-select-list'),
	    itemView: A.data.ShapefileListItem,

	    events: {
    		'change #shapefileSelect' : 'shapefileSelectChanged',
    		'click #uploadShapefileButton'  : 'uploadFile'
   		},

	    initialize : function(options) {

	    	_.bindAll(this, 'shapefileSelectChanged', 'shapefileListUpdated');

	    	this.collection = new A.models.Shapefiles();

			this.collection.fetch({reset: true, success: this.shapefileListUpdated });
	    },

	    getSelectedShapefileId : function() {

	    	if(this.$("#shapefileSelect").val() != "upload") {
	    		return this.$("#shapefileSelect").val();
	    	}
	    	else 
	    		return "";
	    },

	    getSelectedShapefileFieldname : function() {

	    	if(this.getSelectedShapefileId() != "") {
	    		return this.$("#shapefileFieldSelect").val(); 
	    	}

	    },

	    shapefileListUpdated : function(collection, response, options) {

	    	this.$("#uploadingShapefile").hide();

	    	if(collection.length > 0) {
				this.$("#shapefileSelect").val(collection.at(0).get("id"));
			}

			this.shapefileSelectChanged();

	    },

	    shapefileSelectChanged : function(evt) {

	    	if(this.$("#shapefileSelect").val() == "upload")  {
	    		this.$("#fieldSelectGroup").hide();
	    		this.$("#uploadShapefile").show();
		    		
	    	}
	    	else {
	    		this.$("#fieldSelectGroup").show();
	    		this.$("#uploadShapefile").hide();
	    	}
	    },

		appendHtml: function(collectionView, itemView){
	    	collectionView.$("#shapefileSelect").prepend(itemView.el);
	 	 },

	 	 uploadFile: function(event) {

	 	 	var _this = this;
		    var values = {};

		    if(event){ event.preventDefault(); }

		    _.each(this.$('form').serializeArray(), function(input){
		      values[ input.name ] = input.value;
		    })

		    var shapefile = new A.models.Shapefile();

		    shapefile.save(values, { iframe: true,
		                              files: this.$('form :file'),
		                              data: values,
		                              success: function(){
		                      
										_this.collection.fetch({reset: true, success: _this.shapefileListUpdated });
		                              }});

		    this.$("#uploadShapefile").hide();
		    this.$("#uploadingShapefile").show();
		}

	});


})(Analyst, jQuery);	

