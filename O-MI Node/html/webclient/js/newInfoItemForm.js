// Generated by CoffeeScript 1.9.3
(function() {
  (function(consts) {
    var cloneAbove;
    cloneAbove = function() {
      var model, target;
      target = $(this).prev();
      model = target.clone();
      model.find("input").val("");
      return target.after(model);
    };
    return consts.afterJquery(function() {
      return $('.btn-clone-above').on('click', cloneAbove);
    });
  })(WebOmi.consts);

}).call(this);