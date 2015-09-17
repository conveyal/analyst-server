var Analyst = Analyst || {};

(function(A, $) {

  A.analysis.AnalysisMultiPointLayout = Backbone.Marionette.Layout.extend({

    template: Handlebars.getTemplate('analysis', 'analysis-multi-point'),

    events: {
      'click #createQuery': 'createQuery',
      'click #cancelQuery': 'cancelQuery',
      'click #newQuery': 'newQuery',
      'change #origin-shapefile': 'updateQuota'
    },

    regions: {
      main: "#main",
      quotaWarning: "#quotaWarning"
    },

    initialize: function(options) {
      _.bindAll(this, 'createQuery', 'cancelQuery');
    },

    onRender: function() {
      $('#scenario2-controls').hide();
    },

    onShow: function() {

      var _this = this;

      this.$('#date').datetimepicker({pickTime: false});
      this.$('#fromTime').datetimepicker({pickDate: false});
      this.$('#toTime').datetimepicker({pickDate: false});

      // use a bare model to pass information about quota consumption estimates in the create query dialog
      this.queryCreateQuotaUsage = new Backbone.Model();

      this.queryCreateQuotaUsage.listenTo(A.app.user, 'change', function () {
        _this.queryCreateQuotaUsage.set('remainingQuota', A.app.user.get('quota') - A.app.user.get('remainingQuota'));
      });

      this.quotaWarning.show(new A.analysis.QuotaWarning({model: this.queryCreateQuotaUsage}));

      // pick a reasonable default date
      $.get('api/project/' + A.app.selectedProject + '/exemplarDay')
      .done(function (data) {
        var $d = _this.$('#date');

        var sp = data.split('-');
        // months are off by one in javascript
        var date = new Date(sp[0], sp[1] - 1, sp[2]);

        _this.$('#date').data('DateTimePicker').setDate(date);
      });

      // set default times
      this.$('#fromTime').data('DateTimePicker').setDate(new Date(2014, 11, 15, 7, 0, 0));
      this.$('#toTime')  .data('DateTimePicker').setDate(new Date(2014, 11, 15, 9, 0, 0));

      this.walkSpeedSlider = this.$('#walkSpeedSlider').slider({
					formater: function(value) {
						_this.$('#walkSpeedValue').html(window.Messages("analysis.average-walk-speed", value));
						return window.Messages("analysis.km-per-hour", value)
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.walkTimeSlider = this.$('#walkTimeSlider').slider({
					formater: function(value) {
						_this.$('#walkTimeValue').html(window.Messages("analysis.walk-time", value));
						return window.Messages("analysis.n-minutes", value);
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.bikeSpeedSlider = this.$('#bikeSpeedSlider').slider({
					formater: function(value) {
						_this.$('#bikeSpeedValue').html(window.Messages("analysis.average-bike-speed", value));
						return window.Messages("analysis.km-per-hour", value)
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

			this.bikeTimeSlider = this.$('#bikeTimeSlider').slider({
					formater: function(value) {
						_this.$('#bikeTimeValue').html(window.Messages("analysis.bike-time", value));
						return window.Messages("analysis.n-minutes", value);
					}
				}).on('slideStop', function(value) {

				_this.updateResults();
			}).data('slider');

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
        }
      });

      this.shapefiles.fetch({data: {projectId: A.app.selectedProject}})
        .done(function () {
          _this.shapefiles.each(function (shp) {
            $('<option>')
              .attr('value', shp.id)
              .text(shp.get('name'))
              .appendTo(this.$('#origin-shapefile'));

              $('<option>')
                .attr('value', shp.id)
                .text(shp.get('name'))
                .appendTo(this.$('#destination-shapefile'));

          _this.updateQuota();
        });
      });

      // pick a reasonable default date
      $.get('api/project/' + A.app.selectedProject + '/exemplarDay')
        .done(function (data) {
          var $d = _this.$('#date');

          // if the user has edited the date already don't overwrite
          if ($d.val() === '') {
            // data is the plain-text date
            $d.val(data);
          }
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

    /** Update the quota display */
    updateQuota: function () {
      this.queryCreateQuotaUsage.set({
        querySize: this.shapefiles.get(this.$('#origin-shapefile').val()).get('featureCount'),
        quota: A.app.user.get('quota')
      });
    },

    createQuery: function(evt) {
      var _this = this;

      this.$('#insufficientQuota').hide();
      this.$('#requestFailed').hide();

      var bikeSpeed = (this.bikeSpeedSlider.getValue() * 1000 / 60 / 60 );
      var walkSpeed = (this.walkSpeedSlider.getValue() * 1000 / 60 / 60 );
      var walkTime = this.walkTimeSlider.getValue();
      var bikeTime = this.bikeTimeSlider.getValue();

      var data = {
        name: this.$("#name").val(),
        mode: this.mode,
        originShapefileId: this.$('#origin-shapefile').val(),
        destinationShapefileId: this.$('#destination-shapefile').val(),
        scenarioId: this.$('#scenario1').val(),
        projectId: A.app.selectedProject,
        boardingAssumption: 'RANDOM',
        fromTime: A.util.makeTime(this.$('#fromTime').data('DateTimePicker').getDate()),
        date: this.$('#date').data('DateTimePicker').getDate().format('YYYY-MM-DD'),
        walkSpeed: walkSpeed,
        bikeSpeed: bikeSpeed,
        walkTime: walkTime,
        bikeTime: bikeTime
      };

      // profile routing uses a to time as well
      if (A.util.isTransit(this.mode))
        data.toTime = A.util.makeTime(this.$('#toTime').data('DateTimePicker').getDate());
      else
        data.toTime = -1;

      var query = new A.models.Query();
      query.save(data, {
        success: function() {
          // update quota display
          A.app.user.fetch();
          _this.queries.fetch({
            reset: true,
            data: {
              projectId: A.app.selectedProject
            }
          });
        },
        error: function (model, err) {
          if (err.status == 403 && err.responseText == 'INSUFFICIENT_QUOTA') {
            _this.$('#insufficientQuota').show();
            // refetch user to ensure that quota readout is up to date
            A.app.user.fetch();
          } else {
            _this.$('#requestFailed').show();
          }
        }
      });

      this.$("#createQueryForm").hide();
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
      'click .aggregationCheckbox': 'normalizeBy',
      'click .compareCheckbox': 'compareTo',
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
        if (!_this.model.get('complete')) {
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
      data.starting = this.isStarting();
      data.assemblingResults = this.isAssemblingResults();

      if (data.secondsRemaining != null) {
        var totalMinutes = Math.round(data.secondsRemaining / 60);
        data.minutes = totalMinutes % 60;
        data.hours = (totalMinutes - data.minutes) / 60;

        if (data.minutes < 10)
          data.minutes = '0' + data.minutes;
      }

      return data;

    },

    isStarting: function() {
      return this.model.get("completePoints") === 0;
    },

    /** Is this currently assembling results? */
    isAssemblingResults: function () {
      return !this.model.get('complete') && this.model.get('completePoints') == this.model.get('totalPoints') &&
        this.model.get('totalPoints');
    },

    clickItem: function(evt) {

      this.refreshMap();

    },

    exportShape: function(evt) {

      this.normalizeBy();
      this.compareTo();

      var timeLimit = this.timeSlider.getValue() * 60;

      var url = '/gis/query?queryId=' + this.model.id + '&timeLimit=' + timeLimit + '&attributeName=' + this.$('#shapefileColumn').val();

      if (this.groupById)
        url = url + "&groupBy=" + this.groupById;

      if (this.weightByShapefile)
        url = url + "&weightByShapefile=" + this.weightByShapefile + '&weightByAttribute=' + this.weightByAttribute;

      url += '&which=' + this.$('.whichMulti input:checked').val();

      if (this.compareToId)
        url += '&compareTo=' + this.compareToId;

      window.open(url);

    },

    updateMap: function(evt) {
      this.refreshMap();
    },

    normalizeBy: function(evt) {

      if (this.$(".aggregationCheckbox").prop('checked')) {
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
      if (this.$('.compareCheckbox').is(':checked')) {
        this.compareToId = this.$('#compareTo').val();
        this.$('#compareControls').removeClass('hidden');
      } else {
        this.compareToId = false;
        this.$('#compareControls').addClass('hidden');
      }
    },

    deleteItem: function(evt) {
      this.model.destroy().done(function () {
        // if this query had not yet completed, the origins that had not been computed will be "refunded"
        A.app.user.fetch();
      });
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
          this.model.get('destinationShapefileName'),
          this.groupByName,
          this.weightByName
        );
      } else {
        legendTitle = Messages('analysis.accessibility-to', this.model.get('destinationShapefileName'));
      }

      // add the suffix, e.g. (best case)
      legendTitle += ' ';

      if (this.which == 'POINT_ESTIMATE')
        legendTitle += window.Messages('analysis.point-estimate-suffix');

      else if (this.which == 'WORST_CASE')
        legendTitle += window.Messages('analysis.worst-case-suffix');

      else if (this.which == 'BEST_CASE')
        legendTitle += window.Messages('analysis.best-case-suffix');

      else if (this.which == 'AVERAGE')
          legendTitle += window.Messages('analysis.average-suffix');

      else if (this.which == 'SPREAD')
        legendTitle += window.Messages('analysis.spread-suffix');



      if (target.prop("checked")) {
        if (A.map.hasLayer(this.queryOverlay))
          A.map.removeLayer(this.queryOverlay);

        var timeLimit = this.timeSlider.getValue() * 60;

        var url = 'timeLimit=' + timeLimit + '&attributeName=' + this.$('#shapefileColumn').val();

        if (this.groupById)
          url = url + "&groupBy=" + this.groupById;

        if (this.weightByShapefile)
          url = url + "&weightByShapefile=" + this.weightByShapefile + '&weightByAttribute=' + this.weightByAttribute;

        url += '&which=' + this.which;

        this.$(".legendTitle").text(legendTitle);

        var legendItemTemplate = Handlebars.getTemplate('analysis', 'query-legend-item')

        this.$("#legendData").empty();
        this.$("#updatingMap").show();

        var _map = A.map;

        var bins;;

        if (this.compareToId)
          bins = '/api/query/' + this.model.id + '/' + this.compareToId + '/bins?' + url;
        else
          bins = '/api/query/' + this.model.id + '/bins?' + url;

        $.getJSON(bins, function(data) {

          var tileUrl;
          if (_this.compareToId)
            tileUrl = '/tile/query/' + _this.model.id + '/' + _this.compareToId + '/{z}/{x}/{y}.png?' + url;
          else
            tileUrl = '/tile/query/' + _this.model.id + '/{z}/{x}/{y}.png?' + url;

          _this.queryOverlay = L.tileLayer(tileUrl).addTo(_map);

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
                pctLabel: window.Messages('analysis.bin-percent-single', lowerPct, _this.model.get('destinationShapefileName'))
              }
            } else {
              legendItem = {
                color: data[i].hexColor,
                label: window.Messages('analysis.bin-range', lower, upper),
                pctLabel: window.Messages('analysis.bin-percent-range', lowerPct, upperPct, _this.model.get('destinationShapefileName'))
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

      var nameField = "name";

      this.$el.find("#queryName").editable({
        type        : 'text',
        name        : nameField,
        mode				: "inline",
        value       : this.model.get(nameField),
        pk          : this.model.get('id'),
        url         : '',
        success     : function(response, newValue) {
          _this.model.set(nameField, newValue);
          _this.model.save(nameField, newValue);
        }
      }).on("hidden", function(e, reason) {
        _this.render();
      });

      if (this.model.get('complete')) {
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

            // append attribute names for the destination shapefile for this query
            _this.shapefiles.get(_this.model.get('destinationShapefileId')).getNumericAttributes().forEach(function (attr) {
              var atName = A.models.Shapefile.attributeName(attr);

              if(!attr.hide) {
                $('<option>')
                .attr('value', attr.fieldName)
                .text(atName)
                .appendTo(_this.$('#shapefileColumn'));
              }
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

  A.analysis.QuotaWarning = Backbone.Marionette.ItemView.extend({
    template: Handlebars.getTemplate('analysis', 'analysis-multi-point-quota-warning'),

    onShow: function () {
      var _this = this;
      this.listenTo(this.model, 'change', function () {
        _this.render();
      });
    },

    serializeData: function () {
      var data = this.model.toJSON();
      if (this.model.get('querySize') > this.model.get('quota'))
        data.showError = true;
      else
        data.showWarning = this.model.get('querySize') / this.model.get('quota') > 0.75;
      return data;
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
