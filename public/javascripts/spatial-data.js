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

		tagName : "li",

		events: {
			'click #zoomToExtent': 'zoomToExtent',
			'click #deleteShapefile': 'deleteShapefile',
			'click #removeAttribute' : 'removeAttribute',
			'click #addHidden' : 'addHidden',
			'click #attributeRadio' : 'attributeRadio',
			'click #removeAllAttributes' : 'removeAllAttributes'
		},

		initialize : function () {
			this.model.on('change', this.render);
		},

		onRender: function () {

			var _this = this;

			var nameField = "name";

			if(this.shapefileOverlay) {
				if(A.map.hasLayer(this.shapefileOverlay))
					A.map.removeLayer(this.shapefileOverlay);

				this.shapefileOverlay = false;
			}

			this.$('#colorPicker').colorpicker({
				color: "ccf"
			}).on('changeColor', function(evt) {

				var attributeId = $(evt.target).data("id");
				var color = evt.color.toHex();

				$(this).css("background-color", color);

				var match = _.find(_this.model.get("shapeAttributes"), function(val){return val.fieldName === attributeId});
				match.color = color;

			}).on('hidePicker', function(evt) {
				_this.model.save();
			});

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

			_.each(this.model.getNumericAttributes(), function(shapeAttribute) {
						_this.$el.find('#attributeName[data-id='+ shapeAttribute.fieldName +']').editable({
							type        : 'text',
							name        : descriptionField,
							mode				: "inline",
							value       : shapeAttribute.name,
							url         : '',
							success     : function(response, newValue) {
								var match = _.find(_this.model.get("shapeAttributes"), function(val){return val.fieldName === shapeAttribute.fieldName});
								match.name = newValue;
								_this.model.save();
							}
						}).on("hidden", function(e, reason) {
							_this.render();
						});
			});

			_.each(this.model.getNumericAttributes(), function(shapeAttribute) {
					_this.$el.find('#attributeDescription[data-id='+ shapeAttribute.fieldName +']').editable({
						type        : 'text',
						name        : descriptionField,
						mode				: "inline",
						value       : shapeAttribute.description,
						url         : '',
						success     : function(response, newValue) {
							var match = _.find(_this.model.get("shapeAttributes"), function(val){return val.fieldName === shapeAttribute.fieldName});
							match.description = newValue;
							_this.model.save();
						}
					}).on("hidden", function(e, reason) {
						_this.render();
					});
			});

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


		},

		onClose : function() {
			if(this.shapefileOverlay && A.map.hasLayer(this.shapefileOverlay))
				A.map.removeLayer(this.shapefileOverlay);
		},

		deleteShapefile: function(evt) {
			this.model.destroy();
		},

		zoomToExtent : function(evt) {

			// prevent bootstrap toggle state
			evt.stopImmediatePropagation();

			var bounds = L.latLngBounds([L.latLng(this.model.get("bounds").north, this.model.get("bounds").east), L.latLng(this.model.get("bounds").south, this.model.get("bounds").west)])
			A.map.fitBounds(bounds);
		},

		removeAttribute : function(evt) {


			var attributeId = $(evt.target).data("id");

			var match = _.find(this.model.get("shapeAttributes"), function(val){return val.fieldName === attributeId});
			match.hide = true;
			this.model.save();

		},

		removeAllAttributes : function(evt) {

			evt.preventDefault();
			evt.stopImmediatePropagation();

			var newAttributes = _.map(this.model.get("shapeAttributes"), function(val){
				val.hide = true
				return val;
			});

			this.model.set("shapeAttributes", newAttributes);
			this.model.save();

		},

		addHidden : function(evt) {
			var attributeId = this.$("#hiddenAttributes").val();

			var match = _.find(this.model.get("shapeAttributes"), function(val){return val.fieldName === attributeId});
			match.hide = false;
			this.model.save();
		},

		attributeRadio : function(evt) {

			// prevent bootstrap toggle state
			evt.stopImmediatePropagation();

			var attributeId = $(evt.target).data("id");

			if(this.shapefileOverlay) {
				if(A.map.hasLayer(this.shapefileOverlay))
					A.map.removeLayer(this.shapefileOverlay);

				this.shapefileOverlay = false;
			}

			if(attributeId) {
				this.shapefileOverlay = L.tileLayer('/tile/shapefile?z={z}&x={x}&y={y}&attributeName=' +  attributeId + '&shapefileId=' + this.model.id).addTo(A.map);
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
