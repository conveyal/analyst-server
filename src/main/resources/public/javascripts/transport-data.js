var Analyst = Analyst || {};

(function(A, $) {

A.transportData = {};

	A.transportData.DataLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-layout'),

		regions: {
		  bundleData : "#main"
		},

		initialize : function(options) {

			var _this = this;

			this.bundles  = new A.models.Bundles();

		},

		onShow : function() {

			this.bundleLayout = new A.transportData.BundleLayout({collection: this.bundles});

			this.listenTo(this.bundleLayout, "bundleCreate", this.createBundle);

			this.bundleData.show(this.bundleLayout);

			this.bundles.fetch({reset: true, data : {projectId: A.app.selectedProject}});

			var _this = this;

			A.app.instance.vent.on("setSelectedProject", function() {
				_this.bundles.fetch({reset: true, data : {projectId: A.app.selectedProject}});
			});

		},

		cancelNewBundle : function() {

			this.bundleData.close();

			this.onShow();
		},


		createBundle : function(evt) {

			var _this = this;

			var bundleCreateLayout = new A.transportData.BundleCreateView({projectId: A.app.selectedProject});

			this.listenTo(bundleCreateLayout, "bundleCreate:save", function() {
					_this.bundles.fetch({reset: true, data : {projectId: A.app.selectedProject}});
					this.onShow();
				});

			this.listenTo(bundleCreateLayout, "bundleCreate:cancel", this.cancelNewBundle)	;

			this.bundleData.show(bundleCreateLayout);

		},


	});


	A.transportData.BundleCreateView = Backbone.Marionette.Layout.extend({

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

		    // turn it back into a hidden form with all the values we want, and submit it
		    var form = $('<form method="POST" action="/api/bundle" enctype="multipart/form-data">')
		    	.append(this.$('form :file'))

		    for (var v in values) {
		    	if (values.hasOwnProperty(v)) {
		    		form.append($('<input type="hidden" name="' + v + '" value="' + values[v] +'" />'))
		    	}
		    }

		    // pass in hash
		    form.append($('<input type="hidden" name="location" value="' + window.location.hash + '" />'))

            // recent versions of chrome and firefox require the form to be attached to the document for submission to
            // succeed, so add it to document body.
            // Since we're reloading the page on form submission it doesn't matter if we mess up the formatting of the page
            $('body').append(form)

		    form.submit()
		}

	});


	A.transportData.BundleLayout = Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-bundle-layout'),

		events:  {
			'click #createBundle' : 'createBundle'
		},

		regions: {
		  main 	: "#main"
		},

		onShow : function() {

			var bundleListLayout = new A.transportData.BundleListView({collection: this.collection});

			this.main.show(bundleListLayout);

		},

		createBundle : function(evt) {
			this.trigger("bundleCreate");
		}

	});

	A.transportData.BundleListItem = Backbone.Marionette.ItemView.extend({

	  template: Handlebars.getTemplate('data', 'data-bundle-list-item'),

	  events: {

	  	'click #deleteBundle' : 'deleteBundle',
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

	  	var bundleId = this.model.id;

			if(this.transitOverlay) {
				if(A.map.hasLayer(this.transitOverlay))
					A.map.removeLayer(this.transitOverlay);

					this.transitOverlay = false;

					this.$("#zoomToExtent").addClass("disabled");

					this.$("#toggleLayerIcon").removeClass("glyphicon-eye-open");
					this.$("#toggleLayerIcon").addClass("glyphicon-eye-close");
			}
			else {
				this.transitOverlay = L.tileLayer('/tile/transit?z={z}&x={x}&y={y}&bundleId=' + bundleId	).addTo(A.map);

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

	  deleteBundle: function(evt) {
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

				this.$el.find("#bundleName").editable({
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

				this.$el.find("#bundleDescription").editable({
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

	A.transportData.BundleEmptyList = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('data', 'data-bundle-empty-list')
	});

	A.transportData.BundleListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('data', 'data-bundle-list'),
		itemView: A.transportData.BundleListItem,
		emptyView: A.transportData.BundleEmptyList,

		appendHtml: function(collectionView, itemView){
	    	collectionView.$("#bundleList").append(itemView.el);

	 	}

	});




})(Analyst, jQuery);
