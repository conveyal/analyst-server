var Analyst = Analyst || {};

(function(A, $) {

A.spatialData = {};

	A.spatialData.PointSetDataLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-layout'),

		regions: {
		  spatialData 	: "#main"
		},

		events:  {
			'click #createPointset' : 'createPointset'
		},


		initialize : function(options) {

			var _this = this;

			this.pointsets = new A.models.PointSets();

			A.app.instance.vent.on("setSelectedProject", function(){
				_this.pointsets.fetch({reset: true, data : {projectId: A.app.selectedProject}});

				_this.cancelNewPointset();
			});

			this.creatingNewPointSet = false;
		},

		onShow : function() {

			this.pointsets.fetch({reset: true, data : {projectId: A.app.selectedProject}});

			this.pointSetLayout = new A.spatialData.PointSetListView({collection: this.pointsets});

			this.listenTo(this.pointSetLayout, "pointsetCreate", this.createPointset);

			this.spatialData.show(this.pointSetLayout);

		},

		createPointset : function(evt) {

			var pointSetCreateLayout = new A.spatialData.PointSetCreateView({projectId: A.app.selectedProject});

			this.listenTo(pointSetCreateLayout, "pointsetCreate:save", this.saveNewPointset);

			this.listenTo(pointSetCreateLayout, "pointsetCreate:cancel", this.cancelNewPointset);

			this.spatialData.show(pointSetCreateLayout);

			this.creatingNewPointSet = true;
		},

		saveNewPointset: function(data) {

			data.projectId = A.app.selectedProject;
			this.pointsets.create(data, {wait: true});
			this.spatialData.close();

			this.onShow();

			this.creatingNewPointSet = false;
		},

		cancelNewPointset : function() {

			if(!this.spatialData)
				return;

			this.spatialData.close();

			this.onShow();

			this.creatingNewPointSet = false;
		}
	});


	A.spatialData.PointSetCreateView = Backbone.Marionette.Layout.extend({

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

		initialize : function(options) {

			this.projectId = options.projectId;
		},

		onShow : function() {

			var _this = this;
			this.shapefileListView = new A.spatialData.ShapefileSelectListView({projectId: this.projectId});

			this.shapefileSelect.show(this.shapefileListView);
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

	A.spatialData.PointSetAttributeCreateView = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-pointset-attribute-create-form'),

		events : {

			"click #attributeCancel" : "cancelPointSetAttributeCreate",
			"click #attributeSave" : "savePointSetAttributeCreate"
		},



		onShow : function() {

			var _this = this;

			this.shapefileFields = new A.models.Shapefile({id: this.model.get("shapeFileId")})
			this.shapefileFields.fetch({success: function(model, response, options) {

				_this.$("#shapeFieldSelect").empty();

				var fieldnames = model.get("fieldnames");

	    		for(var fieldname in fieldnames)
	    			_this.$("#shapeFieldSelect").append('<option value="' + fieldnames[fieldname] + '">' + fieldnames[fieldname] + '</option>');


			}});

			this.colorVal = '#0fb0b0';

			this.colorPicker = this.$('#attributeColorPicker').colorpicker({color:this.colorVal}).on('changeColor', function(ev){
			 _this.colorVal = ev.color.toHex();
			});
		},

		cancelPointSetAttributeCreate : function(evt) {

			this.trigger("pointSetAttributeCreate:cancel");

		},

		savePointSetAttributeCreate : function(evt) {

			evt.preventDefault();
			var data = Backbone.Syphon.serialize(this);
			data.color = this.colorVal;
			this.trigger("pointSetAttributeCreate:save", data);

		},

	});

	A.spatialData.PointSetListItem = Backbone.Marionette.Layout.extend({

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
	  	"change" : "render",

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

	  	this.pointSetAttributeCreateLayout = new A.spatialData.PointSetAttributeCreateView({model: this.model});

		this.listenTo(this.pointSetAttributeCreateLayout, "pointSetAttributeCreate:save", this.saveNewAttribute);

		this.listenTo(this.pointSetAttributeCreateLayout, "pointSetAttributeCreate:cancel", this.cancelNewAttribute);

		this.createAttribute.show(this.pointSetAttributeCreateLayout);

		this.$("#noAttributes").hide();
		this.$("#addAttribute").hide();
		this.$("#attributeList").hide();

	  },

	  saveNewAttribute : function(data) {
	  	this.model.addAttribute(data.name, data.description, data.color, data.fieldName);
	  	this.model.save();

	  	this.createAttribute.close();
	  	this.$("#addAttribute").show();
	  	this.$("#attributeList").show();


	  	this.render();

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

	A.spatialData.PointSetEmptyList = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('data', 'data-pointset-empty-list')
	});


	A.spatialData.PointSetListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('data', 'data-pointset-list'),
		itemView: A.spatialData.PointSetListItem,
		emptyView: A.spatialData.PointSetEmptyList,

		initialize : function() {
			this.pointSetOverlays = {};

			this.collection.on("request", function() {
				$("#pointsetList").hide();
				$("#loadingPointsets").show();
			});

			this.collection.on("reset", function() {
				$("#pointsetList").show();
				$("#loadingPointsets").hide();
			});
		},

		onShow : function() {

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
			this.pointSetOverlays[data.pointSetId] = L.tileLayer('/tile/spatial?z={z}&x={x}&y={y}&pointSetId=' + data.pointSetId + '&selectedAttributes=' + selectedAttributes).addTo(A.map);
		},

		pointSetHide : function(data) {

			if(A.map.hasLayer(this.pointSetOverlays[data.pointSetId]))
				A.map.removeLayer(this.pointSetOverlays[data.pointSetId ]);

		}

	});


	A.spatialData.ShapefileSelectListItem = Backbone.Marionette.ItemView.extend({

	  template: Handlebars.getTemplate('data', 'data-shapefile-select-list-item'),


	  onRender: function () {
		// Get rid of that pesky wrapping-div
		// Assumes 1 child element present in template.
		this.$el = this.$el.children();
		// Unwrap the element to prevent infinitely
		// nesting elements during re-render.
		this.$el.unwrap();
		this.setElement(this.$el);
      }

	});

	A.spatialData.ShapefileSelectEmptyList = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('data', 'data-shapefile-empty-select-list')
	});

	A.spatialData.ShapefileSelectListView = Backbone.Marionette.CompositeView.extend({
		template: Handlebars.getTemplate('data', 'data-shapefile-select-list'),
		itemView: A.spatialData.ShapefileSelectListItem,
		emptyView: A.spatialData.ShapefileSelectEmptyList,




		initialize : function(options) {
			this.collection = new A.models.Shapefiles();

			this.collection.fetch({reset: true, data : {projectId: A.app.selectedProject}});

			var _this = this;

			A.app.instance.vent.on("setSelectedProject", function() {
				_this.collection.fetch({reset: true, data : {projectId: A.app.selectedProject}});
			});

		},

		appendHtml: function(collectionView, itemView) {
			collectionView.$("#shapefileSelect").prepend(itemView.el);
		}
	});

	A.spatialData.ShapefileDataLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-layout'),

		regions: {
			spatialData 	: "#main"
		},

		events:  {
			'click #uploadShapefile' : 'uploadShapefile'
		},

		initialize : function(options) {

			var _this = this;

			this.shapefiles = new A.models.Shapefiles();


			A.app.instance.vent.on("setSelectedProject", function() {
				_this.shapefiles.fetch({reset: true, data : {projectId: A.app.selectedProject}});
			});
		},

		onShow : function() {

			this.shapefiles.fetch({reset: true, data : {projectId: A.app.selectedProject}});

			this.shapefileListView = new A.spatialData.ShapefileListView({collection: this.shapefiles});

			this.listenTo(this.shapefileListView, "", this.createPointset);

			this.spatialData.show(this.shapefileListView);
		},

		uploadShapefile : function(evt) {

			var shapefileUpload = new A.spatialData.ShapefileUpload({projectId: A.app.selectedProject});

			this.listenTo(shapefileUpload, "shapefileUpload:save", this.saveShapefile);

			this.listenTo(shapefileUpload, "shapefileUpload:cancel", this.cancelShapefile);

			this.spatialData.show(shapefileUpload);
		},

		saveShapefile: function(data) {
			this.shapefiles.fetch({reset: true, data : {projectId: A.app.selectedProject}});
			this.onShow();
		},

		cancelShapefile : function() {
			this.onShow();
		}

	});

	A.spatialData.ShapefileUpload = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-shapefile-upload'),

		ui: {
			name: 			'#name',
			description:  	'#description'
		},

		regions: {
		shapefileSelect 	: "#shapefileSelect"
		},

		events: {
		'click #uploadShapefileButton': 'uploadShapefile',
		'click #cancelShapefileButton': 'cancelShapefile'
		},

		initialize : function(options) {

			this.projectId = options.projectId;
		},

		onShow : function() {

			var _this = this;
			this.shapefileListView = new A.spatialData.ShapefileSelectListView({projectId: this.projectId});

			this.shapefileSelect.show(this.shapefileListView);
		},

		cancelShapefile : function(evt) {
			this.trigger("shapefileUpload:cancel");
		},

		uploadShapefile : function(evt) {

			var _this = this;
			var values = {};

			if(event){ event.preventDefault(); }

			_.each(this.$('form').serializeArray(), function(input){
				values[ input.name ] = input.value;
			})

			values.projectId = this.projectId;

			var shapefile = new A.models.Shapefile();

			shapefile.save(values, { iframe: true,
				files: this.$('form :file'),
				data: values,
				success: function() {
					_this.trigger("shapefileUpload:save");
				}
			});

			this.$("#uploadShapefile").hide();
			this.$("#uploadingShapefile").show();

		}

	});

	A.spatialData.ShapefileListItem = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('data', 'data-shapefile-list-item'),


		onRender: function () {
			// Get rid of that pesky wrapping-div
			// Assumes 1 child element present in template.
			this.$el = this.$el.children();
			// Unwrap the element to prevent infinitely
			// nesting elements during re-render.
			this.$el.unwrap();
			this.setElement(this.$el);

			var _this = this;

			var nameField = "name";

			this.$el.find("#shapefileName").editable({
				type        : 'text',
				name        : nameField,
				mode				: "inline",
				value       : this.model.get(nameField),
				pk          : this.model.get('id'),
				url         : '',
				success     : function(response, newValue) {
					_this.model.set(nameField, newValue);
					_this.model.save(nameField, newValue);
				}
			}).on("hidden", function(e, reason) {
				_this.render();
			});

			var descriptionField = "description";

			this.$el.find("#shapefileDescription").editable({
				type        : 'textarea',
				name        : descriptionField,
				mode				: "inline",
				value       : this.model.get(descriptionField),
				pk          : this.model.get('id'),
				url         : '',
				success     : function(response, newValue) {
					_this.model.set(descriptionField, newValue);
					_this.model.save(descriptionField, newValue);
				}
			}).on("hidden", function(e, reason) {
				_this.render();
			});

		}

	});

	A.spatialData.ShapefileEmptyList = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('data', 'data-shapefile-empty-list')
	});


	A.spatialData.ShapefileListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('data', 'data-shapefile-list'),
		itemView: A.spatialData.ShapefileListItem,
		emptyView: A.spatialData.ShapefileEmptyList,

		events: {
			'change #shapefileSelect' : 'shapefileSelectChanged',
			'click #uploadShapefileButton'  : 'uploadFile'
		},

		modelEvents : {
			"change" : "render",

		},

		initialize : function(options) {

		},

		appendHtml: function(collectionView, itemView){
			collectionView.$("#shapefileList").append(itemView.el);
			this.listenTo(itemView, "shapefileShow", this.pointSetShow);
			this.listenTo(itemView, "shapefileHide", this.pointSetHide);
		},

	});



})(Analyst, jQuery);
