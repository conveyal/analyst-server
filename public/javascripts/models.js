var Analyst = Analyst || {};

(function(A, $) {

	A.models = {}

	A.models.Project = Backbone.Model.extend({
	    urlRoot: '/api/project/',

	    defaults: {
	      id: null,
	      name: null,
	      description: null,
	      boundary: null,
	      defaultLat: null,
	      defaultLon: null,
	      defaultZoom: null,
		 defaultScenario: null
		},

		selectedProject : function(evt) {
			return id === A.app.selectedProject;
		}

	  });

	A.models.Projects = Backbone.Collection.extend({
	  type: 'Projects',
	  model: A.models.Project,
	  url: '/api/project',
	  comparator: 'name'

	});

	A.models.Shapefile = Backbone.Model.extend({
		urlRoot: '/api/shapefile/',

		defaults: {
			id: null,
			name: null,
			description: null,
			fieldnames: [],
			projectId: null,
			attributes: []
		}

	});

	A.models.Shapefiles = Backbone.Collection.extend({
	  type: 'Shapefiles',
	  model: A.models.Shapefile,
	  url: '/api/shapefile',
	  comparator: 'name'

	});

	A.models.Scenario = Backbone.Model.extend({
		urlRoot: '/api/scenario/',

		defaults: {
			id: null,
			name: null,
			description: null,
			filenames: [],
			status: null
		}
	});

	A.models.Scenarios = Backbone.Collection.extend({
	  type: 'Scenarios',
	  model: A.models.Scenario,
	  url: '/api/scenario'
	});

	A.models.Query = Backbone.Model.extend({
		urlRoot: '/api/query/',

		defaults: {
			id: null,
			name: null,
			mode: null,
			pointSetId: null,
			scenarioId: null,
			status: null,
			totalPoints: null,
			completePoints: null
		},

		updateStatus : function() {

		},

		getPoints : function() {

		}

	});

	A.models.Queries = Backbone.Collection.extend({
	  type: 'Queries',
	  model: A.models.Query,
	  url: '/api/query'
	});


	A.models.User = Backbone.Model.extend({
		urlRoot: '/api/user/',

		defaults: {
			id: null,
			name: null,
			email: null,
			projectPermissions: null
		},

	});

	A.models.Users = Backbone.Collection.extend({
		type: 'Users',
		model: A.models.User,
		url: '/api/user'
	});




})(Analyst, jQuery);
