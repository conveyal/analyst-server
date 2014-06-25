/*
  D3.js OTPAGraphs
*/

d3.otpaGraphLine = function module() {
  "use strict";

  // variables
  var margin = {top: 0, right: 20, bottom: 30, left: 75},
      width = 400 - margin.left - margin.right,
      height = 300 - margin.top - margin.bottom,
      color = d3.scale.category10(),
      format = d3.format('ns');

  var x = d3.scale.linear()
      .range([margin.left, width]);

  var y = d3.scale.linear()
      .range([height, margin.bottom]);

  var xAxis = d3.svg.axis()
      .scale(x)
      .orient("bottom");

  var yAxis = d3.svg.axis()
      .scale(y)
      .orient("left");

  var line = d3.svg.line()
      .interpolate("simple")
      .x(function(d) { return x(d.seconds / 60);})
      .y(function(d) { return y(d.count); });

  function countsFromIndicator(indicator) {
    return indicator.breaks.map(function(d, i) {
      return {
        seconds: d,
        count: Math.max(1, i / (indicator.breaks.length - 1) * indicator.count)
      };
    });
  }

  function countFromSeconds(seconds, indicator) {
    if (seconds < indicator.breaks[0]) {
      return 0;
    }
    for (var i = 0; i < indicator.breaks.length - 1; i++) {
      var low = indicator.breaks[i];
      var high = indicator.breaks[i + 1];
      if (seconds < high) {
        // x is in this slice (x >= low because the breaks are sorted)
        var fraction = (seconds - low) / (high - low);
        var n_slices = indicator.breaks.length - 1;
        return ((i + fraction) / n_slices) * indicator.count;
      }
    }
    return indicator.count;
  }

  function otpaGraphLine(selection) {
    // Graph - enter
    var svg = selection.enter()
      .append('div')
        .attr('class', 'otpa-graph')
      .append('svg')
        .attr('class', 'otpa-graph-line')
        .attr("width", width + margin.left + margin.right)
        .attr("height", height + margin.top + margin.bottom);

    // Graph - exit
    selection.exit().remove();

    // Graph - update
    var seconds = 0;
    var counts = [];
    var lines = selection.select('.otpa-graph-line').selectAll('.otpa-graph-line-line')
        .data(function(d, i) {
          seconds = d.seconds;

          // Compute scales/domain/etc.
          // TODO: use d3.extent instead?
          var countMax = 0,
              secondsMax = 0;

          var indicators = d.attributes;
          var data = Object.keys(indicators).map(function(indicator) {
            countMax = Math.max(countMax, d.attributes[indicator].count);
            secondsMax = Math.max(secondsMax, Math.max.apply(Math, indicators[indicator].breaks));
            counts.push(countFromSeconds(seconds, indicators[indicator]));
            return countsFromIndicator(indicators[indicator]);
          });
          x.domain([0, secondsMax / 60]);
          y.domain([0, countMax]);

          data.map(function(indicator) {
            var lastCount = indicator[indicator.length - 1].count;
            indicator.push({seconds: secondsMax, count: lastCount});
          });

          return data;
        });

    // Initialization
    svg.append("g")
        .attr("class", "otpa-graph-axis otpa-graph-line-x-axis")
        .attr("transform", "translate(0," + y(0) + ")")
        .call(xAxis);

    svg.append("g")
        .attr("class", "otpa-graph-axis otpa-graph-line-y-axis")
        .attr("transform", "translate(" + x(0) + "," + 0 + ")")
        .call(yAxis);

    // Line - enter
    lines.enter()
      .append("path")
        .attr("class", "otpa-graph-line-line")
        .style("stroke", function(d, i) { return color(i); })
        .attr("d", line);

    // Circles - enter
    svg.append("g").attr("class", "otpa-graph-line-circles")
      .selectAll('circle')
        .data(counts)
        .enter()
      .append('circle')
        .attr('r', 3)
        .attr('stroke', function(d, i) { return color(i); });

    // Circles - update
    selection.selectAll('circle').data(counts)
      .attr('cx', function (d) { return x(seconds / 60); })
      .attr('cy', function(d) { return y(d) });


  }

  // Getter/setter functions
  otpaGraphLine.width = function(_) {
    if (!arguments.length) return width;
    width = _;
    return otpaGraphLine;
  };

     // Getter/setter functions
  otpaGraphLine.color = function(_) {
    if (!arguments.length) return color;
    color = _;
    return otpaGraphLine;
  };

  return otpaGraphLine;
};