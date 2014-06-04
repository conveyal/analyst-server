var Analyst = Analyst || {};

(function(A, $) {

	A.app = new Backbone.Marionette.Application();
 
	A.app.addRegions({
		appRegion: "#app"
	});

	A.app.addInitializer(function(options){

		var projectController = new A.project.ProjectController({region : A.app.appRegion })

		projectController.show();

	});


})(Analyst, jQuery);


$(document).ready(function() {
  
	Analyst.map = L.map('map', { zoomControl:false }).setView([0, -80.00], 4);
	
	L.tileLayer('http://{s}.tiles.mapbox.com/v3/conveyal.hml987j0/{z}/{x}/{y}.png', {
	    attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://mapbox.com">Mapbox</a>',
	    maxZoom: 18
	}).addTo(Analyst.map);

	
	Analyst.app.start();

});

