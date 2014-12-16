var Analyst = Analyst || {};

(function(A, $) {

A.spatialData = {};

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

		events: {
			'click #toggleLayer': 'toggleLayer',
			'click #zoomToExtent': 'zoomToExtent'
		},


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

			_.each(this.model.get("shapeAttributes"), function(shapeAttribute) {
				if(shapeAttribute.numeric) {

					var attributeName;
					if(shapeAttribute.name === shapeAttribute.fieldName)
						attributeName = shapeAttribute.name;
					else
						attributeName  = shapeAttribute.name + " (" + shapeAttribute.fieldName + ")";

					_this.$el.find("#shapefileAttribute").append('<option value="' + shapeAttribute.id + '">' + attributeName + '</option>');

				}
			});

			this.$el.find("#zoomToExtent").hide();

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

			if(this.shapefileOverlay) {

				this.$("#zoomToExtent").show();

				this.$("#toggleLayer").addClass("glyphicon-eye-open");
				this.$("#toggleLayer").removeClass("glyphicon-eye-close");
				this.$("#toggleLayer").removeClass("gray-icon");
			}
		},

		onClose : function() {
			if(this.shapefileOverlay && A.map.hasLayer(this.shapefileOverlay))
				A.map.removeLayer(this.shapefileOverlay);
		},

		zoomToExtent : function(evt) {
			var bounds = L.latLngBounds([L.latLng(this.model.get("envelope").maxY, this.model.get("envelope").minX), L.latLng(this.model.get("envelope").minY, this.model.get("envelope").maxX)])
			A.map.fitBounds(bounds);
		},

		toggleLayer : function(data) {
			if(this.shapefileOverlay) {

				if(A.map.hasLayer(this.shapefileOverlay))
					A.map.removeLayer(this.shapefileOverlay);

				this.shapefileOverlay = false;

				this.$("#zoomToExtent").hide();

				this.$("#toggleLayer").removeClass("glyphicon-eye-open");
				this.$("#toggleLayer").addClass("glyphicon-eye-close");
				this.$("#toggleLayer").addClass("gray-icon");

			}
			else {
				this.shapefileOverlay = L.tileLayer('/tile/shapefile?z={z}&x={x}&y={y}&shapefileId=' + this.model.id).addTo(A.map);

				this.$("#zoomToExtent").show();

				this.$("#toggleLayer").addClass("glyphicon-eye-open");
				this.$("#toggleLayer").removeClass("glyphicon-eye-close");
				this.$("#toggleLayer").removeClass("gray-icon");

		 }
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
