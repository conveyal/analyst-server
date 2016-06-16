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

    events: {
      'click #scenarioSave': 'saveScenarioEdit',
      'click #scenarioCancel': 'cancelScenarioEdit'
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
        }
      });
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

      // read the scenario
      var reader = new window.FileReader()
      reader.onloadend = function (e) {
        var val = JSON.parse(e.target.result);
        _this.model.set('modifications', val.modifications);
        _this.model.set('feedChecksums', val.feedChecksums)
        _this.model.save().done(function () {
          _this.trigger("scenarioEdit:save");
        })
      };
      
      reader.readAsText(this.$('#file').get(0).files[0])
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
