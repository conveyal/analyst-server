var Analyst = Analyst || {};

Analyst.util = Analyst.util || {};

/** Is an OTP mode string a transit mode? */
_.extend(Analyst.util, {
  isTransit: function (mode) {
  return mode.indexOf('TRANSIT') !== -1 || mode.indexOf('TRAINISH') !== -1 || mode.indexOf('BUSISH') !== -1 ||
  mode.indexOf('FERRY') !== -1 || mode.indexOf('FUNICULAR') !== -1 || mode.indexOf('GONDOLA') !== -1 ||
  mode.indexOf('CABLE_CAR') !== -1 || mode.indexOf('RAIL') !== -1 || mode.indexOf('SUBWAY') !== -1 ||
  mode.indexOf('TRAM') !== -1 || mode.indexOf('BUS') !== -1;
},

/** Remove transit from a mode */
removeTransit: function (mode) {
  var modes = mode.split(',');
  modes = _.filter(modes, function (mode) {
    return !Analyst.util.isTransit(mode);
  });

  return modes.join(',');
},

/** Turn a date into seconds since noon - 12h */
makeTime: function (d) {
  return d.hours() * 3600 + d.minutes() * 60 + d.seconds();
}
});

Backbone.Marionette.View.prototype.mixinTemplateHelpers = function (target) {
    var self = this;
    var templateHelpers = Marionette.getOption(self, "templateHelpers");
    var result = {};

    target = target || {};

    if (_.isFunction(templateHelpers)){
        templateHelpers = templateHelpers.call(self);
    }

    // This _.each block is what we're adding
    _.each(templateHelpers, function (helper, index) {
        if (_.isFunction(helper)) {
            result[index] = helper.call(self);
        } else {
            result[index] = helper;
        }
    });

    return _.extend(target, result);
};

Handlebars.getTemplate = function(module, name, lang) {
    if (Handlebars.templates === undefined || Handlebars.templates[name] === undefined) {

        var langStr = "";

        if(lang)
             langStr = "." + lang;

        $.ajax({
            url : Analyst.config.templatePath + '/' + module + '/' + name + langStr + '.html',
            success : function(data) {
                if (Handlebars.templates === undefined) {
                    Handlebars.templates = {};
                }
                Handlebars.templates[name] = Handlebars.compile(data);
            },
            error : function() {

                 // fall back to lang-less url reuqest
                 if(lang)
                    Handlebars.getTemplate(module, name, false);
            },
            async : false
        });
    }
    return Handlebars.templates[name];
};

Handlebars.registerHelper('I18n',
  function(str){
    var args = [].slice.call(arguments, 0, -1);
    return (window.Messages !== undefined && window.Messages.messages[str] !== undefined) ? Messages.apply(window, args) : str;
  }
);


Handlebars.registerHelper('formatNumber',
function(num){
  if(num && num > -1000000000 && num < 1000000000)
    return num.toFixed(1);
  else
    return "--"

}
);

Handlebars.registerHelper('calcPercent', function (num, den) {
  return Math.round(num / den * 100);
});

/**
 * Returns info (blue) for percentages 0 - 0.75, warn (yellow) for 0.75 - 0.95, and danger (red) for 0.95 - 1
 * Used to color the quota progress bar.
 */
Handlebars.registerHelper('colorByPercent', function (num, den) {
  var v = num / den;

  if (v <= 0.75)
    return 'info';
  else if (v <= 0.95)
    return 'warning';
  else return 'danger'; // will robinson, danger
});

/**
 * Converts large numbers to human-friendly quantities
 */
Handlebars.registerHelper('humanNumber', function (num) {
  divisors = [
    [1, ''],
    // intentionally skipping thousand as saying 1.8 thousand just looks odd.
    [1e6, ' million'],
    [1e9, ' billion'],
    [1e12, ' trillion'],
    [1e15, ' quadrillion'],
    [1e18, ' quintillion'],
    [1e21, ' sextillion'],
    [1e24, ' septillion'],
    [1e27, ' octillion']
    // that's probably sufficient for pedestrian use
  ]

  var i = 0;

  while (i + 1 < divisors.length && divisors[i + 1][0] < num) i++;

  var val = (num / divisors[i][0])
  var human;

  if (i === 0) {
    human = val.toFixed(0);
    // insert commas as needed
    if (human.length >= 4)
      human = human.slice(0, -3) + ',' + human.slice(-3);
  }
  else human = val.toFixed(1);

  return "" + human + divisors[i][1];
});

/** Create a spinner */
Handlebars.registerHelper('spinner', function () {
  return new Handlebars.SafeString('<i class="fa fa-spinner spinner"></i>');
});
