var Analyst = Analyst || {};

(function(A, $) {

A.analysis = {};

	A.analysis.AnalysisController = Marionette.Controller.extend({
	        
	    initialize: function(options){
	        
	        this.region = options.region;

	        this.project  = options.model;
	    },
	    
	    show: function(){

	    	var analysisLayout = new A.analysis.AnalysisLayout({model : this.project});
	    	this.region.show(analysisLayout);
	        
	    }
	});

	A.analysis.AnalysisLayout = Backbone.Marionette.Layout.extend({

		template: Handlebars.getTemplate('analysis', 'analysis-layout'),

		events: {
		  'change .analysis-type': 'selectAnalysisType'
		},

		regions: {
			analysisDetail: '#detail'
		},
		

		onRender : function() {

			if(!this.analysisType || this.analysisType == 'single') {

				var analysisSinglePointLayout = new A.analysis.AnalysisSinglePointLayout({model : this.model});
				
				this.analysisDetail.show(analysisSinglePointLayout);

			}
			else if(this.analysisType == 'multi') {

				var analysisMultiPointLayout = new A.analysis.AnalysisMultiPointLayout({model : this.model});

				this.analysisDetail.show(analysisMultiPointLayout);
			}

		},

		selectAnalysisType : function(evt) {

			this.analysisType = $('.analysis-type').val();

			this.render();

		},

		templateHelpers: {
	     	multiPoint : function () {
	     		if(this.analysisType == "multi") 
	     			return true;
	     		else
	     			return false;
	        },
	        singlePoint : function () {
	     		if(this.analysisType == "single") 
	     			return true;
	     		else
	     			return false;
	        }
      	}

	});

	


})(Analyst, jQuery);	

