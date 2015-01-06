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
			projectId: null,
			shapeAttributes: [],
			featureCount: null
		},

		/**
		 * Get the columns that are numeric and thus can be used for analysis.
		 */
		getNumericAttributes: function () {
			return _.filter(this.get('shapeAttributes'), function (a) {
				return a.numeric;
			})
		},

		/**
		 * Get the category ID of this shapefile in a pointset. This does the same thing as Attribute.convertNameToId.
		 */
		getCategoryId : function () {
			return this.get('name').toLowerCase().trim().replace(/ /g, '_').replace(/\W+/g, '');
		}

	});

	/** static function to get the human-readable, localized name of an attribute */
	A.models.Shapefile.attributeName = function (attr) {
		if (attr.name == attr.fieldName)
			return attr.name;
		else
			return window.Messages('analysis.attribute-name', attr.name, attr.fieldName);
	}

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
			shapefileId: null,
			attributeName: null,
			scenarioId: null,
			status: null,
			totalPoints: null,
			completePoints: null,
			envelope: null
		},

		updateStatus : function() {

		},

		getPoints : function() {

		},

		pointSetName : function () {
			var attrName = A.models.Shapefile.attributeName(this.get('attribute'));
			return window.Messages('analysis.point-set-name', this.get('shapefileName'), attrName);
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
