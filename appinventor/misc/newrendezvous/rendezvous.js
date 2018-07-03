var http = require('http');
var exec = require('child_process').exec;
// var sys = require('sys');
var path = require('path');
var requestHandler = http.IncomingMessage.prototype;
var memcache = require('memcache');
var mc = new memcache.Client();
mc.connect();
var querystring = require('querystring');
var fs = require('fs')

/**
 * Add a uniform interface for remote address in Node.js
 *
 * From: https://gist.github.com/3rd-Eden/1740741
 * @api private
 */

requestHandler.__defineGetter__('remote', function remote () {
  var connection = this.connection
  , headers = this.headers
  , socket = connection.socket;

  // return early if we are behind a reverse proxy
  if (headers['x-forwarded-for']) {
    return {
      ip: headers['x-forwarded-for']
      , port: headers['x-forwarded-port']
    }
  }

  // regular HTTP servers
  if (connection.remoteAddress) {
    return {
      ip: connection.remoteAddress
      , port: connection.remotePort
    };
  }

  // in node 0.4 the remote address for https servers was in a different
  // location
  if (socket.remoteAddress) {
    return {
      ip: socket.remoteAddress
      , port: socket.remotePort
    };
  }

  // last possible location..
  return {
    ip: this.socket.remoteAddress || '0.0.0.0'
    , port: this.socket.remotePort || 0
  }
});

var server = function(request, response) {
  var data = "";
  if (request.method == 'POST') { // A phone checking in
    request.on('data', function(chunk) {
      data += chunk.toString();
    });
    request.on('end', function() {
      console.log(data);
      data = JSON.parse(data);
//      data = querystring.parse(data);
      var key = data['key'];
      var webrtc = data['webrtc'];
      if (key && webrtc) {
        // In this case we append data to what is in memcache
        console.log('key && webrtc');
        mc.get('rr-' + key, function(err, result) {
          console.log(result);
          if (!result) {
            result = "[]";
          }
          result = JSON.parse(result);
          result.push(data);
          result = JSON.stringify(result);
          console.log("after stringify: " + result);
          mc.set('rr-' + key, result, 120);

        });
      } else if (key) {
	var json = JSON.stringify(data);
	mc.set('rr-' + key, json, 120); // Save for two minutes.
      }
      response.writeHead(200, "OK", { "Content-Type" : "text/plain",
				      "Access-Control-Allow-Origin" : "*",
				      "Access-Control-Allow-Headers" : "origin, content-type"});
      response.end("OK\n");
    });
  } else if (request.method == 'OPTIONS') {
    response.writeHead(200, "OK", { "Content-Type" : "text/html",
				    "Access-Control-Allow-Origin" : "*",
				    "Access-Control-Allow-Headers" : "origin, content-type"});
    response.end("");
  } else {
    var url = request.url.split('/');
    var key = url[url.length-1];
    if (key && (key == 'test')) {
      response.writeHead(200, "OK", { "Content-Type" : "application/json",
				      "Access-Control-Allow-Origin" : "*",
                                      "Expires" : "Fri, 01 Jan 1990 00:00:00 GMT",
                                      "Cache-Control" : "no-cache, must-revalidate",
				      "Access-Control-Allow-Headers" : "origin, content-type"});
      response.end("Connection OK\n");
    } else if (key) {
      console.log('ding');
      mc.get('rr-' + key, function(err, result) {
	response.writeHead(200, "OK", { "Content-Type" : "application/json",
					"Access-Control-Allow-Origin" : "*",
                                        "Expires" : "Fri, 01 Jan 1990 00:00:00 GMT",
                                        "Cache-Control" : "no-cache, must-revalidate",
					"Access-Control-Allow-Headers" : "origin, content-type"});
	console.log(request.remote);
	response.end(result);
      });
    } else {		// Not found
      response.writeHead(200, "OK", { "Content-Type" : "application/json",
                                      "Cache-Control" : "no-cache, must-revalidate",
                                      "Expires" : "Fri, 01 Jan 1990 00:00:00 GMT",
				      "Access-Control-Allow-Origin" : "*",
				      "Access-Control-Allow-Headers" : "origin, content-type"});
      response.end("");
    }
  }
}

http.createServer(server).listen(3000);
