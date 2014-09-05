var Analyst = Analyst || {};

(function(A, $) {

	A.app = new Backbone.Marionette.Application();

	A.app.addRegions({
		appRegion: "#app"
	});


	A.app.addInitializer(function(options){

		A.app.instance = new A.app.Main()

		A.app.appRegion.show(A.app.instance);

	});

	A.app.controller = Marionette.Controller.extend({

		initialize: function(options) {
			this.stuff = options.stuff;
		},

		doStuff: function() {
			this.trigger("stuff:done", this.stuff);
		}
	});


	A.app.Main = Marionette.Layout.extend({

		template: Handlebars.getTemplate('app', 'app-main'),

		regions: {
			appNav: 			"#app-nav",
			appSidePanel:       "#app-side-panel",
			appCenterPanel:     "#app-center-panel"
		},

		initialize : function() {

			this.projects  = new A.models.Projects();

			this.projects.fetch({reset: true});
		},

		onRender : function() {

			// Get rid of that pesky wrapping-div.
    			// Assumes 1 child element present in template.
    			this.$el = this.$el.children();

			this.$el.unwrap();
			this.setElement(this.$el);

			A.app.nav = new A.app.Nav();

			this.appNav.show(A.app.nav);

		},

		setSelectedProject : function(projectId) {
			A.app.selectProject = projectId;

			A.app.nav.render();
		},

		onShow: function() {

			A.map = L.map(this.$("#app-map")[0], { loadingControl: true,  zoomControl: false }).setView([0, -80.00], 4);

			L.tileLayer('http://{s}.tiles.mapbox.com/v3/conveyal.hml987j0/{z}/{x}/{y}.png', {
					attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://mapbox.com">Mapbox</a>',
					maxZoom: 18
				}).addTo(A.map);

			new L.control.zoom({position: 'bottomleft'}).addTo(A.map);
		}
	});

	A.app.Nav = Marionette.Layout.extend({

		template: Handlebars.getTemplate('app', 'app-nav'),

		regions : {
			projectDropdown: "#projectDropdown"
		},

		onShow : function() {

		},

		onRender : function() {

			// Get rid of that pesky wrapping-div.
			// Assumes 1 child element present in template.
			this.$el = this.$el.children();

			this.$el.unwrap();
			this.setElement(this.$el);

			var projectDropdownView = new A.app.ProjectListView({collection: A.app.instance.projects});

			this.projectDropdown.show(projectDropdownView);
		},

		templateHelpers: {
			selectedProject : function () {
				if(A.app.selectProject != null)
					return true;
				else
					return false;
			},
			transportDataActive : function () {
					if(this.activeTab == "transport-data")
						return true;
					else
						return false;
			},
			spatialDataActive : function () {
					if(this.activeTab == "spatial-data")
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

	A.app.ProjectListItemView = Backbone.Marionette.ItemView.extend({

		template: Handlebars.getTemplate('project', 'project-list-item-template'),
		tagName: 'li',

		initialize: function () {

			this.model.on('change', this.render);
		}

	});

	A.app.ProjectListView = Backbone.Marionette.CompositeView.extend({

		template: Handlebars.getTemplate('project', 'project-list-template'),
		itemView: A.app.ProjectListItemView,

		triggers : {
				'click #createNewProject' : 'projectList:createNewProject'
		},

		events : {
			'click .selectProject': 'selectProject'
		},

		templateHelpers: {
			selectedProject : function () {
				var selectedProject = this.collection.get(A.app.selectProject);
				if(selectedProject != null)
					return selectedProject.get("name");
				else
					return false;
			}
		},

		onRender : function() {
			// Get rid of that pesky wrapping-div.
			// Assumes 1 child element present in template.
			this.$el = this.$el.children();

			this.$el.unwrap();
			this.setElement(this.$el);
		},

		selectProject : function(evt) {
			var id = $(evt.target).data("id");
			this.setSelected(id);
			//this.trigger("projectList:selectProject", id);
		},

		appendHtml: function(collectionView, itemView){
			collectionView.$("#projectList").prepend(itemView.el);
		},

		setSelected : function(id) {
			A.app.instance.setSelectedProject(id);
			this.render();
		}

	});

	A.app.Welcome = Marionette.Layout.extend({

		template: Handlebars.getTemplate('docs', 'docs-welcome', 'en'),
	});

})(Analyst, jQuery);


$(document).ready(function() {

	Analyst.app.start();

});
