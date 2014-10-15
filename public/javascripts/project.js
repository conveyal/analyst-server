var Analyst = Analyst || {};

(function(A, $) {

	A.project = {};

	A.project.ProjectCreateView = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('project', 'project-create-template'),

		ui: {
			name: 			'#name',
			description:  	'#description'
		},

		events: {
		  'click .save': 'saveProject',
		  'click .cancel': 'cancelProject',
		  'click #setLocation' : "setLocation",
		  'click #deleteProject' : 'deleteProject'
		},

		initialize : function(options) {

			if(options.id) {
				this.model = new A.models.Project({id: options.id});
				this.model.fetch();
				this.model.on('change', this.render);
			}
		},

		cancelProject : function(evt) {

			A.app.instance.vent.trigger("closeSidePanel");

		},

		saveProject : function(evt) {

			evt.preventDefault();
			var data = Backbone.Syphon.serialize(this);

			if(!this.defaultLat) {
				var latlng = A.map.getCenter();

				data.defaultLat = latlng.lat;
				data.defaultLon = latlng.lng;
				data.defaultZoom = A.map.getZoom();
			}
			else {
				data.defaultLat = this.defaultLat;
				data.defaultLon = this.defaultLon;
				data.defaultZoom = this.defaultZoom;
			}

			var project;

			if(this.model)
				project = this.model;
			else
				project = new A.models.Project();

			project.save(data, {success: function(model){
					A.app.controller.setProject(model.id)
					A.app.projects.fetch({reset: true, success: A.app.controller.initProjects});
			}});

			A.app.instance.vent.trigger("closeSidePanel");
		},

		deleteProject : function(evt) {

			if(this.model) {
				this.model.destroy({success: function(model){
					A.app.projects.fetch({reset: true, success: A.app.controller.initProjects});
				}});
			}

			A.app.instance.vent.trigger("closeSidePanel");
		},

		setLocation : function(evt) {

			var latlng = A.map.getCenter();

			this.defaultLat = latlng.lat;
			this.defaultLon = latlng.lng;
			this.defaultZoom = A.map.getZoom();

			var locationString = this.defaultLat.toFixed(3) + ", "  + this.defaultLon.toFixed(3) + " (z" + this.defaultZoom + ")";

			this.$('#projectLocation').html(locationString);

		}

	});

	A.project.ProjectSettingsView = Backbone.Marionette.ItemView.extend({
	 	template: Handlebars.getTemplate('project', 'project-create-template'),

	 	ui: {
			name: 		'#name',
			description:  	'#description'
		},

		events: {
		  'click .save': 'saveProject',
		  'click #setLocation' : "setLocation"
		},

		onShow : function() {

			var latlng = A.map.getCenter();

			var data = {};

			data.defaultLat = latlng.lat;
			data.defaultLon = latlng.lng;
			data.defaultZoom = A.map.getZoom();

			var locationString = data.defaultLat.toFixed(3) + ", "  + data.defaultLon.toFixed(3) + " (z" + data.defaultZoom + ")";

			this.$('#projectLocation').html(locationString);

		},

		saveProject : function(evt) {

			evt.preventDefault();
			var data = Backbone.Syphon.serialize(this);

			this.model.set(data);

			this.model.save();

		},

		setLocation : function(evt) {

			var latlng = A.map.getCenter();

			var data = {};

			data.defaultLat = latlng.lat;
			data.defaultLon = latlng.lng;
			data.defaultZoom = A.map.getZoom();

			this.model.set(data);

			var locationString = data.defaultLat.toFixed(3) + ", "  + data.defaultLon.toFixed(3) + " (z" + data.defaultZoom + ")";

			this.$('#projectLocation').html(locationString);

		}
	});


})(Analyst, jQuery);
