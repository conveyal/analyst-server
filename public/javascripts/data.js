var Analyst = Analyst || {};

(function(A, $) {

A.data = {};

	A.data.DataLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-layout'),

		regions: {
		  spatialData 	: "#spatial",
		  transportData : "#transport"
		},

		initialize : function(options) {

			var _this = this;

			this.pointsets = new A.models.PointSets(); 

			this.pointsets.fetch({reset: true, data : {projectId: this.model.get("id")}});

		},

		onShow : function() {

			this.pointSetLayout = new A.data.PointSetLayout({collection: this.pointsets});

			this.listenTo(this.pointSetLayout, "pointsetCreate", this.createPointset);		

			
			this.spatialData.show(this.pointSetLayout);
		
		},

		createPointset : function(evt) {

			var pointSetCreateLayout = new A.data.PointSetCreateView();		

			this.listenTo(pointSetCreateLayout, "pointsetCreate:save", this.saveNewPointset);

			this.listenTo(pointSetCreateLayout, "pointsetCreate:cancel", this.cancelNewPointset);	

			this.spatialData.show(pointSetCreateLayout);

		},

		saveNewPointset: function(data) {

			data.projectid = this.model.get("id");
			this.pointsets.create(data, {wait: true});
			this.spatialData.close();

			this.onShow();
		},

		cancelNewPointset : function() {

			this.spatialData.close();

			this.onShow();
		}
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

			this.colorVal = '#0fb0b0';

			this.colorPicker = this.$('#pointsetColorPicker').colorpicker({color:this.colorVal}).on('changeColor', function(ev){
			 _this.colorVal = ev.color.toHex();
			});

		},

		cancelPointSetCreate : function(evt) {

			this.trigger("pointsetCreate:cancel");

		},

		savePointSetCreate : function(evt) {

			evt.preventDefault();
			var data = Backbone.Syphon.serialize(this);
			data.color = this.colorVal;

			if(data.name && data.shapefileid && data.shapefieldname)
				this.trigger("pointsetCreate:save", data);

		}

	});

	A.data.PointSetListItem = Backbone.Marionette.ItemView.extend({

	  template: Handlebars.getTemplate('data', 'data-pointset-list-item'),

	  events: { 

	  	'click' : 'clickItem' 
	  },

	  clickItem : function(evt) {

	  	 $(evt.target).toggleClass("list-group-item-warning")
	  	this.trigger("pointsetToggle", $(evt.target).data("id"));
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
			this.pointSetOverlays = {}
			
		},

		onClose : function() {

			for(var id in this.pointSetOverlays){
				if(this.pointSetOverlays[id] && A.map.hasLayer(this.pointSetOverlays[id]))
					A.map.removeLayer(this.pointSetOverlays[id]);
			}

		},

		appendHtml: function(collectionView, itemView){
	    	collectionView.$("#pointsetList").append(itemView.el);
	    	this.listenTo(itemView, "pointsetToggle", this.togglePointSet);	
	 	 },

	 	togglePointSet : function(data) {

			if(this.pointSetOverlays[data] && A.map.hasLayer(this.pointSetOverlays[data]))
				A.map.removeLayer(this.pointSetOverlays[data]);
			else
				this.pointSetOverlays[data] = L.tileLayer('/tile/spatial?z={z}&x={x}&y={y}&&spatialId=' + data).addTo(A.map);
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

	    		var fieldnames = this.collection.get(this.$("#shapefileSelect").val()).get("fieldnames");

	    		this.$("#shapefileFieldSelect").empty();

	    		for(var fieldname in fieldnames)
	    			this.$("#shapefileFieldSelect").append('<option value="' + fieldnames[fieldname] + '">' + fieldnames[fieldname] + '</option>');
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

