var Analyst = Analyst || {};

(function(A, $) {

A.data = {};

	A.data.DataController = Marionette.Controller.extend({
	        
	    initialize: function(options){
	        
	        this.region = options.region;

	        this.project  = options.model;
	    },
	    
	    show: function(){

	    	var dataListLayout = new A.data.DataListLayout({projects: this.projects});
	    	this.region.show(dataListLayout);
	        
	    }
	});

	A.data.DataListLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('data', 'data-list-layout'),


	});


})(Analyst, jQuery);	

