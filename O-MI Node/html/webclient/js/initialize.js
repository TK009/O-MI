// Generated by CoffeeScript 1.9.3
(function() {
  var constsExt,
    slice = [].slice;

  constsExt = function($, parent) {
    var afterWaits, my;
    my = parent.consts = {};
    my.codeMirrorSettings = {
      mode: "text/xml",
      lineNumbers: true,
      lineWrapping: true
    };
    afterWaits = [];
    my.afterJquery = function(fn) {
      return afterWaits.push(fn);
    };
    $(function() {
      var basicInput, fn, i, infoItemIcon, language, len, loc, objectIcon, objectsIcon, requestTip, responseCMSettings, results, v, validators;
      responseCMSettings = $.extend({
        readOnly: true
      }, my.codeMirrorSettings);
      my.requestCodeMirror = CodeMirror.fromTextArea($("#requestArea")[0], my.codeMirrorSettings);
      my.responseCodeMirror = CodeMirror.fromTextArea($("#responseArea")[0], responseCMSettings);
      my.responseDiv = $('.response .CodeMirror');
      my.responseDiv.hide();
      my.serverUrl = $('#targetService');
      my.odfTreeDom = $('#nodetree');
      my.requestSelDom = $('.requesttree');
      my.readAllBtn = $('#readall');
      my.sendBtn = $('#send');
      my.resetAllBtn = $('#resetall');
      my.progressBar = $('.response .progress-bar');
      loc = window.location.href;
      my.serverUrl.val(loc.substr(0, loc.indexOf("html/")));
      objectsIcon = "glyphicon glyphicon-tree-deciduous";
      objectIcon = "glyphicon glyphicon-folder-open";
      infoItemIcon = "glyphicon glyphicon-apple";
      my.odfTreeDom.jstree({
        plugins: ["checkbox", "types", "contextmenu"],
        core: {
          error: function(msg) {
            return console.log(msg);
          },
          force_text: true,
          check_callback: true
        },
        types: {
          "default": {
            icon: "odf-objects " + objectsIcon,
            valid_children: ["object"]
          },
          object: {
            icon: "odf-object " + objectIcon,
            valid_children: ["object", "infoitem"]
          },
          objects: {
            icon: "odf-objects " + objectsIcon,
            valid_children: ["object"]
          },
          infoitem: {
            icon: "odf-infoitem " + infoItemIcon,
            valid_children: []
          }
        },
        checkbox: {
          three_state: false,
          keep_selected_style: true,
          cascade: "up+undetermined",
          tie_selection: true
        },
        contextmenu: {
          show_at_node: true,
          items: function(target) {
            return {
              helptxt: {
                label: "For write request:",
                icon: "glyphicon glyphicon-pencil",
                action: function() {
                  return my.ui.request.set("write", false);
                },
                separator_after: true
              },
              add_info: {
                label: "Add an InfoItem",
                icon: infoItemIcon,
                _disabled: my.odfTree.settings.types[target.type].valid_children.indexOf("infoitem") === -1,
                action: function(data) {
                  var idName, name, path, tree;
                  tree = WebOmi.consts.odfTree;
                  parent = tree.get_node(data.reference);
                  name = window.prompt("Enter a name for the new InfoItem:", "MyInfoItem");
                  idName = idesc(name);
                  path = parent.id + "/" + idName;
                  if ($(jqesc(path)).length > 0) {
                    return;
                  }
                  return tree.create_node(parent.id, {
                    id: path,
                    text: name,
                    type: "infoitem"
                  }, "first", function() {
                    tree.open_node(parent, null, 500);
                    return tree.select_node(path);
                  });
                }
              },
              add_obj: {
                label: "Add an Object",
                icon: objectIcon,
                _disabled: my.odfTree.settings.types[target.type].valid_children.indexOf("object") === -1,
                action: function(data) {
                  var idName, name, path, tree;
                  tree = WebOmi.consts.odfTree;
                  parent = tree.get_node(data.reference);
                  name = window.prompt("Enter an identifier for the new Object:", "MyObject");
                  idName = idesc(name);
                  path = parent.id + "/" + idName;
                  if ($(jqesc(path)).length > 0) {
                    return;
                    return;
                  }
                  return tree.create_node(parent, {
                    id: path,
                    text: name,
                    type: "object"
                  }, "first", function() {
                    tree.open_node(parent, null, 500);
                    return tree.select_node(path);
                  });
                }
              }
            };
          }
        }
      });
      my.odfTree = my.odfTreeDom.jstree();
      my.odfTree.set_type('Objects', 'objects');
      my.requestSelDom.jstree({
        core: {
          themes: {
            icons: false
          },
          multiple: false
        }
      });
      my.requestSel = my.requestSelDom.jstree();
      $('[data-toggle="tooltip"]').tooltip({
        container: 'body'
      });
      requestTip = function(selector, text) {
        return my.requestSelDom.find(selector).children("a").tooltip({
          title: text,
          placement: "right",
          container: "body",
          trigger: "hover"
        });
      };
      requestTip("#readReq", "Requests that can be used to get data from server. Use one of the below cases.");
      requestTip("#read", "Single request for latest or old data with various parameters.");
      requestTip("#subscription", "Create a subscription for data with given interval. Returns requestID which can be used to poll or cancel");
      requestTip("#poll", "Request and empty buffered data for callbackless subscription.");
      requestTip("#cancel", "Cancel and remove an active subscription.");
      requestTip("#write", "Write new data to the server. NOTE: Right click the above odf tree to create new elements.");
      validators = {};
      validators.nonEmpty = function(s) {
        if (s !== "") {
          return s;
        } else {
          return null;
        }
      };
      validators.number = function(s) {
        var a;
        if (s == null) {
          return s;
        }
        a = s.replace(',', '.');
        if ($.isNumeric(a)) {
          return parseFloat(a);
        } else {
          return null;
        }
      };
      validators.integer = function(x) {
        if ((x != null) && x % 1 === 0) {
          return x;
        } else {
          return null;
        }
      };
      validators.greaterThan = function(y) {
        return function(x) {
          if ((x != null) && x > y) {
            return x;
          } else {
            return null;
          }
        };
      };
      validators.greaterThanEq = function(y) {
        return function(x) {
          if ((x != null) && x >= y) {
            return x;
          } else {
            return null;
          }
        };
      };
      validators.equals = function(y) {
        return function(x) {
          if ((x != null) && x === y) {
            return x;
          } else {
            return null;
          }
        };
      };
      validators.or = function() {
        var vs;
        vs = 1 <= arguments.length ? slice.call(arguments, 0) : [];
        return function(c) {
          var i, len, res, v;
          if (vs.length === 0) {
            return null;
          }
          for (i = 0, len = vs.length; i < len; i++) {
            v = vs[i];
            res = v(c);
            if (res != null) {
              return res;
            }
          }
          return null;
        };
      };
      validators.url = function(s) {
        if (/^(https?|ftp):\/\/(((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:)*@)?(((\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5]))|((([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))\.)+(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))\.?)(:\d*)?)(\/((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)+(\/(([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)*)*)?)?(\?((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)|[\uE000-\uF8FF]|\/|\?)*)?(\#((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)|\/|\?)*)?$/i.test(s)) {
          return s;
        } else {
          return null;
        }
      };
      v = validators;
      basicInput = function(selector, validator) {
        if (validator == null) {
          validator = validators.nonEmpty;
        }
        return {
          ref: $(selector),
          get: function() {
            return this.ref.val();
          },
          set: function(val) {
            return this.ref.val(val);
          },
          validate: function() {
            var val, validatedVal, validationContainer;
            val = this.get();
            validationContainer = this.ref.closest(".form-group");
            validatedVal = validator(val);
            if (validatedVal != null) {
              validationContainer.removeClass("has-error").addClass("has-success");
            } else {
              validationContainer.removeClass("has-success").addClass("has-error");
            }
            return validatedVal;
          },
          bindTo: function(callback) {
            return this.ref.on("input", (function(_this) {
              return function() {
                return callback(_this.validate());
              };
            })(this));
          }
        };
      };
      my.ui = {
        request: {
          ref: my.requestSelDom,
          set: function(reqName, preventEvent) {
            var tree;
            if (preventEvent == null) {
              preventEvent = true;
            }
            tree = this.ref.jstree();
            if (!tree.is_selected(reqName)) {
              tree.deselect_all();
              return tree.select_node(reqName, preventEvent, false);
            }
          },
          get: function() {
            return this.ref.jstree().get_selected[0];
          }
        },
        ttl: basicInput('#ttl', function(a) {
          console.log(typeof v.greaterThanEq);
          return (v.or(v.greaterThanEq(0), v.equals(-1)))(v.number(v.nonEmpty(a)));
        }),
        callback: basicInput('#callback', v.url),
        requestID: basicInput('#requestID', function(a) {
          return v.integer(v.number(v.nonEmpty(a)));
        }),
        odf: {
          ref: my.odfTreeDom,
          get: function() {
            return my.odfTree.get_selected();
          },
          set: function(vals, preventEvent) {
            var i, len, node, results;
            if (preventEvent == null) {
              preventEvent = true;
            }
            my.odfTree.deselect_all(true);
            if ((vals != null) && vals.length > 0) {
              results = [];
              for (i = 0, len = vals.length; i < len; i++) {
                node = vals[i];
                results.push(my.odfTree.select_node(node, preventEvent, false));
              }
              return results;
            }
          }
        },
        interval: basicInput('#interval', function(a) {
          return (v.or(v.greaterThanEq(0), v.equals(-1), v.equals(-2)))(v.number(v.nonEmpty(a)));
        }),
        newest: basicInput('#newest', function(a) {
          console.log(typeof v.greaterThan);
          return (v.greaterThan(0))(v.integer(v.number(v.nonEmpty(a))));
        }),
        oldest: basicInput('#oldest', function(a) {
          console.log(typeof v.greaterThan);
          return (v.greaterThan(0))(v.integer(v.number(v.nonEmpty(a))));
        }),
        begin: $.extend(basicInput('#begin'), {
          set: function(val) {
            return this.ref.data("DateTimePicker").date(val);
          },
          get: function() {
            var mementoTime;
            mementoTime = this.ref.data("DateTimePicker").date();
            if (mementoTime != null) {
              return mementoTime.toISOString();
            } else {
              return null;
            }
          },
          bindTo: function(callback) {
            return this.ref.on("dp.change", (function(_this) {
              return function() {
                return callback(_this.validate());
              };
            })(this));
          }
        }),
        end: $.extend(basicInput('#end'), {
          set: function(val) {
            return this.ref.data("DateTimePicker").date(val);
          },
          get: function() {
            var mementoTime;
            mementoTime = this.ref.data("DateTimePicker").date();
            if (mementoTime != null) {
              return mementoTime.toISOString();
            } else {
              return null;
            }
          },
          bindTo: function(callback) {
            return this.ref.on("dp.change", (function(_this) {
              return function() {
                return callback(_this.validate());
              };
            })(this));
          }
        }),
        requestDoc: {
          ref: my.requestCodeMirror,
          get: function() {
            return WebOmi.formLogic.getRequest();
          },
          set: function(val) {
            return WebOmi.formLogic.setRequest(val);
          }
        }
      };
      language = window.navigator.userLanguage || window.navigator.language;
      if (!moment.localeData(language)) {
        language = "en";
      }
      my.ui.end.ref.datetimepicker({
        locale: language
      });
      my.ui.begin.ref.datetimepicker({
        locale: language
      });
      my.ui.begin.ref.on("dp.change", function(e) {
        return my.ui.end.ref.data("DateTimePicker").minDate(e.date);
      });
      my.ui.end.ref.on("dp.change", function(e) {
        return my.ui.begin.ref.data("DateTimePicker").maxDate(e.date);
      });
      my.afterJquery = function(fn) {
        return fn();
      };
      results = [];
      for (i = 0, len = afterWaits.length; i < len; i++) {
        fn = afterWaits[i];
        results.push(fn());
      }
      return results;
    });
    return parent;
  };

  window.WebOmi = constsExt($, window.WebOmi || {});

  window.jqesc = function(mySel) {
    return '#' + mySel.replace(/(:|\.|\[|\]|,|\/)/g, "\\$1").replace(/( )/g, "_");
  };

  window.idesc = function(myId) {
    return myId.replace(/( )/g, "_");
  };

  String.prototype.trim = String.prototype.trim || function() {
    return String(this).replace(/^\s+|\s+$/g, '');
  };

}).call(this);
