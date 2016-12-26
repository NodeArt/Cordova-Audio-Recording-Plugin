function AudioRecorder() {
}

AudioRecorder.prototype.record = function (successCallback, errorCallback, fileName, duration) {
  cordova.exec(successCallback, errorCallback, "AudioRecorder", "record", [fileName, String(duration)]);
};

AudioRecorder.prototype.startRecord = function (successCallback, errorCallback, fileName) {
  cordova.exec(successCallback, errorCallback, "AudioRecorder", "startRecord", [fileName]);
};

AudioRecorder.prototype.stopRecord = function (successCallback, errorCallback ) {
  cordova.exec(successCallback, errorCallback, "AudioRecorder", "stopRecord", []);
};

AudioRecorder.install = function () {
  if (!window.plugins) {
    window.plugins = {};
  }
  window.plugins.audioRecorder = new AudioRecorder();
  return window.plugins.audioRecorder;
};

cordova.addConstructor(AudioRecorder.install);
