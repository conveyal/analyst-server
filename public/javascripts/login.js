var Analyst = Analyst || {};

(function(A, $) {

	A.login = {};

	A.login.instance = new Backbone.Marionette.Application();

	A.login.instance.addRegions({
		appRegion: "#app"
	});


	A.login.instance.addInitializer(function(options){

		A.login.instance.appRegion.show(new A.login.Login());

	});

	A.login.Login = Marionette.Layout.extend({
		template: Handlebars.getTemplate('auth', 'auth-login'),

		events : {
			"click .login" : "doLogin"
		},

		initialize : function () {
			_.bindAll(this, "doLogin")
		},

		doLogin : function() {

			$.post('/doLogin', {username: this.$('#username').val(), password: this.$('#password').val()}, function() {
				alert("success");
			}).fail(function() {
				alert('failure');
			});

		}
 	});


})(Analyst, jQuery);


$(document).ready(function() {

	Analyst.login.instance.start();

});
