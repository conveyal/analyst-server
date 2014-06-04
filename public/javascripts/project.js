var Analyst = Analyst || {};

(function(A, $) {

	A.project = {};

	A.project.ProjectController = Marionette.Controller.extend({
	        
	    initialize: function(options){
	        
	        this.region = options.region;

	        this.projects  = new A.models.Projects();
	    
	        this.projects.fetch({reset: true});
	    },
	    
	    show: function(){

	    	var projectLayout = new A.project.ProjectLayout({projects: this.projects});
	    	this.region.show(projectLayout);
	        
	    }
	});


	A.project.ProjectLayout = Backbone.Marionette.Layout.extend({
		
		template: Handlebars.getTemplate('project', 'project-layout-template'),

		regions: {
		  projectList: 	 "#projectList",
		  projectDetail: "#main"
		},

		initialize : function(options) {
			this.projects = options.projects;
		},

		onShow : function() {

			var _this = this;

			this.projectListView = new A.project.ProjectListView({collection: this.projects});

			this.projects.on("add", function(project) {
				_this.selectedProject = null;

				_this.projectListView.setSelected(project.id);
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

			if(this.selectedProject != null) {
	    
	    		var projectDetailLayout = new A.project.ProjectDetailLayout({model: this.selectedProject});

	    		this.projectDetail.show(projectDetailLayout);
	    	}

		},

		getSelectedMapState : function() {

			if(this.selectedProject != null) {

				var lat = this.selectedProject.get("defaultLat");
				var lng = this.selectedProject.get("defaultLon");
				var zoom = this.selectedProject.get("defaultZoom");

				if(lat && lng && zoom) {
					var latlng = L.latLng(lat, lng);

					A.map.setView(latlng, zoom);
				}
			
			}
		},

		saveSelectedMapState  : function() {

			if(this.selectedProject != null) {

				var latlng = A.map.getCenter();

				this.selectedProject.set("defaultLat", latlng.lat);
				this.selectedProject.set("defaultLon", latlng.lng);
				this.selectedProject.set("defaultZoom", A.map.getZoom());
			
				this.selectedProject.save();

			}
		},

		selectProject : function(id) {
	    	
			this.saveSelectedMapState();

	    	this.selectedProject = this.projects.get(id);

	    	if(this.selectedProject != null) {
	    		this.projectDetail.close();

	    		var projectDetailLayout = new A.project.ProjectDetailLayout({model: this.selectedProject});

	    		this.projectDetail.show(projectDetailLayout);
	    	}

	    	this.getSelectedMapState();
	    }

	});

	A.project.ProjectDetailLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('project', 'project-detail-layout-template'), 

		regions: {
		  projectDetail: 	 "#detail"
		},

		events: {
		  'click li': 'tabClick'
		},

		initialize : function () {
			_.bindAll(this, 'tabClick');
		},

		onRender : function() {

			if(this.activeTab == "settings") {

				var projectSettings = new A.project.ProjectSettingsView({model: this.model});

				this.projectDetail.show(projectSettings);
			}

			if(this.activeTab == "data") {

				var dataController = new A.data.DataController({model: this.model, region: this.projectDetail});

				dataController.show();
			}

			if(this.activeTab == "analysis") {

				var analysisController = new A.analysis.AnalysisController({model: this.model, region: this.projectDetail});

				analysisController.show();
			}

		},


		tabClick : function(evt) {

			if($(evt.target).data("tab") && $(evt.target).data("tab") != this.activeTab) {

				this.activeTab = $(evt.target).data("tab");

				this.render();
			}
		},

		templateHelpers: {
	     	dataActive : function () {
	     		if(this.activeTab == "data") 
	     			return true;
	     		else
	     			return false;
	        },
	        analysisActive : function () {
	     		if(this.activeTab == "analysis") 
	     			return true;
	     		else
	     			return false;
	        },
	        settingsActive : function () {
	     		if(this.activeTab == "settings") 
	     			return true;
	     		else
	     			return false;
	        },
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
		  'click .cancel': 'cancelProject'
		},

		cancelProject : function(evt) {

			this.trigger("projectCreate:cancel");

		},

		saveProject : function(evt) {

			evt.preventDefault();
			var data = Backbone.Syphon.serialize(this);

			var latlng = A.map.getCenter();

			data.defaultLat = latlng.lat;
			data.defaultLon = latlng.lng;
			data.defaultZoom = A.map.getZoom();

			this.trigger("projectCreate:save", data);

		}

	});

	A.project.ProjectSettingsView = Backbone.Marionette.ItemView.extend({
	 	template: Handlebars.getTemplate('project', 'project-settings-template'),

	 	ui: {
			name: 			'#name',
			description:  	'#description'
		},

		events: {
		  'click .save': 'saveProject'
		},

		saveProject : function(evt) {

			evt.preventDefault();
			var data = Backbone.Syphon.serialize(this);

			var latlng = A.map.getCenter();

			data.defaultLat = latlng.lat;
			data.defaultLon = latlng.lng;
			data.defaultZoom = A.map.getZoom();

			this.trigger("projectCreate:save", data);

		}
	});

	A.project.ProjectListItemView = Backbone.Marionette.ItemView.extend({
	  template: Handlebars.getTemplate('project', 'project-list-item-template'),
	  tagName: 'li',

	  initialize: function () {
		
		this.model.on('change', this.render);
	  }

	});

	A.project.ProjectListView = Backbone.Marionette.CompositeView.extend({
	  
	  template: Handlebars.getTemplate('project', 'project-list-template'),
	  itemView: A.project.ProjectListItemView,
	 
	  triggers : {
			'click #createNewProject' : 'projectList:createNewProject'
	  },

	  events : {
	  	'click .selectProject': 'selectProject'
	  },

	  templateHelpers: {
     	selectedProject : function () {
     		var selectedProject = this.collection.get(this.selectedId);
     		if(selectedProject != null)
            	return selectedProject.get("name");
            else
            	return false;
        }
      },

	  selectProject : function(evt) {
	  	var id = $(evt.target).data("id");
	  	this.setSelected(id);
	  	this.trigger("projectList:selectProject", id);
	  },

	  appendHtml: function(collectionView, itemView){
	    collectionView.$("ul").prepend(itemView.el);
	  },

	  setSelected : function(id) {

	  	this.selectedId = id;
	  	this.render();
	  }

	});

})(Analyst, jQuery);	

