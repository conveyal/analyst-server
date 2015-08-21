var Analyst = Analyst || {};

(function(A, $) {
  A.ledger = {};

  A.ledger.LedgerItemView = Backbone.Marionette.ItemView.extend({
    template: Handlebars.getTemplate('ledger', 'ledger-item'),
    tagName: 'tr',
    serializeData: function () {
      var ret = this.model.toJSON();
      ret.time = ret.time.replace(/^([0-9]{4}-[0-9]{2}-[0-9]{2})T([0-9]{2}:[0-9]{2}).*$/, '$1 $2');
      return ret;
    }
  });

  A.ledger.LedgerView = Backbone.Marionette.CompositeView.extend({
    // this is a full page view
    el: 'body',
    template: Handlebars.getTemplate('ledger', 'ledger'),

    itemView: A.ledger.LedgerItemView,
    itemViewContainer: 'tbody',

    events: {
      'change #group, #year, #month': 'fetch'
    },

    initialize: function () {
      this.collection = new A.models.Ledger();

      _.bindAll(this, 'render');
      this.listenTo(this.collection, 'change sync ', this.render);

      var instance = this;
      this.groups = []

      // find available years
      // range is exclusive at end so add one, and reverse so most recent year is at the top
      // Make them strings so they can be used as hash keys
      this.years = _.map(_.range(2015, new Date().getFullYear() + 1).reverse(), String);

      this.year = new Date().getFullYear();
      this.month = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october',
        'november', 'december'][new Date().getMonth()];

      $.ajax({
        url: '/api/groups',
        success: function (groups) {
          instance.groups = groups;
          instance.group = groups[0];
          // call to render here is a bit frivolous but needed so that the select list will be updated so that
          // the call to fetch gets the correct data.
          instance.render();
          instance.fetch();
        }
      });
    },

    fetch: function () {
      this.month = this.$('#month').val();
      this.year = this.$('#year').val();
      this.group = this.$('#group').val();

      this.collection.fetch({
        data: {
          month: this.month,
          year: this.year,
          group: this.group
        }
      });
    },

    serializeData: function () {
      var single = 0, query = 0, refund = 0, purchase = 0, total = 0, starting, ending;

      if (this.collection.length > 0) {
        starting = this.collection.at(0).get('balance') - this.collection.at(0).get('delta');
        ending = this.collection.at(this.collection.length - 1).get('balance');
      }

      this.collection.each(function (entry) {
        var reason = entry.get('reason');
        var delta = entry.get('delta');

        total += delta;

        if (reason == 'SINGLE_POINT')
          // nb reversing sign so everything is positive
          single -= delta;

        else if (reason == 'QUERY_CREATED')
          query -= delta;

        else if (reason == 'PURCHASE')
          purchase += delta;

        else
          refund += delta;
      });

      return {
        years: this.years,
        single: single,
        query: query,
        refund: refund,
        purchase: purchase,
        total: total,
        starting: starting,
        ending: ending,
        groups: this.groups
      };
    },

    onRender: function () {
      if (this.group && this.year && this.month) {
        // select the correct select boxes
        this.$('option').prop('selected', false);

        this.$('#year option[value="' + this.year + '"]').prop('selected', true);
        this.$('#month option[value="' + this.month + '"]').prop('selected', true);
        this.$('#group option[value="' + this.group + '"]').prop('selected', true);
      }
    }
  });

  // this runs on its own page, so if this script is getting loaded we should be starting up the ledger.
  // so go ahead and spin it up.
  $(document).ready(function () {
    var lv = new A.ledger.LedgerView();
  });
})(Analyst, jQuery);
