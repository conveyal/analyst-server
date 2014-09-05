var Analyst = Analyst || {};

(function(A, $) {

	A.project = {};

	A.project.ProjectLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('project', 'project-layout-template'),

		regions: {
		  projectList:   "#projectList",
		  projectDetail: "#main"
		},

		initialize : function(options) {
			this.projects = options.projects;
		},

		onShow : function() {

			var _this = this;

			this.projectListView = new A.project.ProjectListView({collection: this.projects});

			this.projects.on("add", function(project) {
				A.app.selectedProject = project.id;
				_this.projectListView.setSelected(project.id);
				_this.selectProject(project.id);
				//_this.projectListView.setSelected(project.id);
			});

		    this.listenTo(this.projectListView, "projectList:createNewProject", this.createNewProject);

			this.listenTo(this.projectListView, "projectList:selectProject", this.selectProject);

	       	this.projectList.show(this.projectListView);
		},

		createNewProject : function(args) {

			this.projectDetail.close();

			var projectCreateView = new A.project.ProjectCreateView({model: new A.models.Project()});

			this.listenTo(projectCreateView, "projectCreate:save", this.saveNewProject);

			this.listenTo(projectCreateView, "projectCreate:cancel", this.cancelNewProject);

			this.projectDetail.show(projectCreateView);
		},

		saveNewProject : function(data) {
			this.projects.create(data, {wait: true});
			this.projectDetail.close();
		},

		cancelNewProject : function() {

			this.projectDetail.close();

			if(A.app.selectedProject != null) {

		    		var projectDetailLayout = new A.project.ProjectDetailLayout({model: A.app.selectedProject});

		    		this.projectDetail.show(projectDetailLayout);
		    	}

		},

		getSelectedMapState : function() {

			if(A.app.selectedProject != null) {

				var lat = A.app.selectedProject.get("defaultLat");
				var lng = A.app.selectedProject.get("defaultLon");
				var zoom = A.app.selectedProject.get("defaultZoom");

				if(lat && lng && zoom) {
					var latlng = L.latLng(lat, lng);

					A.map.setView(latlng, zoom);
				}

			}
		},


		selectProject : function(id) {

		    	A.app.selectedProject = this.projects.get(id);

		    	if(A.app.selectedProject != null) {


		    		this.projectDetail.close();

		    		var projectDetailLayout = new A.project.ProjectDetailLayout({model: A.app.selectedProject});

		    		this.projectDetail.show(projectDetailLayout);
		    	}

		    	this.getSelectedMapState();
		}

	});

	A.project.ProjectDetailLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('project', 'project-detail-layout-template'),

		regions: {
		  projectDetail: 	 "#projectDetail"
		},

		events: {
		  'click li': 'tabClick'
		},

		initialize : function () {
			_.bindAll(this, 'tabClick');

			this.activeTab = "data";
		},

		onRender : function() {

			if(this.activeTab == "settings") {

				var projectSettings = new A.project.ProjectSettingsView({model: this.model});

				this.projectDetail.show(projectSettings);
			}

			if(this.activeTab == "spatial-data") {

				var dataLayout = new A.data.PointSetLayout({model: this.model});

				this.projectDetail.show(dataLayout);
			}


			if(this.activeTab == "transport-data") {

				var dataLayout = new A.transportData.ScenarioLayout({model: this.model});

				this.projectDetail.show(dataLayout);
			}

			if(this.activeTab == "analysis") {

				var analysisController = new A.analysis.AnalysisController({model: this.model, region: this.projectDetail});

				analysisController.show();
			}

			$('#projectDetail').height($(window).height() - 300);

			$(window).resize(function() {
			    $('#projectDetail').height($(window).height() - 300);
			});


		},


		tabClick : function(evt) {

			if($(evt.target).data("tab") && $(evt.target).data("tab") != this.activeTab) {

				this.activeTab = $(evt.target).data("tab");

				this.render();
			}
		}

	});

	A.project.ProjectCreateView = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('project', 'project-create-template'),

		ui: {
			name: 			'#name',
			description:  	'#description'
		},

		events: {
		  'click .save': 'saveProject',
		  'click .cancel': 'cancelProject',
		  'click #setLocation' : "setLocation"
		},

		cancelProject : function(evt) {

			this.trigger("projectCreate:cancel");

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

			this.trigger("projectCreate:save", data);

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
	 	template: Handlebars.getTemplate('project', 'project-settings-template'),

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
