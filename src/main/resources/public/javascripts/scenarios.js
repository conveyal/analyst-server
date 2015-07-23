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
      });

      this.scenarios.on('scenarioEdit', function (model) {
        _this.editScenario(model);
      });

    },

    cancelNewScenario: function() {

      this.scenarioData.close();

      this.onShow();
    },

    createScenario: function(evt) {

      var _this = this;

      var scenarioEditLayout = new A.scenarioData.ScenarioEditLayout({
        projectId: A.app.selectedProject,
        model: new A.models.Scenario()
      });

      this.listenTo(scenarioEditLayout, "scenarioEdit:save", function() {
        _this.scenarios.fetch({
          reset: true,
          data: {
            projectId: A.app.selectedProject
          }
        });
        this.onShow();
      });

      this.listenTo(scenarioEditLayout, "scenarioEdit:cancel", this.cancelNewScenario);

      this.scenarioData.show(scenarioEditLayout);

    },

    editScenario: function (model) {
      var _this = this;

      var scenarioEditLayout = new A.scenarioData.ScenarioEditLayout({
        projectId: A.app.selectedProject,
        model: model
      });

      this.listenTo(scenarioEditLayout, "scenarioEdit:save", function() {
        _this.scenarios.fetch({
          reset: true,
          data: {
            projectId: A.app.selectedProject
          }
        });
        this.onShow();
      });

      this.listenTo(scenarioEditLayout, "scenarioEdit:cancel", this.cancelNewScenario);

      this.scenarioData.show(scenarioEditLayout);
    }
  });

  A.scenarioData.ScenarioEditLayout = Backbone.Marionette.Layout.extend({

    template: Handlebars.getTemplate('data', 'data-scenario-create-form'),

    ui: {
      name: '#name',
      description: '#description'
    },

    regions: {
      shapefileSelect: "#shapefileSelect"
    },

    events: {
      'click #scenarioSave': 'saveScenarioEdit',
      'click #scenarioCancel': 'cancelScenarioEdit',
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
            var opt = $('<option/>')
              .attr('value', bundle.id)
              .text(bundle.get('name'));

            if (bundle.id == _this.model.get('bundleId'))
              opt.attr('selected', 'selected');

            opt.appendTo(_this.$('#bundleId'));
          });

          _this.bundleChange();
        }
      });
    },

    bundleChange: function () {
      this.$('#bannedRoutes').empty();

      var _this = this;

      _.each(this.bundles.get(this.$('#bundleId').val()).get('routes'), function (route, i) {
        var opt = $('<option/>')
          .text(route.shortName + ' ' + route.longName)
          // use index in routes list as value because the value in the banned routes list is
          // the entire json block
          .attr('value', i);

          // if some are already selected fill them in
          if (_this.$('#bundleId').val() == _this.model.get('bundleId')) {
            if (_.findWhere(_this.model.get('bannedRoutes'), {agencyId: route.agencyId, id: route.id})) {
              opt.attr('selected', 'selected');
            }
          }

          opt.appendTo(_this.$('#bannedRoutes'));
      });

      _this.$('#bannedRoutes').select2();
    },

    cancelScenarioEdit: function(evt) {
      this.trigger("scenarioEdit:cancel");
    },

    saveScenarioEdit: function(evt) {

      evt.preventDefault();
      var _this = this;
      
      this.model.set({
        projectId: this.projectId,
        bundleId: this.$('#bundleId').val(),
        name: this.$('#name').val(),
        description: this.$('#description').val()
      });

      // figure out the banned routes
      // route indices are the form values
      var bannedRoutes = [];
      var routes = this.bundles.get(this.model.get('bundleId')).get('routes');
      _.each(this.$('#bannedRoutes').val(), function (routeIdx) {
        bannedRoutes.push(routes[Number(routeIdx)]);
      });

      this.model.set('bannedRoutes', bannedRoutes);

      this.model.save().done(function () {
        _this.trigger("scenarioEdit:save");
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
      'click .deleteScenario': 'deleteScenario',
      'click .editScenario': 'editScenario',
      'click .duplicateScenario': 'duplicateScenario'
    },

    deleteScenario: function(evt) {
      this.model.destroy();
    },

    editScenario: function (evt) {
      // kind of ugly to trigger this on the model but there is a wall against bubbling from
      // children to parents in compositeview . . .
      // also context is not preserved so pass the model along.
      this.model.trigger('scenarioEdit', this.model);
    },

    duplicateScenario: function (evt) {
      var newScenario = new A.models.Scenario();
      newScenario.set(_.omit(this.model.toJSON(), 'id'));
      newScenario.set('name', newScenario.get('name') + " (copy)");

      var _this = this;
      newScenario.save().done(function () {
        _this.model.collection.fetch({reset: true, data: {projectId: _this.model.get('projectId')}});
      });
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
