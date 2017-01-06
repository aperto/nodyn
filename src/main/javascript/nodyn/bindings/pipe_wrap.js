/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

"use strict";

var util = require('util');
var Stream = process.binding('stream_wrap').Stream;
var TCP = process.binding( 'tcp_wrap').TCP;

function Pipe(ipc) {
  this._ipc = ipc;
  this._pipe = new Packages.io.nodyn.pipe.PipeWrap( process._process, ipc );
  if ( ipc ) {
    this._pipe.on( 'dataWithHandle', Pipe.prototype._onDataWithHandle.bind(this) );
  }
  Stream.call( this, this._pipe );
}

util.inherits(Pipe, Stream);

Pipe.prototype._onDataWithHandle = function(result) {
  var record = result.result;

  var buffer = record.buffer;
  var fd     = record.fd;

  if ( fd == -1 ) {
    fd = undefined;
  }

  var b;
  var nread = 0;
  var handle;

  if ( buffer ) {
    b = process.binding('buffer').createBuffer( buffer );
    nread = buffer.readableBytes();
  }

  if (fd) {
    var msg = b.toString().trim();
    var json = JSON.parse( msg );
    if ( json.cmd == 'NODE_HANDLE' ) {
      if ( json.type == 'net.Socket' ) {
        handle = new TCP(fd);
      } else if ( json.type == 'net.Native' ) {
        handle = new TCP(fd);
      }
    }
  }

  this.onread( nread, b, handle );
}

Pipe.prototype.closeDownstream = function() {
  this._pipe.closeDownstream();
}

Pipe.prototype._create = function(downstreamFd) {
  this._pipe.create(downstreamFd);
  this._upstream   = this._pipe.upstream
  this._downstream = this._pipe.downstream
}

Pipe.prototype.bind = function() {
  console.log( "Pipe.bind" );
};

Pipe.prototype.listen = function() {
  console.log( "Pipe.listen" );
};

Pipe.prototype.connect = function() {
  console.log( "Pipe.connect" );
};

Pipe.prototype.open = function(fd) {
  this._pipe.open(fd, true, true);
};


Pipe.prototype.writeUtf8String = function(req,data,handle) {
  if ( ! handle ) {
    return Stream.prototype.writeUtf8String.call(this,req,data);
  }

  //System.err.println( "SEND: " + data + " // " + handle._fd );
  this._pipe.writeUtf8String(data, handle._fd);
};

Pipe.prototype.setBlocking = function(isBlocking) {
	// this is called only on windows, see 
	// https://github.com/nodejs/node/commit/20176a98416353d4596900793f739d5ebf4f0ee1
	// related pull-request is https://github.com/nodejs/node/pull/3584
	// changes are supposed to implement "major browser console doc",
	// but there is no mention of blocking I/O in those docs!
	// so just ignore the call ...
	return 0;
};


module.exports.Pipe = Pipe;
