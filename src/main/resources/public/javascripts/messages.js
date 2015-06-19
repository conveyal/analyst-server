/** A drop-in replacement for Play JsMessages */

window.Messages = function (code) {
  var msg = window.Messages.messages[code];

  if (msg !== undefined) {
    for (var i = 1; i < arguments.length; i++) {
      msg = msg.replace("{" + (i - 1) + "}", arguments[i]);
    }
  }

  return msg;
}

$.ajax({
  url: "/messages",
  success: function (data) {
    window.Messages.messages = data;
  },
  async: false
});
