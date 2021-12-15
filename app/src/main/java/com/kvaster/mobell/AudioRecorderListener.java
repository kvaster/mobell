package com.kvaster.mobell;

import androidx.annotation.Keep;

@Keep
public interface AudioRecorderListener {
    void onAudioData(byte[] data);
}
