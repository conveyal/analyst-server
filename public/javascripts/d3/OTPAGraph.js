/*
  D3.js OTPAGraphs
*/

d3.otpaGraph = function module() {
  "use strict";

  // Public variables width default settings
  var type = 'line',
      color = d3.scale.category10(),
      width = 275,
      graph;


  // Private variables

  function otpaGraph(selection) {
    if (type == 'line') {
      graph = d3.otpaGraphLine();
    } else if (type == 'bar') {
      graph = d3.otpaGraphBar();
    } else if (type == 'circle') {
      graph = d3.otpaGraphCircle();
    } else if (type == 'table') {
      graph = d3.otpaGraphTable();
    } else if (type == 'density') {
      graph = d3.otpaGraphDensity();
    }
    graph = graph.width(width);
    graph.color(color);
    selection.call(graph);
  }

  // Getter/setter functions
  otpaGraph.type = function(_) {
    if (!arguments.length) return type;
    type = _;
    return otpaGraph;
  };

  otpaGraph.width = function(_) {
    if (!arguments.length) return width;
    width = _;
    return otpaGraph;
  };

  otpaGraph.color = function(_) {
    if (!arguments.length) return color;
  
    var colors  = _;
    color = function(i) {
      return colors[i];
    };

    if(graph)
      graph.color(color);

    return otpaGraph;
  };

  // returns the function otpGraph above as a result. then it must be called to actually generate SVG.
  // This graph function chains through to the specific graph, calling it with the selection
  // as a parameter.
  return otpaGraph; 

};
