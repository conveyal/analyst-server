/*
  D3.js OTPAGraphs
*/

// TODO: use namespaced class names!

d3.otpaGraphBar = function module() {
  "use strict";

  // Private variables
  var margin = {top: 20, right: 20, bottom: 30, left: 75},
      width = 500 - margin.left - margin.right,
      height = 350 - margin.top - margin.bottom,
      barGap = 20, // including text
      axisHeight = 50,
      color = d3.scale.category10(),
      format = d3.format('ns');

  var x = d3.scale.ordinal()
        .rangeRoundBands([margin.left, width], 0);

  var y = d3.scale.linear()
      .range([height, margin.top]);

  var xAxis = d3.svg.axis()
      .scale(x)
      .orient("bottom");

  var yAxis = d3.svg.axis()
      .scale(y)
      .orient("left");

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

  function otpaGraphBar(selection) {

    // Graph - enter
    var svg = selection.enter()
      .append('div')
        .attr('class', 'otpa-graph')
      .append('svg')
        .attr('class', 'otpa-graph-bar')
        .attr("width", width + margin.left + margin.right)
        .attr("height", height + margin.top + margin.bottom);

    // Graph - exit
    selection.exit().remove();

    // Graph - update
    var barGroups = selection.select('.otpa-graph-bar').selectAll('.bar-group')
        .data(function(d, i) {
          // Compute scales/domain/etc.
          // TODO: use d3.extent instead?
          var countMax = 0;

          var data = Object.keys(d.attributes).map(function(indicator) {
            countMax = Math.max(countMax, d.attributes[indicator].count);
            return {value: countFromSeconds(d.seconds, d.attributes[indicator]), total: d.attributes[indicator].count};
          });

          y.domain([0, countMax]);
          x.domain(Object.keys(d.attributes))

          return data;
        });

    // Bar - enter
    var barGroup = barGroups.enter().append('g')
        .attr('class', 'bar-group')

    barGroup.append('text')
        .attr('class', 'text')
        .attr('x', function(d, i) { return margin.left + i * x.rangeBand() + x.rangeBand() / 2 - barGap / 2; });

    barGroup.append('rect')
        .attr('class', 'bar-total')
        .attr('fill', function(d, i) { return color(i); })
        .attr('fill-opacity', '0.2')
        .style("stroke", 'none')
        .attr('width', function(d) { return x.rangeBand() - barGap; })
        .attr('x', function(d, i) { return margin.left + i * x.rangeBand(); });

    barGroup.append('rect')
        .attr('class', 'bar')
        .attr('stroke', 'none')
        .style("fill", function(d, i) { return color(i); })
        .attr('width', function(d) { return x.rangeBand() - barGap; })
        .attr('x', function(d, i) { return margin.left + i * x.rangeBand(); });

    // Bar - exit
    barGroups.exit().remove();

    var barCount = barGroup.selectAll('rect').data(function(d) { return [d[1]]; }, function(d) { return d; });

    // Bar - update
    selection.selectAll('.bar-group').each(function(d) {
      d3.select(this).select('.bar')
          .transition()
          .duration(300)
          .attr('y', function(d) { return y(d.value); })
          .attr('height', function(d) { return height - y(d.value); });

      d3.select(this).select('.bar-total')
          .transition()
          .duration(300)
          .attr('y', function(d) { return y(d.total); })
          .attr('height', function(d) { return height - y(d.total); });

      d3.select(this).select('.text')
          .transition()
          .duration(300)
          .attr('y', function(d) { return y(d.value) - 5; })
          .text(function(d) { return format(Math.round(d.value)); });
    });


    // Initialization
    svg.append("g")
        .attr("class", "otpa-graph-axis otpa-graph-line-x-axis")
        .attr("transform", "translate(" + -barGap / 2 + "," + height + ")")
        .call(xAxis);

    svg.append("g")
        .attr("class", "otpa-graph-axis otpa-graph-line-y-axis")
        .attr("transform", "translate(" + (margin.left - (barGap / 2)) + "," + 0 + ")")
        .call(yAxis);


  }

  // Getter/setter functions
  otpaGraphBar.width = function(_) {
    if (!arguments.length) return width;
    width = _;
    return otpaGraphBar;
  };

  // Getter/setter functions
  otpaGraphBar.color = function(_) {
    if (!arguments.length) return color;
    color = _;
    return otpaGraphBar;
  };

  return otpaGraphBar;

};