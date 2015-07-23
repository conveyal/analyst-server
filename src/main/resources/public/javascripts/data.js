var Analyst = Analyst || {};

(function(A, $) {

A.data = {};

	A.data.DataLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-layout'),

		regions: {
		  spatialData 	: "#spatial",
		  bundleData 	: "#bundle",
			bundleData    : "#bundle"
		},

		initialize : function(options) {

			var _this = this;

			this.pointsets = new A.models.PointSets();

			this.bundles  = new A.models.Bundles();

		},

		onShow : function() {

			this.pointSetLayout = new A.data.PointSetLayout({collection: this.pointsets});

			this.listenTo(this.pointSetLayout, "pointsetCreate", this.createPointset);

			this.spatialData.show(this.pointSetLayout);

			this.bundleLayout = new A.data.BundleLayout({collection: this.bundles});

			this.listenTo(this.bundleLayout, "bundleCreate", this.createBundle);

			this.bundleData.show(this.bundleLayout);

			this.bundles.fetch({reset: true, data : {projectId: this.model.get("id")}});

		},

		createPointset : function(evt) {

			var pointSetCreateLayout = new A.data.PointSetCreateView({projectId: this.model.get("id")});

			this.listenTo(pointSetCreateLayout, "pointsetCreate:save", this.saveNewPointset);

			this.listenTo(pointSetCreateLayout, "pointsetCreate:cancel", this.cancelNewPointset);

			this.spatialData.show(pointSetCreateLayout);
			this.bundleData.close();

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

		cancelNewBundle : function() {

			this.bundleData.close();

			this.onShow();
		},


		createBundle : function(evt) {

			var _this = this;

			var bundleCreateLayout = new A.data.BundleCreateView({projectId: this.model.get("id")});

			this.listenTo(bundleCreateLayout, "bundleCreate:save", function() {
					_this.bundles.fetch({reset: true, data : {projectId: _this.model.get("id")}});
					this.onShow();
				});

			this.listenTo(bundleCreateLayout, "bundleCreate:cancel", this.cancelNewBundle)	;

			this.spatialData.close();
			this.bundleData.show(bundleCreateLayout);

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

		initialize : function(options) {

			this.projectId = options.projectId;
		},

		onShow : function() {

			var _this = this;
			this.shapefileListView = new A.data.ShapefileListView({projectId: this.projectId});

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

	A.data.PointSetAttributeCreateView = Backbone.Marionette.Layout.extend({

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

	  	this.pointSetAttributeCreateLayout = new A.data.PointSetAttributeCreateView({model: this.model});

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

	A.data.PointSetEmptyList = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('data', 'data-pointset-empty-list')
	});


	A.data.PointSetListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('data', 'data-pointset-list'),
		itemView: A.data.PointSetListItem,
		emptyView: A.data.PointSetEmptyList,

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
			this.pointSetOverlays[data.pointSetId] = L.tileLayer('/tile/spatial?z={z}&x={x}&y={y}&pointSetId=' + data.pointSetId + '&selectedAttributes=' + selectedAttributes).addTo(A.map);
		},

		pointSetHide : function(data) {

			if(A.map.hasLayer(this.pointSetOverlays[data.pointSetId]))
				A.map.removeLayer(this.pointSetOverlays[data.pointSetId ]);

		}

	});

	A.data.BundleCreateView = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-bundle-create-form'),

		ui: {
			name: 			'#name',
			description:  	'#description'
		},

		regions: {
		  shapefileSelect 	: "#shapefileSelect"
		},

		events: {
		  'click #bundleSave': 'saveBundleCreate',
		  'click #bundleCancel': 'cancelBundleCreate',
		  'change #bundleType' : 'bundleTypeChange'
		},

		initialize : function(options) {


			this.projectId = options.projectId;
		},

		onShow : function() {

			var _this = this;

			this.bundles = new A.models.Bundles();

			this.bundles.fetch({reset: true, data : {projectId: this.projectId}, success: function(collection, response, options){

				_this.$("#augmentBundleId").empty();

				for(var i in _this.bundles.models) {
					_this.$("#augmentBundleId").append('<option value="' + _this.bundles.models[i].get("id") + '">' + _this.bundles.models[i].get("name") + '</option>');
				}

			}});

			this.$("#augmentBundleId").hide();
		},

		bundleTypeChange : function(evt) {

			if(this.$('#bundleType').val() === "augment")
				this.$("#augmentBundleId").show();
			else
				this.$("#augmentBundleId").hide();
		},

		cancelBundleCreate : function(evt) {

			this.trigger("bundleCreate:cancel");

		},

		saveBundleCreate : function(evt) {

			evt.preventDefault();
			var _this = this;
		    var values = {};

		    _.each(this.$('form').serializeArray(), function(input){
		      values[ input.name ] = input.value;
		    })

		    values.projectId = this.projectId;
		    values.bundleType = this.$('#bundleType').val();

		    if(values.bundleType === "augment")
			values.augmentBundleId = this.$('#augmentBundleId').val();

		    var bundle = new A.models.Bundle();

		    bundle.save(values, { iframe: true,
		                              files: this.$('form :file'),
		                              data: values,
		                              success: function(){
		                      				_this.trigger("bundleCreate:save");
		                              }});
		}

	});


	A.data.BundleLayout = Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-bundle-layout'),

		events:  {
			'click #createBundle' : 'createBundle'
		},

		regions: {
		  main 	: "#main"
		},

		onShow : function() {

			var bundleListLayout = new A.data.BundleListView({collection: this.collection});

			this.main.show(bundleListLayout);

		},

		createBundle : function(evt) {
			this.trigger("bundleCreate");
		}

	});

	A.data.BundleListItem = Backbone.Marionette.ItemView.extend({

	  template: Handlebars.getTemplate('data', 'data-bundle-list-item'),

	  events: {

	  	'click #deleteItem' : 'deleteItem',
	  	'click #bundleCheckbox' : 'clickItem'

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

	  	var bundleId = target.data("id")

	  	if(target.prop("checked"))
	  		this.trigger("transitShow", {bundleId : bundleId});
	  	else
	  		this.trigger("transitHide", {bundleId : bundleId});

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
      }

	});

	A.data.BundleEmptyList = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('data', 'data-bundle-empty-list')
	});

	A.data.BundleListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('data', 'data-bundle-list'),
		itemView: A.data.BundleListItem,
		emptyView: A.data.BundleEmptyList,

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
	    	collectionView.$("#bundleList").append(itemView.el);
	    	this.listenTo(itemView, "transitShow", this.transitShow);
	    	this.listenTo(itemView, "transitHide", this.transitHide);
	 	},

	 	transitShow : function(data) {

	 		if(A.map.hasLayer(this.transitOverlays[data.bundleId]))
	 			A.map.removeLayer(this.transitOverlays[data.bundleId ]);

			this.transitOverlays[data.bundleId] = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&bundleId=' + data.bundleId).addTo(A.map);
		},

		transitHide : function(data) {

			if(A.map.hasLayer(this.transitOverlays[data.bundleId]))
				A.map.removeLayer(this.transitOverlays[data.bundleId ]);

		}

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

			this.projectId = options.projectId;

			this.collection.fetch({data: {projectId: this.projectId}, reset: true, success: this.shapefileListUpdated });
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

		    _.each(this.$('form').serializeArray(), function(input){
		      values[ input.name ] = input.value;
		    })

		    values.projectId = this.projectId;

		    var shapefile = new A.models.Shapefile();

		    shapefile.save(values, { iframe: true,
		                              files: this.$('form :file'),
		                              data: values,
		                              success: function(){

										_this.collection.fetch({reset: true, data: {projectId : _this.projectId}, success: _this.shapefileListUpdated });
		                              }});

		    this.$("#uploadShapefile").hide();
		    this.$("#uploadingShapefile").show();
		}

	});





})(Analyst, jQuery);
