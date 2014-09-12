var Analyst = Analyst || {};

(function(A, $) {

	A.app = {};

	A.app.instance = new Backbone.Marionette.Application();

	A.app.instance.addRegions({
		appRegion: "#app"
	});


	A.app.instance.addInitializer(function(options){

		A.app.projects  = new A.models.Projects();

		A.app.controller =  new A.app.AppController();

		A.app.appRouter = new A.app.Router({controller: A.app.controller});

		A.app.projects.fetch({reset: true, success: A.app.controller.initProjects});

		if( ! Backbone.History.started) Backbone.history.start();

	});

	A.app.Router = Marionette.AppRouter.extend({
  		appRoutes: {
    			':project/:x/:y/:z(/:namespace/*subroute)': 'invokeSubRoute',
			'home': 'index'
		}
	});

	A.app.AppController = Marionette.Controller.extend({

		index: function(options) {
			A.app.main = new A.app.Main();

			A.app.instance.appRegion.show(A.app.main);
			A.app.main.showWelcome();
		},

		invokeSubRoute: function(project, x, y, z, namespace, subroute) {

		    this.setProject(project);

			if(!A.app.main) {
				A.app.main = new A.app.Main();

				A.app.instance.appRegion.show(A.app.main);
			}

			this.setMap(x, y, z);

			if(namespace)
				this.setTab(namespace);

		},

		setMap : function(x,y,z) {
			this.x = x;
			this.y = y;
			this.z = z;

			A.map.setView(new L.LatLng(y,x), z);

			this.updateNav(true);
		},

		initProjects : function() {
			A.app.controller.setProject();
		},

		setProject : function(project) {

			if(project)
				A.app.selectedProject = project;

			if(!A.app.selectedProject)
				return;

			A.app.instance.vent.trigger("setSelectedProject");

			if(A.app.projects) {
				var p = A.app.projects.get(project);
				if(p) {
					this.setMap(p.get('defaultLon'), p.get('defaultLat'), p.get('defaultZoom'));
					return;
				}
			}

			this.updateNav(true);
		},

		setTab : function(tab) {

			this.selectedTab = tab;
			this.updateNav(true);

			A.app.instance.vent.trigger("setSelectedTab", this.selectedTab);

			A.app.main.closeSidePanel();

			if(this.selectedTab == "transport-data") {
				var transportDataPanel = new A.transportData.DataLayout();
				A.app.main.showSidePanel(transportDataPanel);
			}
			else if(this.selectedTab == "spatial-data-pointsets") {
				var pointSetDataPanel = new A.spatialData.PointSetDataLayout();
				A.app.main.showSidePanel(pointSetDataPanel);
			}
			else if(this.selectedTab == "analysis-single") {
				var analaysisPanel = new A.analysis.AnalysisSinglePointLayout;
				A.app.main.showSidePanel(analaysisPanel);
			}


		},

		updateNav : function(replace) {

			if(A.app.selectedProject) {
				var baseUrl = '/' + A.app.selectedProject + '/' + this.x  + '/' + this.y  + '/' +  this.z;

				if(this.selectedTab)
					baseUrl = baseUrl + '/' + this.selectedTab + "/";

				A.app.appRouter.navigate(baseUrl, {replace: replace});
			}
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

		showSidePanel : function (mainLayout) {
			this.$('#app-side-panel').show();
			this.appSidePanel.show(mainLayout);

		},

		closeSidePanel : function() {
			this.appSidePanel.close();
			this.$('#app-side-panel').hide();
		},

		showWelcome : function() {
			var welcomeLayout = new A.app.Welcome();

			this.$('#app-side-panel').show();
			this.appSidePanel.show(welcomeLayout);
		},

		onShow: function() {

			A.map = L.map(this.$("#app-map")[0], { loadingControl: true,  zoomControl: false }).setView([0, -80.00], 4);

			L.tileLayer('http://{s}.tiles.mapbox.com/v3/conveyal.hml987j0/{z}/{x}/{y}.png', {
					attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://mapbox.com">Mapbox</a>',
					maxZoom: 18
				}).addTo(A.map);

			new L.control.zoom({position: 'bottomleft'}).addTo(A.map);

			A.map.on("moveend", function(evt) {

				if(A.map.getCenter().lng != -80 && A.map.getCenter().lat != 0)
					A.app.controller.setMap(A.map.getCenter().lng, A.map.getCenter().lat, A.map.getZoom());
			});
		}
	});

	A.app.Nav = Marionette.Layout.extend({

		template: Handlebars.getTemplate('app', 'app-nav'),

		regions : {
			projectDropdown: "#projectDropdown"
		},

		events : {
			'click #transportDataTab' : 'clickTransportData',
			'click #spatialDataTabShapefilesListItem' : 'clickSpatialDataTabShapefilesListItem',
			'click #spatialDataTabPointSetsListItem' : 'clickSpatialDataTabPointSetsListItem',
			'click #analysisTabSinglePoint' : 'clickAnalysisTabSinglePoint',
			'click #analysisTabRegional' : 'clickAnalysisTabRegional'
		},

		initialize : function() {
			var _this = this;
			A.app.instance.vent.on("setSelectedProject", function(){
			  _this.render();
			});

			A.app.instance.vent.on("setSelectedTab", function(tab){
			_this.activeTab = tab;
			_this.render();
			});
		},

		onRender : function() {

			// Get rid of that pesky wrapping-div.
			// Assumes 1 child element present in template.
			this.$el = this.$el.children();

			this.$el.unwrap();
			this.setElement(this.$el);

			var projectDropdownView = new A.app.ProjectListView({collection: A.app.projects});

			this.projectDropdown.show(projectDropdownView);
		},

		clickTransportData : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("transport-data");
		},

		clickSpatialDataTabShapefilesListItem : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("spatial-data-shapefiles");
		},

		clickSpatialDataTabPointSetsListItem : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("spatial-data-pointsets");
		},

		clickAnalysisTabSinglePoint : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("analysis-single");
		},

		clickAnalysisTabRegional : function(evt) {
			evt.preventDefault();
			A.app.controller.setTab("analysis-regional");
		},

		templateHelpers: {
			selectedProject : function () {
				if(A.app.selectedProject != null)
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
					if(this.activeTab == "spatial-data-shapefiles" || this.activeTab == "spatial-data-pointsets")
						return true;
					else
						return false;
			},
			analysisActive : function () {
					if(this.activeTab == "analysis-single" ||  this.activeTab == "analysis-regional")
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

		initialize : function() {
		},

		templateHelpers: {
			selectedProject : function () {
				var selectedProject = this.collection.get(A.app.selectedProject);
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
			A.app.controller.setProject(id);
		},

		appendHtml: function(collectionView, itemView){
			collectionView.$("#projectList").prepend(itemView.el);
		}
	});

	A.app.SidePanel = Marionette.Layout.extend({

		template: Handlebars.getTemplate('app', 'app-side-panel'),

		regions: {
			main : '#main'
		},



		initialize : function (options) {

			this.mainLayout = options.mainLayout;
		},

		onShow : function() {

			this.main.show(this.mainLayout);



		}
	});



	A.app.Welcome = Marionette.Layout.extend({

		template: Handlebars.getTemplate('docs', 'docs-welcome', 'en'),

		events : {
			'click #startTour': 'startTour'
		},

		startTour : function(evt) {
			A.app.tour = new Tour({

			steps: [
			{
			element: "#transportDataTab",
			title: "Build Transport Scenarios",
			content: "Create a transport scenario by clicking \"Add\" above and uploading a GTFS feed, or create a GTFS feed using TransitMix or TransitWand."
			},
			{
			element: "#spatialDataTab",
			title: "Manage Spatial Data",
			content: "Spatial data sets allow measurement of access to spatially distributed characteristics like population or jobs. Create one by clicking \"Add\" above and upload a Shapefile."
			}
			]});

			// Initialize the tour
			A.app.tour.init();

			// Start the tour
			A.app.tour.start(true);
		}

	});

})(Analyst, jQuery);


$(document).ready(function() {

	Analyst.app.instance.start();

});
