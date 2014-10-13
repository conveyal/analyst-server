var Analyst = Analyst || {};

(function(A, $) {

	A.login = {};

	A.login.instance = new Backbone.Marionette.Application();

	A.login.instance.addRegions({
		appRegion: "#app"
	});


	A.login.instance.addInitializer(function(options){


	});


	A.app.Main = Marionette.Layout.extend({

		template: Handlebars.getTemplate('auth', 'auth-login')
	});


})(Analyst, jQuery);


$(document).ready(function() {

	Analyst.login.instance.start();

});
