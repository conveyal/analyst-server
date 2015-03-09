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
    return (Messages != undefined && Messages.messages[str] != undefined ? Messages.apply(window, args) : str);
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
