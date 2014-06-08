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
	      defaultZoom: null
	    }
	  });

	A.models.Projects = Backbone.Collection.extend({
	  type: 'Projects',
	  model: A.models.Project,
	  url: '/api/project'

	});

	A.models.PointSet = Backbone.Model.extend({
		urlRoot: '/api/pointset/',

		defaults: {
			id: null,
			name: null,
			description: null,
			color: null,
			shapefileid: null,
			shapefieldname: null,
			projectid: null
		}	

	});

	A.models.PointSets = Backbone.Collection.extend({
	  type: 'PointSets',
	  model: A.models.PointSet,
	  url: '/api/pointset'

	});

	A.models.Shapefile = Backbone.Model.extend({
		urlRoot: '/api/shapefile/',

		defaults: {
			id: null,
			name: null,
			description: null,
			fieldnames: []
		}	

	});

	A.models.Shapefiles = Backbone.Collection.extend({
	  type: 'Shapefiles',
	  model: A.models.Shapefile,
	  url: '/api/shapefile'

	});

})(Analyst, jQuery);	
