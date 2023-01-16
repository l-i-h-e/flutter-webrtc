import 'dart:convert';
import 'dart:html' as html;
import 'dart:js_util' as js_util;
import 'dart:js' as js;
import 'dart:typed_data';
import 'package:js/js.dart';

import 'e2ee.cryptor.dart';

import 'package:flutter_webrtc/src/web/rtc_transform_stream.dart';
import 'package:collection/collection.dart';

@JS()
abstract class TransformMessage {
  external String get msgType;
  external String get kind;
}

@anonymous
@JS()
class EnableTransformMessage {
  external factory EnableTransformMessage({
    ReadableStream readable,
    WritableStream writable,
    String msgType,
    String kind,
    String participantId,
    String trackId,
    String codec,
  });
  external ReadableStream get readable;
  external WritableStream get writable;
  external String get msgType; // 'encode' or 'decode'
  external String get participantId;
  external String get trackId;
  external String get kind;
  external String get codec;
}

@anonymous
@JS()
class RemoveTransformMessage {
  external factory RemoveTransformMessage(
      {String msgType, String participantId, String trackId});
  external String get msgType; // 'removeTransform'
  external String get participantId;
  external String get trackId;
}

@JS('self')
external html.DedicatedWorkerGlobalScope get self;

extension PropsRTCTransformEventHandler on html.DedicatedWorkerGlobalScope {
  set onrtctransform(Function(dynamic) callback) =>
      js_util.setProperty<Function>(this, 'onrtctransform', callback);
}

var participantCryptors = <Cryptor>[];
var publisherKeys = <String, html.CryptoKey>{};
bool isEncryptionEnabled = false;
bool useSharedKey = false;
html.CryptoKey? sharedKey;

void main() async {
  print('E2EE Worker created');

  var sharedKey = Uint8List.fromList([
    200,
    244,
    58,
    72,
    214,
    245,
    86,
    82,
    192,
    127,
    23,
    153,
    167,
    172,
    122,
    234,
    140,
    70,
    175,
    74,
    61,
    11,
    134,
    58,
    185,
    102,
    172,
    17,
    11,
    6,
    119,
    253
  ]);

  print('setup transform event handler');
  if (js_util.getProperty(self, 'RTCTransformEvent') != null) {
    self.onrtctransform = allowInterop((event) {
      print('got transform event');
      var transformer = (event as RTCTransformEvent).transformer;
      print('transformer $transformer');
      transformer.handled = true;
      var options = transformer.options;
      var kind = options.kind;
      var participantId = options.participantId;
      var trackId = options.trackId;
      var codec = options.codec;
      var msgType = options.msgType;

      var cryptor = Cryptor(
          participantId: participantId, trackId: trackId, sharedKey: false);

      cryptor.setupTransform(
          operation: msgType,
          readable: transformer.readable,
          writable: transformer.writable,
          trackId: trackId,
          kind: kind,
          codec: codec);

      cryptor.setKey(sharedKey);

      participantCryptors[participantId] = cryptor;
    });
  }

  self.onMessage.listen((e) {
    var msg = e.data;
    var msgType = msg['msgType'];
    switch (msgType) {
      case 'init':
        useSharedKey = msg['useSharedKey'] as bool;
        break;
      case 'enable':
        var enabled = msg['enabled'] as bool;
        var participantId = msg['participantId'] as String;
        print('worker: set enable $enabled for participantId $participantId');
        var cryptor = participantCryptors
            .firstWhereOrNull((c) => c.participantId == participantId);
        if (cryptor != null) {
          cryptor.enabled = enabled;
        }
        break;
      case 'decode':
      case 'encode':
        var kind = msg['kind'];
        var participantId = msg['participantId'] as String;
        var trackId = msg['trackId'];
        var readable = msg['readableStream'] as ReadableStream;
        var writable = msg['writableStream'] as WritableStream;
        var codec = msg['codec'] as String;
        print(
            'worker: got $msgType, kind $kind, trackId $trackId, participantId $participantId, ${readable.runtimeType} ${writable.runtimeType}}');
        var cryptor = participantCryptors.firstWhere(
            (c) => c.participantId == participantId,
            orElse: () => Cryptor(
                participantId: participantId,
                trackId: trackId,
                sharedKey: useSharedKey));

        cryptor.setupTransform(
            operation: msgType,
            readable: readable,
            writable: writable,
            trackId: trackId,
            kind: kind,
            codec: codec);

        participantCryptors.add(cryptor);
        break;
      case 'removeTransform':
        var trackId = msg['trackId'] as String;
        print('worker: removing trackId $trackId');
        participantCryptors.removeWhere((c) => c.trackId == trackId);
        break;
      case 'setKey':
        var key = Uint8List.fromList(base64Decode(msg['key'] as String));
        print('worker: got setKey ${msg['key']}, key $key');
        var participantId = msg['participantId'] as String;
        print('worker: setup key for participant $participantId');
        var cryptor = participantCryptors
            .firstWhereOrNull((c) => c.participantId == participantId);
        if (key.length != 32) {
          print('worker: invalid key length ${key.length}');
          break;
        }
        if (cryptor != null) {
          cryptor.setKey(key);
        }
        break;
      case 'setKeyIndex':
        var keyIndex = msg['index'];
        var participantId = msg['participantId'] as String;
        print('worker: setup key index for participant $participantId');
        var cryptor = participantCryptors
            .firstWhereOrNull((c) => c.participantId == participantId);
        if (cryptor != null) {
          cryptor.setKeyIndex(keyIndex);
        }
        break;
      case 'updateCodec':
        var codec = msg['codec'] as String;
        var participantId = msg['participantId'] as String;
        print(
            'worker: update codec for participant $participantId, codec $codec');
        var cryptor = participantCryptors
            .firstWhereOrNull((c) => c.participantId == participantId);
        if (cryptor != null) {
          cryptor.updateCodec(codec);
        }
        break;
      default:
        print('worker: unknown message kind ${msg.msgType}');
    }
    self.postMessage({});
  });
}
