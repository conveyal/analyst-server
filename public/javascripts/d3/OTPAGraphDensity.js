/*
  D3.js OTPAGraphs
*/

d3.otpaGraphDensity = function module() {
"use strict";

var margin = {top: 20, right: 40, bottom: 30, left: 50},
    width = 500 - margin.left - margin.right,
    height = 300 - margin.top - margin.bottom,
    color = d3.scale.category10();
//      format = d3.format('ns');

var x = d3.scale.linear()
    .domain([0, 90])
    .range([0, width]);

var y = d3.scale.linear()
    .domain([0, 100])
    .range([height, 0]);

var xAxis = d3.svg.axis()
    .scale(x)
    .orient("bottom");

var yAxis = d3.svg.axis()
    .scale(y)
    .orient("left");

// TODO validate count is positive and breaks are strictly increasing.

// Take a list of sorted time breaks and returns one point per minute 
// with the density. 
function evalDensity(breaks) {
    var nq = breaks.length - 1,
        ds = [];
    for (var x = 0; x < breaks[0]; x++) ds.push( {x:x, d:0} );
    for (var q = 0; q < nq; q++) {
        var b0 = breaks[q],
            b1 = breaks[q + 1],
            range = b1 - b0,
            density = (1.0/nq) / range;
        if (b1 <= b0) {
          console.log("Bad input."); 
          continue;
        };
        for (var x = b0; x < b1; x++) {
            ds.push( {x:x, d:density} );
        }
    }
    // Stacked layout seems to require all layers to have identical lengths and xs
    for (var x = breaks[nq]; x < 100; x++) ds.push( {x:x, d:0} );
    return ds;
}

// Finds the cumulative number of opportunities at t minutes
// using quantiles-based indicator q.
function accumulate(q, t) {
    if (t < q.breaks[0]) return 0;
    var n_slices = q.breaks.length - 1;
    for (var i = 0; i < q.breaks.length - 1; i++) {
        var low  = q.breaks[i],
            high = q.breaks[i+1];
        if (t < high) {
            // x is in this slice (x >= low because the breaks are sorted)
            var fraction = (t - low) / (high - low);
            return Math.round(((i + fraction) / n_slices) * q.count);
        }
    }
    return q.count;
}

// Strictly increasing integer seconds.
function toSeconds(ary) {
  var prev = -1;
  for (var i = 0; i < ary.length; i++) {
    var m = Math.floor(ary[i] / 60);
    if (m <= prev) m = prev + 1;
    ary[i] = m;
    prev = m;
  }
}


// Function called to create a new graph of this kind.
function otpaGraphDensity(selection) {

    // Graph - enter
    var svg = selection.enter()
      .append('div')
        .attr('class', 'otpa-graph')
      .append('svg')
        .attr('class', 'otpa-graph-density')
        .attr("width", width + margin.left + margin.right)
        .attr("height", height + margin.top + margin.bottom)
        .append("g")
        .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

    // Graph - exit
    selection.exit().remove();

    // UGLY HACK
    var attr = selection.data()[0].attributes;
    var quantiles = []
    for (var desc in attr) {
      attr[desc].desc = desc
      attr[desc].id = desc
      toSeconds(attr[desc].breaks);
      quantiles.push(attr[desc])
    }
    quantiles.forEach(function(q) { 
        q.densities = evalDensity(q.breaks); 
        q.densities.forEach(function(d) { d.y = d.d * q.count; }); 
    });

    // Graph - update
    // Bar - exit
    // barGroups.exit().remove();

    // A stack layout transforms the input data set, adding a y0 offset value
    // See https://github.com/mbostock/d3/wiki/Stack-Layout
    var stack = d3.layout.stack() 
      .offset("wiggle") // other options are: silhouette, wiggle, zero, expand
      .values(function(q) { return q.densities; }) // func to extract values from each layer
      .x(function(d) { return d.x; })              // func to extract x values from densities
      .y(function(d) { return d.y; });             // func to extract y values from densities

    // call the stack layout on the data series (array of layers) to create y0 vertical shifts
    stack(quantiles);

    // Find the max absolute y value and set the y domain accordingly
    // De this before adding the axis SVG elements (and before plotting the areas?)
    y.domain([0, d3.max(quantiles, function(q) { 
      return d3.max(q.densities, function(d) { return d.y0 + d.y; }); })]);

    // d3 objects are often also callable functions
    // the area will later be called with stacked data, and will extract x and y values
    var area = d3.svg.area()
      .interpolate("basis")              
      .x (function (d) { return x(d.x); })   // x scale projects x --> SVG coords
      .y0(function (d) { return y(d.y0); })  // y0 is the offset calculated by stack
      .y1(function (d) { return y(d.y0 + d.y); }); // top of the area

    var selection = svg.selectAll(".series")
      .data(quantiles)
      .enter().append("g")
      .attr("class", "series");

    selection.append("path")
      .attr("class", "streamPath")
      .attr("d", function (d) { return area(d.densities); })
      .style("fill", function (d) { 
        return color(d.id); })
      .style("stroke", "black")
      .style("stroke-width", "0.4");

    // Add a rectangle that hides part of the graph, but is under the axes.
    var rectangle = svg
        .append("rect")
        .attr("x", 0)
        .attr("y", 0)
        .attr("width", 1)
        .attr("height", height)
        .style("fill", "white")
        .style("fill-opacity", 0.8);

    var vline = svg
        .append("rect")
        .attr("x", 0)
        .attr("y", 0)
        .attr("width", 1)
        .attr("height", height)
        .style("fill", "none")
        .style("stroke", "black")
        .style("stroke-opacity", 0.5);

    // The labels for each quantile category
    var labelgroup = svg.append("g");

    // Move the rectangle to the given x position and
    // Update the labels accordingly.
    function updateRect(x0) {
        rectangle.attr("x", x0);
        rectangle.attr("width", width - x0);
        vline.attr("x", x0);
        labelgroup.remove();
        labelgroup = svg.append("g");
        var x1 = Math.round(x.invert(x0)) // deproject 
        quantiles.forEach(function(q) {
            var d = q.densities[x1];
            labelgroup.append("text")
              .attr("class", "label")
              .attr("x", 0)
              .attr("y", y(d.y0 + d.y / 2))
              .text(accumulate(q, x1)+" "+q.desc+" ("+Math.round(d.y)+"/min)")
        });
        labelgroup.attr("transform", "translate(" + (x0 + 10) + ")");
    }

    svg.on("mousemove", function(d, i) { 
        updateRect(d3.mouse(this)[0]); 
    });

    // g element is used to group SVG shapes together, not d3 specific
    // In this case, they contain the x and y axis elements
    svg.append("g") 
        .attr("class", "x axis")
        .attr("transform", "translate(0," + height + ")")
        .call(xAxis)
      .append("text")
        .attr("class", "label")
        .attr("x", width)
        .attr("y", -6)
        .style("text-anchor", "end")
        .text("Travel time (min.)");

    // Append the y axis SVG elements (must do after rescaling the y axis to match the data).
    svg.append("g")
        .attr("class", "y axis")
        .call(yAxis)
      .append("text")
        .attr("class", "label")
        .attr("x", margin.left)
        .attr("y", margin.top)
        .style("text-anchor", "beginning")
        .text("Opportunity density (per minute)");

}

// Get or set the graph width
otpaGraphDensity.width = function(_) {
    if (!arguments.length) return width;
    width = _;
    return otpaGraphDensity;
};

// Getter/setter functions
otpaGraphDensity.color = function(_) {
if (!arguments.length) return color;
color = _;
return otpaGraphDensity;
};

return otpaGraphDensity; // Module function return

}; // END module


