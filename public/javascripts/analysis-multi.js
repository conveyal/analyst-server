var Analyst = Analyst || {};

(function(A, $) {

  A.analysis.AnalysisMultiPointLayout = Backbone.Marionette.Layout.extend({

    template: Handlebars.getTemplate('analysis', 'analysis-multi-point'),

    events: {
      'click #createQuery': 'createQuery',
      'click #cancelQuery': 'cancelQuery',
      'click #newQuery': 'newQuery',
      'change #shapefile': 'updateAttributes'
    },

    regions: {
      main: "#main"
    },

    initialize: function(options) {
      _.bindAll(this, 'createQuery', 'cancelQuery', 'updateAttributes');
    },

    onRender: function() {
      $('#scenario2-controls').hide();
    },

    onShow: function() {

      var _this = this;

      this.scenarios = new A.models.Scenarios();
      this.queries = new A.models.Queries();
      this.shapefiles = new A.models.Shapefiles();

      this.scenarios.fetch({
        reset: true,
        data: {
          projectId: A.app.selectedProject
        },
        success: function(collection, response, options) {

          _this.$(".scenario-list").empty();

          for (var i in _this.scenarios.models) {
            if (_this.scenarios.models[i].get("id") == "default")
              _this.$(".scenario-list").append('<option selected value="' + _this.scenarios.models[i].get(
                "id") + '">' + _this.scenarios.models[i].get("name") + '</option>');
            else
              _this.$(".scenario-list").append('<option value="' + _this.scenarios.models[i].get("id") +
                '">' + _this.scenarios.models[i].get("name") + '</option>');

          }

        }
      });

      this.queries.fetch({
        reset: true,
        data: {
          projectId: A.app.selectedProject
        },
        success: function(collection, response, options) {

        }
      });

      this.shapefiles.fetch({data: {projectId: A.app.selectedProject}})
        .done(function () {
          _this.shapefiles.each(function (shp) {
            $('<option>')
              .attr('value', shp.id)
              .text(shp.get('name'))
              .appendTo(this.$('#shapefile'));
          });

          _this.updateAttributes();
        });

      this.mode = "WALK,TRANSIT";

      this.$('input[name=mode1]:radio').on('change', function(event) {
        _this.mode = _this.$('input:radio[name=mode1]:checked').val();
        // profile routing needs a toTime
        if (A.util.isTransit(_this.mode))
          _this.$('#toTimeControls').removeClass('hidden');
        else
          _this.$('#toTimeControls').addClass('hidden');
      });

      this.$("#createQueryForm").hide();

      var queryListLayout = new A.analysis.QueryList({
        collection: this.queries
      });

      this.main.show(queryListLayout);
    },

    /**
     * Update the attributes select to show the attributes of the current shapefile
     */
    updateAttributes: function () {
      var shpId = this.$('#shapefile').val();
      var shp = this.shapefiles.get(shpId);
      var _this = this;

      this.$('#shapefileColumn').empty();

      shp.getNumericAttributes().forEach(function (attr) {
        var atName = A.models.Shapefile.attributeName(attr);

        $('<option>')
          .attr('value', attr.fieldName)
          .text(atName)
          .appendTo(_this.$('#shapefileColumn'));
      });
    },

    createQuery: function(evt) {

      var _this = this;

      var data = {
        name: this.$("#name").val(),
        mode: this.mode,
        shapefileId: this.$('#shapefile').val(),
        attributeName: this.$('#shapefileColumn').val(),
        scenarioId: this.$('#scenario1').val(),
        projectId: A.app.selectedProject,
        fromTime: this.makeTime(this.$('#fromTime').val()),
        date: this.$('#date').val()
      };

      // profile routing uses a to time as well
      if (A.util.isTransit(this.mode))
        data.toTime = this.makeTime(this.$('#toTime').val());
      else
        data.toTime = -1;

      var query = new A.models.Query();
      query.save(data, {
        success: function() {
          _this.queries.fetch({
            reset: true,
            data: {
              projectId: A.app.selectedProject
            },
            success: function(collection, response, options) {

            }
          });
        }
      });

      this.$("#createQueryForm").hide();
    },

    /** Turn a time like 14:00:00 into seconds since midnight */
    makeTime: function (t) {
      var ts = t.split(':');

      var time = ts[0] * 3600 + ts[1] * 60;

      if (ts.length == 3)
        time += ts[2];

      return time;
    },

    cancelQuery: function(evt) {
      this.$("#createQueryForm").hide();
    },

    newQuery: function(evt) {
      this.$("#createQueryForm").show();
    }

  });

  A.analysis.QueryListItem = Backbone.Marionette.ItemView.extend({
    tagName: 'li',
    className: 'list-group-item',

    template: Handlebars.getTemplate('analysis', 'query-list-item'),

    events: {

      'click #deleteItem': 'deleteItem',
      'click #queryCheckbox': 'clickItem',
      'click #normalizeCheckbox': 'normalizeBy',
      'click #compareCheckbox': 'compareTo',
      'click #exportShape': 'exportShape',
      'click #updateMap': 'updateMap',
      'change #weightByShapefile': 'updateAttributes'
    },

    modelEvents: {
      'change': 'fieldsChanged'
    },

    initialize: function() {
      var _this = this;
      this.updateInterval = setInterval(function() {
        if (!_this.isComplete()) {
          _this.model.fetch();
        } else {
          // don't keep polling
          clearInterval(_this.updateInterval);
        }
      }, 1000);
    },

    onClose: function() {
      clearInterval(this.updateInterval);

      if (this.queryOverlay && A.map.hasLayer(this.queryOverlay))
        A.map.removeLayer(this.queryOverlay);

    },

    fieldsChanged: function() {
      this.render();
    },

    serializeData: function() {

      var data = this.model.toJSON();

      if (this.isStarting())
        data['starting'] = true;
      else if (this.isComplete())
        data['complete'] = true;

      data['pointSetName'] = this.model.pointSetName();

      return data;

    },

    isStarting: function() {
      return this.model.get("totalPoints") === null || this.model.get('totalPoints') == -1;
    },

    isComplete: function() {
      var tp = this.model.get("totalPoints");
      var cp = this.model.get("completePoints");
      return tp !== null && cp !== null && tp == cp;
    },

    clickItem: function(evt) {

      this.refreshMap();

    },

    exportShape: function(evt) {

      this.normalizeBy();
      this.compareTo();

      var timeLimit = this.timeSlider.getValue() * 60;

      var url = '/gis/query?queryId=' + this.model.id + '&timeLimit=' + timeLimit;

      if (this.groupById)
        url = url + "&groupBy=" + this.groupById;

      if (this.weightByShapefile)
        url = url + "&weightByShapefile=" + this.weightByShapefile + '&weightByAttribute=' + this.weightByAttribute;

      url += '&which=' + this.which;

      if (this.compareToId)
        url += '&compareTo=' + this.compareToId;

      window.open(url);

    },

    updateMap: function(evt) {
      this.refreshMap();
    },

    normalizeBy: function(evt) {

      if (this.$("#normalizeCheckbox").prop('checked')) {
        this.$("#weightByShapefile").prop("disabled", false);
        this.$("#weightByAttribute").prop("disabled", false);
        this.$("#groupBy").prop("disabled", false);
        this.$('#aggregation-controls').slideDown();

        this.weightByShapefile = this.$("#weightByShapefile").val();
        this.weightByName = this.$('#weightByShapefile :selected').text() + ' ' +
          this.$('#weightByAttribute :selected').text();

        this.weightByAttribute = this.$("#weightByAttribute").val();

        this.groupById = this.$('#groupBy').val();
        this.groupByName = this.$('#groupBy :selected').text();
      } else {
        this.$("#weightByShapefile").prop("disabled", true);
        this.$("#weightByAttribute").prop("disabled", true);

        this.weightByShapefile = false;
        this.weightByName = '';
        this.weightByAttribute = false;

        this.groupById = false;
        this.groupByName = '';
        this.$("#groupBy").prop("disabled", true);

        this.$('#aggregation-controls').slideUp();
      }
    },

    compareTo: function () {
      if (this.$('#compareCheckbox').is(':checked')) {
        this.compareToId = this.$('#compareTo').val();
        this.$('#compareControls').removeClass('hidden');
      } else {
        this.compareToId = false;
        this.$('#compareControls').addClass('hidden');
      }
    },

    deleteItem: function(evt) {
      this.model.destroy();
    },

    refreshMap: function() {
      var target = this.$("#queryCheckbox");
      var _this = this;

      this.normalizeBy();
      this.compareTo();

      this.which = this.$('.whichMulti input:checked').val();

      var legendTitle;
      if (this.groupById) {
        legendTitle = Messages('analysis.aggregated-title',
          this.model.pointSetName(),
          this.groupByName,
          this.weightByName
        );
      } else {
        legendTitle = Messages('analysis.accessibility-to', this.model.pointSetName());
      }

      // add the suffix, e.g. (best case)
      legendTitle += ' ';

      if (this.which == 'POINT_ESTIMATE')
        legendTitle += window.Messages('analysis.point-estimate-suffix');

      else if (this.which == 'WORST_CASE')
        legendTitle += window.Messages('analysis.worst-case-suffix');

      else if (this.which == 'BEST_CASE')
        legendTitle += window.Messages('analysis.best-case-suffix');

      else if (this.which == 'SPREAD')
        legendTitle += window.Messages('analysis.spread-suffix');



      if (target.prop("checked")) {
        if (A.map.hasLayer(this.queryOverlay))
          A.map.removeLayer(this.queryOverlay);

        var timeLimit = this.timeSlider.getValue() * 60;

        var url = 'queryId=' + this.model.id + '&timeLimit=' + timeLimit;

        if (this.groupById)
          url = url + "&groupBy=" + this.groupById;

        if (this.weightByShapefile)
          url = url + "&weightByShapefile=" + this.weightByShapefile + '&weightByAttribute=' + this.weightByAttribute;

        url += '&which=' + this.which;

        if (this.compareToId)
          url = url + '&compareTo=' + this.compareToId;

        this.$(".legendTitle").text(legendTitle);

        var legendItemTemplate = Handlebars.getTemplate('analysis', 'query-legend-item')

        this.$("#legendData").empty();
        this.$("#updatingMap").show();

        var _map = A.map;

        $.getJSON('/api/queryBins?' + url, function(data) {

          _this.queryOverlay = L.tileLayer('/tile/query?z={z}&x={x}&y={y}&' + url).addTo(_map);

          _this.$("#updatingMap").hide();

          for (var i in data) {

            var lower = _this.numberWithCommas(parseFloat(data[i].lower).toFixed(0));
            var upper = _this.numberWithCommas(parseFloat(data[i].upper).toFixed(0));
            var lowerPct = parseFloat(data[i].lowerPercent).toFixed(0);
            var upperPct = parseFloat(data[i].upperPercent).toFixed(0);

            var legendItem;
            // if it contains only a single value, don't show upper and lower
            if (lower == upper) {
              legendItem = {
                color: data[i].hexColor,
                label: window.Messages('analysis.bin-single', lower),
                pctLabel: window.Messages('analysis.bin-percent-single', lowerPct, _this.model.pointSetName())
              }
            } else {
              legendItem = {
                color: data[i].hexColor,
                label: window.Messages('analysis.bin-range', lower, upper),
                pctLabel: window.Messages('analysis.bin-percent-range', lowerPct, upperPct, _this.model.pointSetName())
              };
            }

            _this.$("#legendData").append(legendItemTemplate(legendItem));

          }
        });

        this.$("#legend").show();

      } else {
        if (A.map.hasLayer(this.queryOverlay))
          A.map.removeLayer(this.queryOverlay);

        this.$("#legend").hide();
      }
    },

    /** Update available shapefile attributes for weighting */
    updateAttributes: function () {
      this.$('#weightByAttribute').empty();

      var _this = this;

      var shp = this.shapefiles.get(this.$('#weightByShapefile').val());

      shp.getNumericAttributes().forEach(function (attr) {
        var atName = A.models.Shapefile.attributeName(attr);

        $('<option>')
          .attr('value', attr.fieldName)
          .text(atName)
          .appendTo(_this.$('#weightByAttribute'));
      });

    },

    onRender: function() {

      var _this = this;

      if (this.isComplete()) {
        if (this.model.get('transit') && false) {
          // we have transit modes, so it's a profile request
          this.$('.whichMulti input[value="POINT_ESTIMATE"]').parent().remove();
          this.$('.whichMulti input[value="SPREAD"]').parent().remove();
          this.$('.whichMulti input[value="WORST_CASE"]').prop('checked', true).parent().addClass('active');
        } else {
          // it's a stock/vanilla request
          this.$('.whichMulti input[value="WORST_CASE"]').parent().remove();
          this.$('.whichMulti input[value="BEST_CASE"]').parent().remove();
          this.$('.whichMulti input[value="SPREAD"]').parent().remove();
          this.$('.whichMulti input[value="POINT_ESTIMATE"]').prop('checked', true).parent().addClass('active');
        }


        // Set up weight and group by select boxes
        // we weight and group by shapefiles. for weighting we also specify an attribute.
        this.shapefiles = new A.models.Shapefiles();
        this.shapefiles.fetch({
            data: {
              projectId: this.model.get("projectId")
            }
          })
          .done(function() {
            _this.$('#groupBy').empty();

            _this.shapefiles.each(function(shapefile) {
              $('<option>')
                .attr('value', shapefile.id)
                .text(shapefile.get('name'))
                .appendTo(_this.$('#weightByShapefile'));

                $('<option>')
                .attr('value', shapefile.id)
                .text(shapefile.get('name'))
                .appendTo(_this.$('#groupBy'));
            });

            _this.updateAttributes();
          });

        // set up the comparison select boxes
        this.comparisonQueries = new A.models.Queries();
        this.comparisonQueries.fetch({
          data: {
            projectId: A.app.selectedProject
          }
        }).done(function () {
          _this.comparisonQueries.each(function (query) {
            $('<option>')
            .attr('value', query.id)
            .text(query.get('name'))
            .appendTo(_this.$('#compareTo'));
          });
        });

        this.$("#weightBy").prop("disabled", true);
        this.$("#groupBy").prop("disabled", true);

        this.$("#settings").show();
      } else
        this.$("#settings").hide();

      this.timeSlider = this.$('#timeSlider').slider({
        formater: function(value) {
          return value + " minutes";
        }
      }).on('slideStop', function(evt) {
        _this.$('#timeLimitValue').html(evt.value + " mins");
      }).data('slider');

    },
    numberWithCommas: function(x) {
      var parts = x.toString().split(".");
      parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
      return parts.join(".");
    }

  });

  A.analysis.QueryList = Backbone.Marionette.CompositeView.extend({

    template: Handlebars.getTemplate('analysis', 'query-list'),
    itemView: A.analysis.QueryListItem,

    initialize: function() {
      this.queryOverlay = {};

    },

    modelEvents: {
      'change': 'render'
    },

    onShow: function() {

    },

    appendHtml: function(collectionView, itemView) {
      collectionView.$("#queryList").append(itemView.el);
    }

  });

})(Analyst, jQuery);
