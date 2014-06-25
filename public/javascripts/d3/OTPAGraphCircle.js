/*
  D3.js OTPAGraphs
*/

// TODO: use namespaced class names!

d3.otpaGraphCircle = function module() {
  "use strict";

  // Public variables width default settings
  var width = 300;

  // Private variables
  var padding = 15,
      radius = width / 2 - padding * 2,
      gap = 8,
      color = d3.scale.category10(),

      proportionalRadius = function(d) {
        var a = Math.PI * (Math.pow(radius, 2) - Math.pow(gap, 2)) / d.data.total;
        return Math.sqrt(d.data.value * a / Math.PI + Math.pow(gap, 2));
      },

      countFromSeconds = function(seconds, indicator) {
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
      },

      pie = d3.layout.pie()
          .sort(null)
          .value(function(d) { return d.total; }),

      piePiece = d3.svg.arc()
          .outerRadius(proportionalRadius)
          .innerRadius(gap),

      circle = d3.svg.arc()
          .outerRadius(radius + 2)
          .startAngle(0)
          .endAngle(2 * Math.PI),

      proportionalCircle = d3.svg.arc()
          .outerRadius(proportionalRadius)
          .innerRadius(proportionalRadius)
          .startAngle(function(d) { return d.startAngle; })
          .endAngle(function(d) { return d.endAngle; });

  function otpaGraphCircle(selection) {
    var height = width;

    // Graph - enter
    selection.enter()
      .append('div')
        .attr('class', 'otpa-graph')
      .append('svg')
        .attr('class', 'otpa-graph-circle')
        .attr("width", width)
        .attr("height", height)
      .append("g")
        .attr("transform", "translate(" + (width / 2) + "," + (height / 2) + ")")
      .append('path')
        .attr('class', 'total')
        .attr("id", "outer-circle")
        .attr('stroke', 'black')
        .attr('stroke-width', '2px')
        .attr('stroke-dasharray', '7, 7')
        .attr('stroke-linecap', 'round')
        .attr('fill', 'none')
        .attr("d", circle);

    // Graph - exit
    selection.exit().remove();

    // Graph - update
    var arc = selection.select('g').selectAll(".arc")
        .data(function(d) {
          var indicators = d.attributes;
          var data = Object.keys(indicators).map(function(indicator) {
            return {
              label: indicator,
              value: countFromSeconds(d.seconds, indicators[indicator]),
              total: indicators[indicator].count
            };
          });
          return pie(data);
        });

    // TODO: this whole thing only works when selection is single element
    // make sure everything works for selections of more elements

    // Arc - enter
    var arcG = arc.enter().append("g")
        .attr('class', 'arc');

    arcG.append("path")
      .attr('class', 'pie-piece')
      .attr('stroke', 'white')
      .attr('stroke-width', '2px')
      .style("fill", function(d, i) { return color(i); });

    arcG.append('path')
        .attr('class', 'total')
        .attr("id", function(d, i) { return "value-circle" + i; })
        .attr('stroke', 'none')
        .attr('fill', 'none');

    arcG.append('text')
        .attr('dy', '1em')
        .style('text-anchor', 'middle')
        .attr('font-size', '13px')
      .append('textPath')
        .attr('class', 'value-text')
        .attr('xlink:href', function(d, i) { return "#value-circle" + i; })
        .attr("startOffset", '25%');

    arcG.append('text')
        .attr('dy', '-.35em')
        .attr('font-size', '13px')
        .style('text-anchor', 'middle')
      .append('textPath')
        .attr('xlink:href', '#outer-circle')
        .attr("startOffset", function(d) {
          var p =  (((d.endAngle + d.startAngle) / 2) / (2 * Math.PI) * 100);
          return ((p + 50) % 100) + '%';
        })
        .text(function(d) {
          var maxLength = Math.round((d.endAngle - d.startAngle) / (Math.PI * 2) * radius);
          if (d.data.label.length > maxLength) {
            return d.data.label.substring(0, maxLength).trim() + '…';
          } else {
            return d.data.label;
          }
        });

    // Arc - exit
    arc.exit().remove();

    // Arc - update
    arc.select('.pie-piece').attr("d", piePiece)
        // .transition()
        // .duration(500)
        // .attrTween("d", function(a) {
        //
        //     // var i = d3.interpolate(this._current, a),
        //      //     k = d3.interpolate(arc.outerRadius()(), newRadius);
        //      // this._current = i(0);
        //      // return function(t) {
        //      //     return arc.innerRadius(k(t)/4).outerRadius(k(t))(i(t));
        //      // };
        //   });

    arc.select('.total').attr("d", proportionalCircle);

    arc.select('.value-text').text(function(d) { return Math.round(d.data.value); });

    // g.append('text')
    //     .attr('dy', '1em')
    //     .style('text-anchor', 'middle')
    //     .attr('font-size', '8px')
    //   .append('textPath')
    //     .attr('xlink:href', function(d, i) { return "#value-circle" + i; })
    //     .attr("startOffset", '25%')
    //     .text(function(d) { return d.data.value; });
    //


    // selection.each(function(d, i) {
    // });

  }

  // Getter/setter functions
  otpaGraphCircle.width = function(_) {
    if (!arguments.length) return width;
    width = _;
    return otpaGraphCircle;
  };

  otpaGraphCircle.color = function(_) {
if (!arguments.length) return color;
color = _;
return otpaGraphCircle;
};

  return otpaGraphCircle;

};