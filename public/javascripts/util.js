var Analyst = Analyst || {};

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
