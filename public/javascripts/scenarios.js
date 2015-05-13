/**
 * Scenario management interface
 */

var Analyst = Analyst || {};

(function(A, $) {

  A.scenarioData = {};

  A.scenarioData.DataLayout = Backbone.Marionette.Layout.extend({

    template: Handlebars.getTemplate('data', 'data-layout'),

    regions: {
      scenarioData: "#main"
    },

    initialize: function(options) {

      var _this = this;

      this.scenarios = new A.models.Scenarios();
      this.scenarios.fetch({data: {projectId: A.app.selectedProject}});

    },

    onShow: function() {
      var _this = this;

      this.scenarioLayout = new A.scenarioData.ScenarioLayout({
        collection: this.scenarios
      });

      this.scenarioData.show(this.scenarioLayout);
      this.scenarioLayout.on('scenarioCreate', function () {
        _this.createScenario();
      })

    },

    cancelNewScenario: function() {

      this.scenarioData.close();

      this.onShow();
    },

    createScenario: function(evt) {

      var _this = this;

      var scenarioCreateLayout = new A.scenarioData.ScenarioCreateView({
        projectId: A.app.selectedProject
      });

      this.listenTo(scenarioCreateLayout, "scenarioCreate:save", function() {
        _this.scenarios.fetch({
          reset: true,
          data: {
            projectId: A.app.selectedProject
          }
        });
        this.onShow();
      });

      this.listenTo(scenarioCreateLayout, "scenarioCreate:cancel", this.cancelNewScenario);

      this.scenarioData.show(scenarioCreateLayout);

    },

  });

  A.scenarioData.ScenarioCreateView = Backbone.Marionette.Layout.extend({

    template: Handlebars.getTemplate('data', 'data-scenario-create-form'),

    ui: {
      name: '#name',
      description: '#description'
    },

    regions: {
      shapefileSelect: "#shapefileSelect"
    },

    events: {
      'click #scenarioSave': 'saveScenarioCreate',
      'click #scenarioCancel': 'cancelScenarioCreate',
      'change #bundleId': 'bundleChange'
    },

    initialize: function(options) {
      this.projectId = options.projectId;
    },

    onShow: function() {

      var _this = this;

      this.bundles = new A.models.Bundles();

      this.bundles.fetch({
        reset: true,
        data: {
          projectId: this.projectId
        },
        success: function(collection, response, options) {

          _this.$("#bundleId").empty();

          _this.bundles.each(function(bundle) {
            $('<option/>')
              .attr('value', bundle.id)
              .text(bundle.get('name'))
              .appendTo(_this.$('#bundleId'));
          });

          _this.bundleChange();

        }
      });

      $('#bannedRoutes').select2();
    },

    bundleChange: function () {
      this.$('#bannedRoutes').empty();

      var _this = this;

      _.each(this.bundles.get(this.$('#bundleId').val()).get('routes'), function (route, i) {
        $('<option/>')
          .text(route.shortName + ' ' + route.longName)
          // use index in routes list as value because the value in the banned routes list is
          // the entire json block
          .attr('value', i)
          .appendTo(_this.$('#bannedRoutes'));
      });
    },

    cancelScenarioCreate: function(evt) {
      this.trigger("scenarioCreate:cancel");
    },

    saveScenarioCreate: function(evt) {

      evt.preventDefault();
      var _this = this;

      if (event) {
        event.preventDefault();
      }

      var scenario = new A.models.Scenario();

      scenario.set({
        projectId: this.projectId,
        bundleId: this.$('#bundleId').val(),
        name: this.$('#name').val(),
        description: this.$('#description').val()
      });

      // figure out the banned routes
      // route indices are the form values
      var bannedRoutes = [];
      var routes = this.bundles.get(scenario.get('bundleId')).get('routes');
      _.each(this.$('#bannedRoutes').val(), function (routeIdx) {
        bannedRoutes.push(routes[Number(routeIdx)]);
      });

      scenario.set('bannedRoutes', bannedRoutes);

      scenario.save().done(function () {
        _this.trigger("scenarioCreate:save");
      });
    }
  });

  A.scenarioData.ScenarioLayout = Marionette.Layout.extend({

    template: Handlebars.getTemplate('data', 'data-scenario-layout'),

    events: {
      'click #createScenario': 'createScenario'
    },

    regions: {
      main: "#main"
    },

    onShow: function() {

      var scenarioListLayout = new A.scenarioData.ScenarioListView({
        collection: this.collection
      });

      this.main.show(scenarioListLayout);

    },

    createScenario: function(evt) {
      this.trigger("scenarioCreate");
    }

  });

  A.scenarioData.ScenarioListItem = Backbone.Marionette.ItemView.extend({

    template: Handlebars.getTemplate('data', 'data-scenario-list-item'),

    events: {
      'click #deleteScenario': 'deleteScenario'
    },

    deleteScenario: function(evt) {
      this.model.destroy();
    },

    templateHelpers: {
      built: function() {
        if (this.model.get('status') === "BUILT")
          return true;
        else
          return false;
      }
    },

    onRender: function() {

      var _this = this;

      this.$el.find("#scenarioName").editable({
        type: 'text',
        name: "name",
        mode: "inline",
        value: this.model.get("name"),
        pk: this.model.get('id'),
        url: '',
        success: function(response, newValue) {
          _this.model.set("name", newValue);
          _this.model.save("name", newValue);
        }
      }).on("hidden", function(e, reason) {
        _this.render();
      });

      this.$el.find("#scenarioDescription").editable({
        type: 'textarea',
        name: "description",
        mode: "inline",
        value: this.model.get("description"),
        pk: this.model.get('id'),
        url: '',
        success: function(response, newValue) {
          _this.model.set("description", newValue);
          _this.model.save("description", newValue);
        }
      }).on("hidden", function(e, reason) {
        _this.render();
      });

    }

  });

  A.scenarioData.ScenarioEmptyList = Backbone.Marionette.ItemView.extend({

    template: Handlebars.getTemplate('data', 'data-scenario-empty-list')
  });

  A.scenarioData.ScenarioListView = Backbone.Marionette.CompositeView.extend({

    template: Handlebars.getTemplate('data', 'data-scenario-list'),
    itemView: A.scenarioData.ScenarioListItem,
    emptyView: A.scenarioData.ScenarioEmptyList,

    appendHtml: function(collectionView, itemView) {
      collectionView.$("#scenarioList").append(itemView.el);

    }

  });

})(Analyst, jQuery);
