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

})(Analyst, jQuery);	
