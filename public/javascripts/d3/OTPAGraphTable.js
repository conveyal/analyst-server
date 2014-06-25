/*
  D3.js OTPAGraphs
*/

d3.otpaGraphTable = function module() {
  "use strict";

  // Public variables width default settings
  var width = 300;

  // Private variables
  var height = width,
      color = d3.scale.category10(),
      formatPercent = d3.format(".0%");

  function countFromSeconds(seconds, indicator) {
    if (seconds < indicator.breaks[0]) {
      return 0;
    }
    for (var i = 0; i < indicator.breaks.length - 1; i++) {
      var low = indicator.breaks[i];
      var high = indicator.breaks[i + 1];
      if (seconds < high) {
        // x is in this slice (x >= low because the breaks are sorted)
        var fraction = (seconds  - low) / (high - low);
        var n_slices = indicator.breaks.length - 1;
        return ((i + fraction) / n_slices) * indicator.count;
      }
    }
    return indicator.count;
  }

  function otpaGraphTable(selection) {

    // Graph - enter
    selection.enter()
      .append('div')
        .attr('class', 'otpa-graph')
      .append('table')
        .attr('class', 'otpa-graph-table');

    // Graph - exit
    selection.exit().remove();

    // Graph - update
    var rows = selection.select('table').selectAll('tr')
        .data(function(d) {
          var indicators = d.attributes;
          var data = Object.keys(indicators).map(function(indicator) {
            return {name: indicator, value: countFromSeconds(d.seconds, indicators[indicator]), total: indicators[indicator].count};
          });
          return data;
        });

    // Row - enter
    rows.enter()
      .append('tr')
        .each(function(d, i) {

          d3.select(this).append('td')
              .attr('class', 'otpa-graph-table-row-color')
              .style('background-color', color(i));

          d3.select(this).append('td')
              .html(d.name);

          d3.select(this).append('td')
              .attr('class', 'otpa-graph-table-row-value');

          d3.select(this).append('td')
              .attr('class', 'otpa-graph-table-row-percentage');

        });

    // Row - exit
    rows.exit().remove();

    // Row - update
    selection.selectAll('tr').each(function(d) {
      d3.select(this).select('.otpa-graph-table-row-value')
        .html(d.value ? Math.round(d.value) : 0);
      d3.select(this).select('.otpa-graph-table-row-percentage')
        .html('(' + formatPercent((d.value ? d.value : 0) / d.total) + ')');
    });

  }

  // Getter/setter functions
  otpaGraphTable.width = function(_) {
    if (!arguments.length) return width;
    width = _;
    return otpaGraphTable;
  };

   // Getter/setter functions
  otpaGraphTable.color = function(_) {
    if (!arguments.length) return color;
    color = _;
    return otpaGraphTable;
  };


  return otpaGraphTable;

};